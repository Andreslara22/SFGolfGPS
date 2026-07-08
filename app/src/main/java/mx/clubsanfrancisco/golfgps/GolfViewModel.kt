package mx.clubsanfrancisco.golfgps

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import org.json.JSONObject

// Data Layer: sincroniza el estado de la ronda con el reloj (snapshot completo,
// last-write-wins por timestamp): nombres, golpes de todos, jugador activo,
// hoyo actual y unidades.
private const val STATE_PATH = "/round/state"
private const val KEY_NAMES = "names"
private const val KEY_SCORES = "scores"
private const val KEY_ACTIVE = "active"
private const val KEY_HOLE = "hole"
private const val KEY_UNITS = "units"
private const val KEY_AUTO = "auto"
private const val KEY_FLAGS = "flags"
private const val KEY_CLUBS = "clubs"
private const val KEY_TS = "ts"
private const val SEP = ""

enum class Units { YARDS, METERS }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class Player(
    name: String,
    strokes: List<Int> = List(18) { 0 },
    clubs: List<Int> = defaultClubYards,
    putts: List<Int> = List(18) { 0 },
    fir: List<Int> = List(18) { -1 }
) {
    var name by mutableStateOf(name)
    val strokes = mutableStateListOf<Int>().apply { addAll(strokes) }
    val clubYards = mutableStateListOf<Int>().apply { addAll(clubs) }

    /** Putts por hoyo (0 = sin registrar). */
    val putts = mutableStateListOf<Int>().apply { addAll(putts) }
    /** Fairway por hoyo: -1 sin dato · 0 fallado · 1 acertado. Solo aplica en par 4/5. */
    val fir = mutableStateListOf<Int>().apply { addAll(fir) }

    fun total(): Int = strokes.sum()
    fun totalPutts(): Int = putts.sum()

    /** Score vs par counting only holes with recorded strokes. */
    fun relativeToPar(): Int {
        var rel = 0
        strokes.forEachIndexed { i, s ->
            if (s > 0) rel += s - CourseData.holes[i].par
        }
        return rel
    }

    fun playedHoles(): Int = strokes.count { it > 0 }

    /**
     * Greens in regulation: llegaste al green en (par − 2) golpes.
     * Derivado de golpes − putts, solo en hoyos con ambos registrados.
     */
    fun girCount(): Int {
        var gir = 0
        strokes.forEachIndexed { i, s ->
            val p = putts[i]
            if (s > 0 && p > 0 && s - p <= CourseData.holes[i].par - 2) gir++
        }
        return gir
    }

    /** Hoyos con golpes Y putts registrados (denominador del GIR%). */
    fun girTracked(): Int = strokes.indices.count { strokes[it] > 0 && putts[it] > 0 }

    /** Fairways: Pair(acertados, intentados) en par 4/5 con dato. */
    fun firStats(): Pair<Int, Int> {
        var hit = 0; var att = 0
        fir.forEachIndexed { i, v ->
            if (CourseData.holes[i].par >= 4 && v >= 0) { att++; if (v == 1) hit++ }
        }
        return hit to att
    }
}

class SavedRound(
    val date: Long,
    val entries: List<Entry>
) {
    class Entry(
        val name: String, val strokes: Int, val relative: Int, val holes: Int,
        val putts: Int = 0, val gir: Int = 0, val girTracked: Int = 0,
        val firHit: Int = 0, val firAtt: Int = 0
    )
}

/** Auto hole switching only engages within this distance of a tee (meters). */
private const val AUTO_DETECT_RADIUS_M = 150.0

/** Radio alrededor del centro del green para autocalibrar elevación (m). */
private const val GREEN_CALIBRATION_RADIUS_M = 22.0
/** Precisión GPS mínima para confiar en la altitud (m). */
private const val MAX_ACCURACY_FOR_ALT_M = 15.0
/** Suavizado de la altitud del jugador. */
private const val ALT_EMA_ALPHA = 0.25
/** Suavizado de la elevación aprendida de cada green. */
private const val GREEN_EMA_ALPHA = 0.15

class GolfViewModel(app: Application) : AndroidViewModel(app), DataClient.OnDataChangedListener {

    private val prefs = app.getSharedPreferences("sfgolf", Context.MODE_PRIVATE)
    private val dataClient: DataClient = Wearable.getDataClient(app)
    private var stateTs = 0L

    // --- GPS ---
    var userLat by mutableStateOf<Double?>(null); private set
    var userLng by mutableStateOf<Double?>(null); private set
    var gpsAccuracyM by mutableStateOf<Float?>(null); private set
    var hasLocationPermission by mutableStateOf(false)

    // --- Altitud (para "plays like") ---
    /** Altitud del jugador, suavizada con media móvil exponencial (el GPS es ruidoso en vertical). */
    var userAltM by mutableStateOf<Double?>(null); private set
    /**
     * Elevación de cada green, autocalibrada: cuando el jugador pisa un green
     * (a ≤ [GREEN_CALIBRATION_RADIUS_M] del centro con buena precisión) se
     * promedia su altitud GPS hacia ese hoyo. Tras una ronda el campo queda
     * mapeado y persiste en SharedPreferences. Double.NaN = sin dato aún.
     */
    private val greenElevM = DoubleArray(18) { Double.NaN }
    var calibratedGreens by mutableStateOf(0); private set

    // --- Current hole (always starts at hole 1) ---
    var currentHoleIndex by mutableStateOf(0); private set
    var autoDetect by mutableStateOf(false); private set

    // --- Players (max 5) ---
    val players = mutableStateListOf<Player>()
    var activePlayerIndex by mutableStateOf(0)

    // --- Pin position per hole: -1 none · 0 red (front) · 1 white (mid) · 2 blue (back) ---
    // Club rule: flags rotate red -> white -> blue hole after hole, so picking
    // one hole's flag determines the entire course.
    val flags = mutableStateListOf<Int>().apply { repeat(18) { add(-1) } }

    // --- Round history ---
    val history = mutableStateListOf<SavedRound>()

    // --- Settings ---
    var units by mutableStateOf(Units.YARDS)
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    init {
        loadState()
        if (players.isEmpty()) players.add(Player("Player 1"))
        stateTs = prefs.getLong("stateTs", 0L)

        // Sincronización con el reloj (Data Layer): trae el último snapshot.
        dataClient.addListener(this)
        dataClient.dataItems.addOnSuccessListener { items ->
            for (item in items) {
                if (item.uri.path == STATE_PATH) {
                    applyIncoming(DataMapItem.fromDataItem(item).dataMap)
                }
            }
            items.release()
        }
    }

    // --- Sincronización con el reloj (snapshot completo de la ronda) ---

    /** Marca cambio local, guarda y publica el estado al reloj. */
    private fun syncOut() {
        stateTs = System.currentTimeMillis()
        saveState()
        pushState()
    }

    /** Publica el estado actual (nombres, golpes, activo, hoyo, unidades). */
    private fun pushState() {
        val req = PutDataMapRequest.create(STATE_PATH).apply {
            dataMap.putString(KEY_NAMES, players.joinToString(SEP) { it.name })
            dataMap.putString(KEY_SCORES, players.joinToString(SEP) { p -> p.strokes.joinToString(",") })
            dataMap.putInt(KEY_ACTIVE, activePlayerIndex)
            dataMap.putInt(KEY_HOLE, currentHoleIndex)
            dataMap.putString(KEY_UNITS, units.name)
            dataMap.putBoolean(KEY_AUTO, autoDetect)
            dataMap.putString(KEY_FLAGS, flags.joinToString(","))
            dataMap.putString(KEY_CLUBS, players.joinToString(SEP) { p -> p.clubYards.joinToString(",") })
            dataMap.putLong(KEY_TS, stateTs)
        }
        dataClient.putDataItem(req.asPutDataRequest().setUrgent())
    }

    /** Aplica un snapshot entrante si es más nuevo (last-write-wins). */
    private fun applyIncoming(dm: com.google.android.gms.wearable.DataMap) {
        val ts = dm.getLong(KEY_TS)
        if (ts <= stateTs) return
        val names = dm.getString(KEY_NAMES)?.split(SEP) ?: return
        val scores = dm.getString(KEY_SCORES)?.split(SEP) ?: return
        // Actualiza golpes y yardas de palos de los jugadores existentes.
        val clubsIn = dm.getString(KEY_CLUBS)?.split(SEP)
        players.forEachIndexed { i, p ->
            scores.getOrNull(i)?.split(",")?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == 18 }
                ?.forEachIndexed { h, v -> p.strokes[h] = v }
            clubsIn?.getOrNull(i)?.split(",")?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == clubNames.size }
                ?.forEachIndexed { c, v -> p.clubYards[c] = v }
        }
        val a = dm.getInt(KEY_ACTIVE, activePlayerIndex)
        if (a in players.indices) activePlayerIndex = a
        val h = dm.getInt(KEY_HOLE, currentHoleIndex)
        if (h in 0..17) currentHoleIndex = h
        runCatching { units = Units.valueOf(dm.getString(KEY_UNITS, units.name)) }
        autoDetect = dm.getBoolean(KEY_AUTO, autoDetect)
        dm.getString(KEY_FLAGS)?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 18 }?.forEachIndexed { i, v -> flags[i] = v }
        stateTs = ts
        saveState()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == STATE_PATH) {
                applyIncoming(DataMapItem.fromDataItem(event.dataItem).dataMap)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataClient.removeListener(this)
    }

    val currentHole: Hole get() = CourseData.holes[currentHoleIndex]

    fun onLocation(lat: Double, lng: Double, accuracy: Float, altitudeM: Double? = null) {
        userLat = lat
        userLng = lng
        gpsAccuracyM = accuracy

        if (altitudeM != null && accuracy <= MAX_ACCURACY_FOR_ALT_M) {
            // EMA: suaviza el ruido vertical del GPS (±5-10 m por lectura).
            userAltM = userAltM?.let { it * (1 - ALT_EMA_ALPHA) + altitudeM * ALT_EMA_ALPHA }
                ?: altitudeM
            calibrateGreenIfOnIt(lat, lng)
        }

        if (autoDetect) {
            val nearest = CourseData.nearestHoleByTee(lat, lng)
            val dist = haversineMeters(lat, lng, nearest.teeLat, nearest.teeLng)
            // Only switch when actually standing near a tee — prevents jumping
            // around when you're away from the course.
            if (dist <= AUTO_DETECT_RADIUS_M && currentHoleIndex != nearest.number - 1) {
                currentHoleIndex = nearest.number - 1
                syncOut()
            }
        }
    }

    /** Si el jugador está parado sobre un green, aprende su elevación. */
    private fun calibrateGreenIfOnIt(lat: Double, lng: Double) {
        val alt = userAltM ?: return
        CourseData.holes.forEachIndexed { i, h ->
            if (haversineMeters(lat, lng, h.greenLat, h.greenLng) <= GREEN_CALIBRATION_RADIUS_M) {
                greenElevM[i] = if (greenElevM[i].isNaN()) alt
                    else greenElevM[i] * (1 - GREEN_EMA_ALPHA) + alt * GREEN_EMA_ALPHA
                val count = greenElevM.count { !it.isNaN() }
                if (count != calibratedGreens) calibratedGreens = count
                saveElevations()
            }
        }
    }

    fun distanceToGreenMeters(): Double? {
        val lat = userLat ?: return null
        val lng = userLng ?: return null
        val h = currentHole
        return haversineMeters(lat, lng, h.greenLat, h.greenLng)
    }

    /**
     * Diferencia de elevación jugador → green del hoyo actual, en metros.
     * Positivo = green cuesta arriba. Null si aún no hay calibración para
     * este green o no hay altitud GPS.
     */
    fun elevationDeltaM(): Double? {
        val alt = userAltM ?: return null
        val g = greenElevM[currentHoleIndex]
        if (g.isNaN()) return null
        return g - alt
    }

    /**
     * Distancia "plays like": distancia real + ajuste por elevación.
     * Regla práctica de campo: cada metro de subida/bajada suma/resta ~1 m
     * efectivo al tiro. Se ignoran deltas menores a 2 m (ruido GPS).
     */
    fun playsLikeMeters(): Double? {
        val dist = distanceToGreenMeters() ?: return null
        val delta = elevationDeltaM() ?: return null
        if (kotlin.math.abs(delta) < 2.0) return null
        return (dist + delta).coerceAtLeast(0.0)
    }

    /** Borra las elevaciones aprendidas (por si una calibración salió mal). */
    fun resetElevations() {
        for (i in 0 until 18) greenElevM[i] = Double.NaN
        calibratedGreens = 0
        saveElevations()
    }

    private fun saveElevations() {
        prefs.edit()
            .putString("greenElev", greenElevM.joinToString(",") {
                if (it.isNaN()) "" else "%.1f".format(it)
            })
            .apply()
    }

    fun nextHole() {
        autoDetect = false
        currentHoleIndex = (currentHoleIndex + 1) % 18
        syncOut()
    }

    fun previousHole() {
        autoDetect = false
        currentHoleIndex = (currentHoleIndex + 17) % 18
        syncOut()
    }

    fun toggleAutoDetect() {
        autoDetect = !autoDetect
        if (autoDetect) {
            val lat = userLat
            val lng = userLng
            if (lat != null && lng != null) {
                val nearest = CourseData.nearestHoleByTee(lat, lng)
                val dist = haversineMeters(lat, lng, nearest.teeLat, nearest.teeLng)
                if (dist <= AUTO_DETECT_RADIUS_M) currentHoleIndex = nearest.number - 1
            }
        }
        syncOut()
    }

    // --- Strokes ---
    fun addStroke(playerIdx: Int, holeIdx: Int = currentHoleIndex) {
        if (playerIdx in players.indices) {
            val p = players[playerIdx]
            if (p.strokes[holeIdx] < 15) p.strokes[holeIdx] = p.strokes[holeIdx] + 1
            syncOut()
        }
    }

    fun removeStroke(playerIdx: Int, holeIdx: Int = currentHoleIndex) {
        if (playerIdx in players.indices) {
            val p = players[playerIdx]
            if (p.strokes[holeIdx] > 0) p.strokes[holeIdx] = p.strokes[holeIdx] - 1
            syncOut()
        }
    }

    /** Cambia el jugador activo y sincroniza (para el selector del reloj). */
    fun setActivePlayer(index: Int) {
        if (index in players.indices && index != activePlayerIndex) {
            activePlayerIndex = index
            syncOut()
        }
    }

    // --- Putts y fairways ---
    fun addPutt(playerIdx: Int, holeIdx: Int = currentHoleIndex) {
        if (playerIdx in players.indices) {
            val p = players[playerIdx]
            if (p.putts[holeIdx] < 9) p.putts[holeIdx] = p.putts[holeIdx] + 1
            saveState()
        }
    }

    fun removePutt(playerIdx: Int, holeIdx: Int = currentHoleIndex) {
        if (playerIdx in players.indices) {
            val p = players[playerIdx]
            if (p.putts[holeIdx] > 0) p.putts[holeIdx] = p.putts[holeIdx] - 1
            saveState()
        }
    }

    /** Cicla el fairway: sin dato -> acertado -> fallado -> sin dato. */
    fun cycleFir(playerIdx: Int, holeIdx: Int = currentHoleIndex) {
        if (playerIdx in players.indices) {
            val p = players[playerIdx]
            p.fir[holeIdx] = when (p.fir[holeIdx]) { -1 -> 1; 1 -> 0; else -> -1 }
            saveState()
        }
    }

    // --- Juegos ---

    /**
     * Skins con acarreo: en cada hoyo donde TODOS los jugadores registraron
     * golpes, el score único más bajo gana el pozo (1 skin + acarreados).
     * Empate en el más bajo -> el pozo se acarrea al siguiente hoyo completo.
     * Devuelve skins ganados por jugador y el pozo pendiente.
     */
    fun skinsStandings(): Pair<List<Int>, Int> {
        val won = IntArray(players.size)
        var pot = 0
        for (h in 0 until 18) {
            val scores = players.map { it.strokes[h] }
            if (scores.any { it == 0 }) continue // hoyo incompleto: no cuenta ni acarrea
            pot += 1
            val minScore = scores.min()
            val winners = scores.withIndex().filter { it.value == minScore }
            if (winners.size == 1) {
                won[winners.first().index] += pot
                pot = 0
            } // empate: pot se acarrea
        }
        return won.toList() to pot
    }

    /**
     * Match play clásico para exactamente 2 jugadores.
     * Devuelve el estado legible ("Andres 2 UP thru 7", "All square thru 7",
     * "Andres gana 3&2") o null si no aplica.
     */
    fun matchPlayStatus(): String? {
        if (players.size != 2) return null
        val a = players[0]; val b = players[1]
        var diff = 0 // >0 = jugador A arriba
        var thru = 0
        var decidedAt = -1
        for (h in 0 until 18) {
            if (a.strokes[h] == 0 || b.strokes[h] == 0) continue
            thru = h + 1
            if (a.strokes[h] < b.strokes[h]) diff++
            else if (b.strokes[h] < a.strokes[h]) diff--
            val remaining = 18 - (h + 1)
            if (decidedAt < 0 && kotlin.math.abs(diff) > remaining) decidedAt = h + 1
        }
        if (thru == 0) return null
        val leader = if (diff > 0) a.name else b.name
        val up = kotlin.math.abs(diff)
        return when {
            decidedAt > 0 -> "$leader gana $up&${18 - decidedAt}"
            diff == 0 -> "All square · thru $thru"
            thru == 18 -> "$leader gana $up UP"
            else -> "$leader $up UP · thru $thru"
        }
    }

    // --- Players ---
    fun addPlayer() {
        if (players.size < 5) {
            players.add(Player("Player ${players.size + 1}"))
            syncOut()
        }
    }

    fun removePlayer(index: Int) {
        if (players.size > 1 && index in players.indices) {
            players.removeAt(index)
            if (activePlayerIndex >= players.size) activePlayerIndex = players.size - 1
            syncOut()
        }
    }

    fun setFlagRotation(holeIdx: Int, color: Int) {
        for (i in 0 until 18) {
            flags[i] = (((color + (i - holeIdx)) % 3) + 3) % 3
        }
        syncOut()
    }

    fun clearFlags() {
        for (i in 0 until 18) flags[i] = -1
        syncOut()
    }

    fun adjustClub(playerIdx: Int, clubIdx: Int, delta: Int) {
        if (playerIdx in players.indices && clubIdx in clubNames.indices) {
            val p = players[playerIdx]
            p.clubYards[clubIdx] = (p.clubYards[clubIdx] + delta).coerceIn(30, 350)
            syncOut()
        }
    }

    fun resetClubs(playerIdx: Int) {
        if (playerIdx in players.indices) {
            val p = players[playerIdx]
            defaultClubYards.forEachIndexed { i, v -> p.clubYards[i] = v }
            syncOut()
        }
    }

    fun renamePlayer(index: Int, newName: String) {
        if (index in players.indices && newName.isNotBlank()) {
            players[index].name = newName.trim().take(14)
            syncOut()
        }
    }

    /** Saves the current round to history (if any strokes) and starts fresh at hole 1. */
    fun finishRound() {
        if (players.any { it.total() > 0 }) {
            history.add(0, SavedRound(
                date = System.currentTimeMillis(),
                entries = players.map {
                    val (fh, fa) = it.firStats()
                    SavedRound.Entry(
                        it.name, it.total(), it.relativeToPar(), it.playedHoles(),
                        it.totalPutts(), it.girCount(), it.girTracked(), fh, fa
                    )
                }
            ))
            while (history.size > 30) history.removeAt(history.size - 1)
        }
        players.forEach { p ->
            for (i in 0 until 18) { p.strokes[i] = 0; p.putts[i] = 0; p.fir[i] = -1 }
        }
        for (i in 0 until 18) flags[i] = -1
        currentHoleIndex = 0
        autoDetect = false
        syncOut()
    }

    /** Clears current strokes without saving to history. */
    fun clearStrokes() {
        players.forEach { p ->
            for (i in 0 until 18) { p.strokes[i] = 0; p.putts[i] = 0; p.fir[i] = -1 }
        }
        for (i in 0 until 18) flags[i] = -1
        currentHoleIndex = 0
        syncOut()
    }

    fun deleteRound(index: Int) {
        if (index in history.indices) {
            history.removeAt(index)
            saveState()
        }
    }

    fun setUnitsAndSave(u: Units) { units = u; syncOut() }
    fun setThemeAndSave(t: ThemeMode) { themeMode = t; saveState() }

    // --- Persistence ---
    private fun saveState() {
        val historyJson = JSONArray()
        history.forEach { r ->
            val entries = JSONArray()
            r.entries.forEach { e ->
                entries.put(JSONObject()
                    .put("n", e.name).put("s", e.strokes)
                    .put("r", e.relative).put("h", e.holes)
                    .put("p", e.putts).put("g", e.gir).put("gt", e.girTracked)
                    .put("fh", e.firHit).put("fa", e.firAtt))
            }
            historyJson.put(JSONObject().put("d", r.date).put("e", entries))
        }
        prefs.edit()
            .putString("names", players.joinToString("|") { it.name })
            .putString("scores", players.joinToString("|") { p -> p.strokes.joinToString(",") })
            .putString("clubs", players.joinToString("|") { p -> p.clubYards.joinToString(",") })
            .putString("putts", players.joinToString("|") { p -> p.putts.joinToString(",") })
            .putString("firs", players.joinToString("|") { p -> p.fir.joinToString(",") })
            .putString("flags", flags.joinToString(","))
            .putString("units", units.name)
            .putString("theme", themeMode.name)
            .putString("history", historyJson.toString())
            .putLong("stateTs", stateTs)
            .apply()
    }

    private fun loadState() {
        units = runCatching { Units.valueOf(prefs.getString("units", "YARDS")!!) }.getOrDefault(Units.YARDS)
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)

        prefs.getString("flags", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 18 }
            ?.forEachIndexed { i, v -> flags[i] = v }

        prefs.getString("greenElev", null)?.split(",")
            ?.takeIf { it.size == 18 }
            ?.forEachIndexed { i, s -> greenElevM[i] = s.toDoubleOrNull() ?: Double.NaN }
        calibratedGreens = greenElevM.count { !it.isNaN() }

        runCatching {
            val arr = JSONArray(prefs.getString("history", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val es = o.getJSONArray("e")
                val entries = (0 until es.length()).map { j ->
                    val e = es.getJSONObject(j)
                    SavedRound.Entry(
                        e.getString("n"), e.getInt("s"), e.getInt("r"), e.getInt("h"),
                        e.optInt("p"), e.optInt("g"), e.optInt("gt"),
                        e.optInt("fh"), e.optInt("fa")
                    )
                }
                history.add(SavedRound(o.getLong("d"), entries))
            }
        }

        val names = prefs.getString("names", null)?.split("|")?.filter { it.isNotBlank() } ?: return
        val scores = prefs.getString("scores", null)?.split("|") ?: return
        val clubs = prefs.getString("clubs", null)?.split("|")
        val puttsAll = prefs.getString("putts", null)?.split("|")
        val firsAll = prefs.getString("firs", null)?.split("|")
        names.forEachIndexed { i, name ->
            val strokes = scores.getOrNull(i)
                ?.split(",")
                ?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == 18 }
                ?: List(18) { 0 }
            val clubList = clubs?.getOrNull(i)
                ?.split(",")
                ?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == clubNames.size }
                ?: defaultClubYards
            val puttList = puttsAll?.getOrNull(i)
                ?.split(",")?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == 18 } ?: List(18) { 0 }
            val firList = firsAll?.getOrNull(i)
                ?.split(",")?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == 18 } ?: List(18) { -1 }
            if (players.size < 5) players.add(Player(name, strokes, clubList, puttList, firList))
        }
    }
}
