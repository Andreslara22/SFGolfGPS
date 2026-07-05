package mx.clubsanfrancisco.golfgps

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel

enum class Units { YARDS, METERS }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class Player(name: String, strokes: List<Int> = List(18) { 0 }) {
    var name by mutableStateOf(name)
    val strokes = mutableStateListOf<Int>().apply { addAll(strokes) }

    fun total(): Int = strokes.sum()

    /** Diferencial vs par contando solo hoyos con golpes anotados. */
    fun relativeToPar(): Int {
        var rel = 0
        strokes.forEachIndexed { i, s ->
            if (s > 0) rel += s - CourseData.holes[i].par
        }
        return rel
    }

    fun playedHoles(): Int = strokes.count { it > 0 }
}

class GolfViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("sfgolf", Context.MODE_PRIVATE)

    // --- Ubicación GPS ---
    var userLat by mutableStateOf<Double?>(null); private set
    var userLng by mutableStateOf<Double?>(null); private set
    var gpsAccuracyM by mutableStateOf<Float?>(null); private set
    var hasLocationPermission by mutableStateOf(false)

    // --- Hoyo actual ---
    var currentHoleIndex by mutableStateOf(0); private set
    var autoDetect by mutableStateOf(true); private set

    // --- Jugadores (máx. 5) ---
    val players = mutableStateListOf<Player>()
    var activePlayerIndex by mutableStateOf(0)

    // --- Ajustes ---
    var units by mutableStateOf(Units.YARDS)
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    init {
        loadState()
        if (players.isEmpty()) players.add(Player("Jugador 1"))
    }

    val currentHole: Hole get() = CourseData.holes[currentHoleIndex]

    fun onLocation(lat: Double, lng: Double, accuracy: Float) {
        userLat = lat
        userLng = lng
        gpsAccuracyM = accuracy
        if (autoDetect) {
            val nearest = CourseData.nearestHoleByTee(lat, lng)
            currentHoleIndex = nearest.number - 1
        }
    }

    /** Distancia actual al centro del green, en metros, o null sin GPS. */
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

    fun enableAutoDetect() {
        autoDetect = true
        val lat = userLat
        val lng = userLng
        if (lat != null && lng != null) {
            currentHoleIndex = CourseData.nearestHoleByTee(lat, lng).number - 1
        }
    }

    // --- Golpes ---
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

    // --- Jugadores ---
    fun addPlayer() {
        if (players.size < 5) {
            players.add(Player("Jugador ${players.size + 1}"))
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

    fun renamePlayer(index: Int, newName: String) {
        if (index in players.indices && newName.isNotBlank()) {
            players[index].name = newName.trim().take(14)
            saveState()
        }
    }

    fun resetRound() {
        players.forEach { p -> for (i in 0 until 18) p.strokes[i] = 0 }
        saveState()
    }

    fun setUnitsAndSave(u: Units) { units = u; saveState() }
    fun setThemeAndSave(t: ThemeMode) { themeMode = t; saveState() }

    // --- Persistencia ---
    private fun saveState() {
        prefs.edit()
            .putString("names", players.joinToString("|") { it.name })
            .putString("scores", players.joinToString("|") { p -> p.strokes.joinToString(",") })
            .putString("units", units.name)
            .putString("theme", themeMode.name)
            .apply()
    }

    private fun loadState() {
        units = runCatching { Units.valueOf(prefs.getString("units", "YARDS")!!) }.getOrDefault(Units.YARDS)
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
        val names = prefs.getString("names", null)?.split("|")?.filter { it.isNotBlank() } ?: return
        val scores = prefs.getString("scores", null)?.split("|") ?: return
        names.forEachIndexed { i, name ->
            val strokes = scores.getOrNull(i)
                ?.split(",")
                ?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == 18 }
                ?: List(18) { 0 }
            if (players.size < 5) players.add(Player(name, strokes))
        }
    }
}
