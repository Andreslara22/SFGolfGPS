package mx.clubsanfrancisco.golfgps.wear

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlin.math.roundToInt

private val Mint = Color(0xFF7ADFA8)
private val Dim = Color(0xFF9BB8A8)

// Data Layer: snapshot completo de la ronda (mismo formato que la app de teléfono).
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

// Tras 1 hora sin interacción, la app se cierra y vuelve la carátula normal.
private const val AUTO_EXIT_MS = 3_600_000L
private const val SEP = ""

/** Jugador en el reloj: nombre + 18 golpes + yardas por palo (espejo del cel). */
class WPlayer(name0: String) {
    var name by mutableStateOf(name0)
    val strokes = mutableStateListOf<Int>().apply { repeat(18) { add(0) } }
    val clubs = mutableStateListOf<Int>().apply { addAll(defaultClubYards) }
}


class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private lateinit var lm: LocationManager
    private val dataClient: DataClient by lazy { Wearable.getDataClient(this) }
    private lateinit var ambient: AmbientLifecycleObserver
    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable { finish() }

    private var lat by mutableStateOf<Double?>(null)
    private var lng by mutableStateOf<Double?>(null)
    private var granted by mutableStateOf(false)
    private var holeIdx by mutableStateOf(0)
    private var auto by mutableStateOf(true)
    private var useMeters by mutableStateOf(false)
    private val wplayers = mutableStateListOf<WPlayer>()
    private var activePlayer by mutableStateOf(0)
    private val flags = mutableStateListOf<Int>().apply { repeat(18) { add(-1) } }
    private var showScorecard by mutableStateOf(false)
    private var stateTs = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            lat = l.latitude; lng = l.longitude
            if (auto) {
                val n = WearCourse.nearestByTee(l.latitude, l.longitude)
                if (meters(l.latitude, l.longitude, n.teeLat, n.teeLng) <= 150.0 &&
                    holeIdx != n.number - 1) {
                    holeIdx = n.number - 1
                    persist() // GPS local; no publica para evitar ping-pong
                }
            }
        }
        @Deprecated("Deprecated in API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            granted = res[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (granted) { startGps(); startRoundService() }
        }

    private fun requiredPermissions(): Array<String> =
        if (android.os.Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    /** Servicio de ronda (Ongoing Activity): chip en la carátula para volver. */
    private fun startRoundService() {
        ContextCompat.startForegroundService(
            this, android.content.Intent(this, RoundService::class.java)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sin FLAG_KEEP_SCREEN_ON: la pantalla se apaga con el timeout del
        // reloj y se prende al levantar la muñeca, volviendo a la app.
        lm = getSystemService(LOCATION_SERVICE) as LocationManager

        loadLocal()
        setContent { WatchApp() }

        // Modo Ambient: al bajar la muñeca la app NO se cierra; se queda activa
        // con la pantalla apagada/atenuada y vuelve a la app al levantar la muñeca.
        ambient = AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                if (granted) startGps(fast = false)   // GPS lento: ahorra batería
            }
            override fun onExitAmbient() {
                resetIdle()
                if (granted) startGps(fast = true)    // GPS rápido al ver el reloj
            }
            override fun onUpdateAmbient() {}
        })
        lifecycle.addObserver(ambient)
        resetIdle()

        // Trae el último snapshot publicado por el teléfono.
        dataClient.dataItems.addOnSuccessListener { items ->
            for (item in items) {
                if (item.uri.path == STATE_PATH) {
                    applyIncoming(DataMapItem.fromDataItem(item).dataMap)
                }
            }
            items.release()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            granted = true
            startGps()
            startRoundService()
        } else {
            permLauncher.launch(requiredPermissions())
        }
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(this)
        if (granted) startGps(fast = true)
    }

    override fun onPause() {
        super.onPause()
        dataClient.removeListener(this)
        // En segundo plano (carátula visible, ronda viva por el servicio):
        // GPS lento para no drenar batería.
        if (granted) startGps(fast = false)
    }

    /**
     * GPS adaptativo para cuidar batería en rondas de 4+ horas:
     * fast=true (pantalla activa) cada 1 s · fast=false (ambient, muñeca
     * abajo — la mayor parte de la ronda) cada 8 s / 8 m.
     */
    private fun startGps(fast: Boolean = true) {
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.removeUpdates(listener)
                if (fast) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener)
                else lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 8000L, 8f, listener)
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                    lat = it.latitude; lng = it.longitude
                }
            }
        } catch (_: SecurityException) {}
    }

    // ---- Persistencia local ----
    private fun loadLocal() {
        val prefs = getSharedPreferences("wear", MODE_PRIVATE)
        val names = prefs.getString("names", null)?.split(SEP)?.filter { it.isNotEmpty() }
        val scores = prefs.getString("scores", null)?.split(SEP)
        val clubs = prefs.getString("clubs", null)?.split(SEP)
        if (names != null && names.isNotEmpty()) {
            names.forEachIndexed { i, nm ->
                val wp = WPlayer(nm)
                scores?.getOrNull(i)?.split(",")?.mapNotNull { it.toIntOrNull() }
                    ?.takeIf { it.size == 18 }?.forEachIndexed { h, v -> wp.strokes[h] = v }
                clubs?.getOrNull(i)?.split(",")?.mapNotNull { it.toIntOrNull() }
                    ?.takeIf { it.size == clubNames.size }?.forEachIndexed { c, v -> wp.clubs[c] = v }
                wplayers.add(wp)
            }
        }
        if (wplayers.isEmpty()) wplayers.add(WPlayer("P1"))
        activePlayer = prefs.getInt("active", 0).coerceIn(0, wplayers.size - 1)
        holeIdx = prefs.getInt("hole", 0).coerceIn(0, 17)
        useMeters = prefs.getString("units", "YARDS") == "METERS"
        auto = prefs.getBoolean("auto", true)
        prefs.getString("flags", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 18 }?.forEachIndexed { i, v -> flags[i] = v }
        stateTs = prefs.getLong("stateTs", 0L)
    }

    private fun persist() {
        getSharedPreferences("wear", MODE_PRIVATE).edit()
            .putString("names", wplayers.joinToString(SEP) { it.name })
            .putString("scores", wplayers.joinToString(SEP) { p -> p.strokes.joinToString(",") })
            .putString("clubs", wplayers.joinToString(SEP) { p -> p.clubs.joinToString(",") })
            .putInt("active", activePlayer)
            .putInt("hole", holeIdx)
            .putString("units", if (useMeters) "METERS" else "YARDS")
            .putBoolean("auto", auto)
            .putString("flags", flags.joinToString(","))
            .putLong("stateTs", stateTs)
            .apply()
        // Refresca el tile y la complicación de la carátula con el hoyo/golpes actuales.
        runCatching {
            androidx.wear.tiles.TileService.getUpdater(this)
                .requestUpdate(RoundTileService::class.java)
        }
        runCatching {
            androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
                .create(this, android.content.ComponentName(this, RoundComplicationService::class.java))
                .requestUpdateAll()
        }
    }

    // ---- Sincronización con el teléfono ----
    private fun pushState() {
        val req = PutDataMapRequest.create(STATE_PATH).apply {
            dataMap.putString(KEY_NAMES, wplayers.joinToString(SEP) { it.name })
            dataMap.putString(KEY_SCORES, wplayers.joinToString(SEP) { p -> p.strokes.joinToString(",") })
            dataMap.putInt(KEY_ACTIVE, activePlayer)
            dataMap.putInt(KEY_HOLE, holeIdx)
            dataMap.putString(KEY_UNITS, if (useMeters) "METERS" else "YARDS")
            dataMap.putBoolean(KEY_AUTO, auto)
            dataMap.putString(KEY_FLAGS, flags.joinToString(","))
            dataMap.putString(KEY_CLUBS, wplayers.joinToString(SEP) { p -> p.clubs.joinToString(",") })
            dataMap.putLong(KEY_TS, stateTs)
        }
        dataClient.putDataItem(req.asPutDataRequest().setUrgent())
    }

    private fun applyIncoming(dm: DataMap) {
        val ts = dm.getLong(KEY_TS)
        if (ts <= stateTs) return
        val names = dm.getString(KEY_NAMES)?.split(SEP)?.filter { it.isNotEmpty() } ?: return
        val scores = dm.getString(KEY_SCORES)?.split(SEP) ?: return
        if (names.isEmpty()) return
        while (wplayers.size < names.size) wplayers.add(WPlayer("P${wplayers.size + 1}"))
        while (wplayers.size > names.size) wplayers.removeAt(wplayers.size - 1)
        val clubsIn = dm.getString(KEY_CLUBS)?.split(SEP)
        names.forEachIndexed { i, nm ->
            wplayers[i].name = nm
            scores.getOrNull(i)?.split(",")?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == 18 }?.forEachIndexed { h, v -> wplayers[i].strokes[h] = v }
            clubsIn?.getOrNull(i)?.split(",")?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == clubNames.size }?.forEachIndexed { c, v -> wplayers[i].clubs[c] = v }
        }
        activePlayer = dm.getInt(KEY_ACTIVE, activePlayer).coerceIn(0, wplayers.size - 1)
        holeIdx = dm.getInt(KEY_HOLE, holeIdx).coerceIn(0, 17)
        useMeters = dm.getString(KEY_UNITS, if (useMeters) "METERS" else "YARDS") == "METERS"
        auto = dm.getBoolean(KEY_AUTO, auto)
        dm.getString(KEY_FLAGS)?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 18 }?.forEachIndexed { i, v -> flags[i] = v }
        stateTs = ts
        persist()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == STATE_PATH) {
                applyIncoming(DataMapItem.fromDataItem(event.dataItem).dataMap)
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetIdle()
    }

    /** Reinicia el temporizador de auto-cierre (1 h de inactividad). */
    private fun resetIdle() {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, AUTO_EXIT_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        idleHandler.removeCallbacks(idleRunnable)
        lm.removeUpdates(listener)
        stopService(android.content.Intent(this, RoundService::class.java))
    }

    // ---- Mutaciones locales (guardan y publican) ----
    private fun bumpAndSync() {
        stateTs = System.currentTimeMillis()
        persist()
        pushState()
    }

    private fun prevHole() { auto = false; holeIdx = (holeIdx + 17) % 18; bumpAndSync() }
    private fun nextHole() { auto = false; holeIdx = (holeIdx + 1) % 18; bumpAndSync() }
    private fun toggleAuto() { auto = !auto; bumpAndSync() }

    private fun cyclePlayer() {
        if (wplayers.size > 1) {
            activePlayer = (activePlayer + 1) % wplayers.size
            bumpAndSync()
        }
    }

    private fun changeStroke(delta: Int) {
        val p = wplayers.getOrNull(activePlayer) ?: return
        val v = p.strokes[holeIdx] + delta
        if (v in 0..15) { p.strokes[holeIdx] = v; bumpAndSync() }
    }

    private fun distVal(m: Double): Int = if (useMeters) m.roundToInt() else yards(m)

    @androidx.compose.runtime.Composable
    private fun WatchApp() {
        MaterialTheme {
            Scaffold(timeText = { TimeText() }) {
                if (showScorecard) {
                    ScorecardView()
                    return@Scaffold
                }
                val hole = WearCourse.holes[holeIdx]
                val feat = wearFeatures[hole.number] ?: WFeatures(0f, emptyList())
                // Ajuste por pin del día (sincronizado desde el cel):
                // frente ≈ -depth/4 · fondo ≈ +depth/4
                val flag = flags.getOrNull(holeIdx) ?: -1
                val pinShift = when (flag) { 0 -> -hole.depthM / 4.0; 2 -> hole.depthM / 4.0; else -> 0.0 }
                // rawM = al centro real del green · distM = al pin del día.
                // F/B son los bordes FIJOS del green (medidos en satélite);
                // solo el número grande sigue al pin.
                val rawM = if (lat != null && lng != null)
                    meters(lat!!, lng!!, hole.greenLat, hole.greenLng) else null
                val distM = rawM?.plus(pinShift)?.coerceAtLeast(0.0)
                val half = hole.depthM / 2.0
                val center = distM?.let { distVal(it) }
                val front = rawM?.let { distVal((it - half).coerceAtLeast(0.0)) }
                val back = rawM?.let { distVal(it + half) }
                val player = wplayers.getOrNull(activePlayer)
                val strokeVal = player?.strokes?.getOrNull(holeIdx) ?: 0

                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind { drawMiniHole(hole, feat, lat, lng, flag) }
                        .pointerInput(Unit) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (total <= -45f) nextHole()
                                    else if (total >= 45f) prevHole()
                                },
                                onHorizontalDrag = { _, dx -> total += dx }
                            )
                        }
                        .pointerInput(Unit) {
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = { if (total <= -60f) showScorecard = true },
                                onVerticalDrag = { _, dy -> total += dy }
                            )
                        }
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(top = 24.dp, bottom = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // ---- Encabezado: hoyo + par + jugador activo ----
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { toggleAuto() }
                            ) {
                                Text("‹", fontSize = 15.sp, color = Dim)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "HOYO ${hole.number}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (auto) Mint else Color.White
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("›", fontSize = 15.sp, color = Dim)
                            }
                            Text(
                                "PAR ${hole.par}" + if (auto) " · GPS" else "",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Dim
                            )
                            if (wplayers.size > 1 && player != null) {
                                Text(
                                    "▸ ${player.name}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.clickable { cyclePlayer() }
                                )
                            }
                        }

                        // ---- Distancias (izquierda) · mapa a la derecha ----
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier.weight(1.05f).padding(start = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy((-2).dp)
                            ) {
                                Text(
                                    back?.toString() ?: "–",
                                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                                Text(
                                    center?.toString() ?: (if (granted) "– –" else "GPS?"),
                                    fontSize = 42.sp, fontWeight = FontWeight.Black, color = Color.White
                                )
                                Text(
                                    front?.toString() ?: "–",
                                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                                // Palo sugerido (siempre calculado en yardas).
                                val club = if (distM != null && player != null)
                                    clubForDistance(yards(distM).toDouble(), player.clubs) else ""
                                if (club.isNotEmpty()) {
                                    Text(
                                        club,
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Mint
                                    )
                                }
                            }
                            Spacer(Modifier.weight(0.95f))
                        }

                        // ---- Golpes del jugador activo (sincroniza con el cel) ----
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { changeStroke(-1) },
                                modifier = Modifier.size(42.dp),
                                colors = ButtonDefaults.secondaryButtonColors()
                            ) { Text("−", fontSize = 22.sp) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$strokeVal",
                                    Modifier.width(40.dp),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Text("GOLPES", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Dim)
                            }
                            Button(
                                onClick = { changeStroke(1) },
                                modifier = Modifier.size(42.dp)
                            ) { Text("+", fontSize = 22.sp) }
                        }
                    }
                }
            }
        }
    }

    /** Scorecard de la ronda (abre con swipe arriba; cierra con swipe a la derecha). */
    @androidx.compose.runtime.Composable
    private fun ScorecardView() {
        SwipeToDismissBox(onDismissed = { showScorecard = false }) { isBackground ->
            if (isBackground) {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().background(Color.Black)
                )
                return@SwipeToDismissBox
            }
            val p = wplayers.getOrNull(activePlayer)
            ScalingLazyColumn(
                Modifier.fillMaxSize().background(Color.Black)
            ) {
                item {
                    Text(
                        "TARJETA" + if (wplayers.size > 1) " · ▸ ${p?.name ?: ""}" else "",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.clickable { cyclePlayer() }
                    )
                }
                items(18) { i ->
                    val h = WearCourse.holes[i]
                    val s = p?.strokes?.getOrNull(i) ?: 0
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 30.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "H${i + 1} · Par ${h.par}",
                            fontSize = 13.sp,
                            fontWeight = if (i == holeIdx) FontWeight.Bold else FontWeight.Normal,
                            color = if (i == holeIdx) Mint else Dim
                        )
                        Text(
                            if (s > 0) "$s" else "–",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = if (s > 0) Color.White else Dim
                        )
                    }
                }
                item {
                    val strokes = p?.strokes ?: emptyList<Int>()
                    val out = (0 until 9).sumOf { strokes.getOrNull(it) ?: 0 }
                    val inn = (9 until 18).sumOf { strokes.getOrNull(it) ?: 0 }
                    var rel = 0
                    strokes.forEachIndexed { i, s ->
                        if (s > 0) rel += s - WearCourse.holes[i].par
                    }
                    val relTxt = when { rel == 0 -> "E"; rel > 0 -> "+$rel"; else -> "$rel" }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "OUT $out · IN $inn",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Dim
                        )
                        Text(
                            "TOTAL ${out + inn} · $relTxt",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Mint
                        )
                    }
                }
            }
        }
    }
}
