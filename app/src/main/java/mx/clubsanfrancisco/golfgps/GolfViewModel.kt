package mx.clubsanfrancisco.golfgps

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject

enum class Units { YARDS, METERS }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class Player(
    name: String,
    strokes: List<Int> = List(18) { 0 },
    clubs: List<Int> = defaultClubYards
) {
    var name by mutableStateOf(name)
    val strokes = mutableStateListOf<Int>().apply { addAll(strokes) }
    val clubYards = mutableStateListOf<Int>().apply { addAll(clubs) }

    fun total(): Int = strokes.sum()

    /** Score vs par counting only holes with recorded strokes. */
    fun relativeToPar(): Int {
        var rel = 0
        strokes.forEachIndexed { i, s ->
            if (s > 0) rel += s - CourseData.holes[i].par
        }
        return rel
    }

    fun playedHoles(): Int = strokes.count { it > 0 }
}

class SavedRound(
    val date: Long,
    val entries: List<Entry>
) {
    class Entry(val name: String, val strokes: Int, val relative: Int, val holes: Int)
}

/** Auto hole switching only engages within this distance of a tee (meters). */
private const val AUTO_DETECT_RADIUS_M = 150.0

class GolfViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("sfgolf", Context.MODE_PRIVATE)

    // --- GPS ---
    var userLat by mutableStateOf<Double?>(null); private set
    var userLng by mutableStateOf<Double?>(null); private set
    var gpsAccuracyM by mutableStateOf<Float?>(null); private set
    var hasLocationPermission by mutableStateOf(false)

    // --- Current hole (always starts at hole 1) ---
    var currentHoleIndex by mutableStateOf(0); private set
    var autoDetect by mutableStateOf(false); private set

    // --- Players (max 5) ---
    val players = mutableStateListOf<Player>()
    var activePlayerIndex by mutableStateOf(0)

    // --- Round history ---
    val history = mutableStateListOf<SavedRound>()

    // --- Settings ---
    var units by mutableStateOf(Units.YARDS)
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    init {
        loadState()
        if (players.isEmpty()) players.add(Player("Player 1"))
    }

    val currentHole: Hole get() = CourseData.holes[currentHoleIndex]

    fun onLocation(lat: Double, lng: Double, accuracy: Float) {
        userLat = lat
        userLng = lng
        gpsAccuracyM = accuracy
        if (autoDetect) {
            val nearest = CourseData.nearestHoleByTee(lat, lng)
            val dist = haversineMeters(lat, lng, nearest.teeLat, nearest.teeLng)
            // Only switch when actually standing near a tee — prevents jumping
            // around when you're away from the course.
            if (dist <= AUTO_DETECT_RADIUS_M) {
                currentHoleIndex = nearest.number - 1
            }
        }
    }

    fun distanceToGreenMeters(): Double? {
        val lat = userLat ?: return null
        val lng = userLng ?: return null
        val h = currentHole
        return haversineMeters(lat, lng, h.greenLat, h.greenLng)
    }

    fun nextHole() {
        autoDetect = false
        currentHoleIndex = (currentHoleIndex + 1) % 18
    }

    fun previousHole() {
        autoDetect = false
        currentHoleIndex = (currentHoleIndex + 17) % 18
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
    }

    // --- Strokes ---
    fun addStroke(playerIdx: Int, holeIdx: Int = currentHoleIndex) {
        if (playerIdx in players.indices) {
            val p = players[playerIdx]
            if (p.strokes[holeIdx] < 15) p.strokes[holeIdx] = p.strokes[holeIdx] + 1
            saveState()
        }
    }

    fun removeStroke(playerIdx: Int, holeIdx: Int = currentHoleIndex) {
        if (playerIdx in players.indices) {
            val p = players[playerIdx]
            if (p.strokes[holeIdx] > 0) p.strokes[holeIdx] = p.strokes[holeIdx] - 1
            saveState()
        }
    }

    // --- Players ---
    fun addPlayer() {
        if (players.size < 5) {
            players.add(Player("Player ${players.size + 1}"))
            saveState()
        }
    }

    fun removePlayer(index: Int) {
        if (players.size > 1 && index in players.indices) {
            players.removeAt(index)
            if (activePlayerIndex >= players.size) activePlayerIndex = players.size - 1
            saveState()
        }
    }

    fun adjustClub(playerIdx: Int, clubIdx: Int, delta: Int) {
        if (playerIdx in players.indices && clubIdx in clubNames.indices) {
            val p = players[playerIdx]
            p.clubYards[clubIdx] = (p.clubYards[clubIdx] + delta).coerceIn(30, 350)
            saveState()
        }
    }

    fun resetClubs(playerIdx: Int) {
        if (playerIdx in players.indices) {
            val p = players[playerIdx]
            defaultClubYards.forEachIndexed { i, v -> p.clubYards[i] = v }
            saveState()
        }
    }

    fun renamePlayer(index: Int, newName: String) {
        if (index in players.indices && newName.isNotBlank()) {
            players[index].name = newName.trim().take(14)
            saveState()
        }
    }

    /** Saves the current round to history (if any strokes) and starts fresh at hole 1. */
    fun finishRound() {
        if (players.any { it.total() > 0 }) {
            history.add(0, SavedRound(
                date = System.currentTimeMillis(),
                entries = players.map {
                    SavedRound.Entry(it.name, it.total(), it.relativeToPar(), it.playedHoles())
                }
            ))
            while (history.size > 30) history.removeAt(history.size - 1)
        }
        players.forEach { p -> for (i in 0 until 18) p.strokes[i] = 0 }
        currentHoleIndex = 0
        autoDetect = false
        saveState()
    }

    /** Clears current strokes without saving to history. */
    fun clearStrokes() {
        players.forEach { p -> for (i in 0 until 18) p.strokes[i] = 0 }
        currentHoleIndex = 0
        saveState()
    }

    fun deleteRound(index: Int) {
        if (index in history.indices) {
            history.removeAt(index)
            saveState()
        }
    }

    fun setUnitsAndSave(u: Units) { units = u; saveState() }
    fun setThemeAndSave(t: ThemeMode) { themeMode = t; saveState() }

    // --- Persistence ---
    private fun saveState() {
        val historyJson = JSONArray()
        history.forEach { r ->
            val entries = JSONArray()
            r.entries.forEach { e ->
                entries.put(JSONObject()
                    .put("n", e.name).put("s", e.strokes)
                    .put("r", e.relative).put("h", e.holes))
            }
            historyJson.put(JSONObject().put("d", r.date).put("e", entries))
        }
        prefs.edit()
            .putString("names", players.joinToString("|") { it.name })
            .putString("scores", players.joinToString("|") { p -> p.strokes.joinToString(",") })
            .putString("clubs", players.joinToString("|") { p -> p.clubYards.joinToString(",") })
            .putString("units", units.name)
            .putString("theme", themeMode.name)
            .putString("history", historyJson.toString())
            .apply()
    }

    private fun loadState() {
        units = runCatching { Units.valueOf(prefs.getString("units", "YARDS")!!) }.getOrDefault(Units.YARDS)
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)

        runCatching {
            val arr = JSONArray(prefs.getString("history", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val es = o.getJSONArray("e")
                val entries = (0 until es.length()).map { j ->
                    val e = es.getJSONObject(j)
                    SavedRound.Entry(e.getString("n"), e.getInt("s"), e.getInt("r"), e.getInt("h"))
                }
                history.add(SavedRound(o.getLong("d"), entries))
            }
        }

        val names = prefs.getString("names", null)?.split("|")?.filter { it.isNotBlank() } ?: return
        val scores = prefs.getString("scores", null)?.split("|") ?: return
        val clubs = prefs.getString("clubs", null)?.split("|")
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
            if (players.size < 5) players.add(Player(name, strokes, clubList))
        }
    }
}
