package mx.clubsanfrancisco.golfgps.wear

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.wear.compose.material.Scaffold
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
private val Amber = Color(0xFFF3B61F)

// Color del golpe según el score (igual que el teléfono):
// eagle+ naranja · birdie azul · par dorado · bogey+ blanco.
private fun scoreColor(diff: Int): Color = when {
    diff <= -2 -> Color(0xFFF0912B)
    diff == -1 -> Color(0xFF4DA3FF)
    diff == 0 -> Color(0xFFF3B61F)
    else -> Color.White
}

// Data Layer: snapshot completo de la ronda (mismo formato que la app de teléfono).
private const val STATE_PATH = "/round/state"
private const val KEY_NAMES = "names"
private const val KEY_SCORES = "scores"
private const val KEY_ACTIVE = "active"
private const val KEY_HOLE = "hole"
private const val KEY_UNITS = "units"
private const val KEY_AUTO = "auto"
private const val KEY_TS = "ts"
private const val SEP = ""

/** Jugador en el reloj: nombre + 18 golpes (espejo de la app de teléfono). */
class WPlayer(name0: String) {
    var name by mutableStateOf(name0)
    val strokes = mutableStateListOf<Int>().apply { repeat(18) { add(0) } }
}

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private lateinit var lm: LocationManager
    private val dataClient: DataClient by lazy { Wearable.getDataClient(this) }

    private var lat by mutableStateOf<Double?>(null)
    private var lng by mutableStateOf<Double?>(null)
    private var granted by mutableStateOf(false)
    private var holeIdx by mutableStateOf(0)
    private var auto by mutableStateOf(true)
    private var useMeters by mutableStateOf(false)
    private val wplayers = mutableStateListOf<WPlayer>()
    private var activePlayer by mutableStateOf(0)
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
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            if (ok) startGps()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sin FLAG_KEEP_SCREEN_ON: la pantalla se apaga con el timeout del
        // reloj y se prende al levantar la muñeca, volviendo a la app.
        lm = getSystemService(LOCATION_SERVICE) as LocationManager

        loadLocal()
        setContent { WatchApp() }

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
        } else {
            permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(this)
    }

    override fun onPause() {
        super.onPause()
        dataClient.removeListener(this)
    }

    private fun startGps() {
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener)
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
        if (names != null && names.isNotEmpty()) {
            names.forEachIndexed { i, nm ->
                val wp = WPlayer(nm)
                scores?.getOrNull(i)?.split(",")?.mapNotNull { it.toIntOrNull() }
                    ?.takeIf { it.size == 18 }?.forEachIndexed { h, v -> wp.strokes[h] = v }
                wplayers.add(wp)
            }
        }
        if (wplayers.isEmpty()) wplayers.add(WPlayer("P1"))
        activePlayer = prefs.getInt("active", 0).coerceIn(0, wplayers.size - 1)
        holeIdx = prefs.getInt("hole", 0).coerceIn(0, 17)
        useMeters = prefs.getString("units", "YARDS") == "METERS"
        auto = prefs.getBoolean("auto", true)
        stateTs = prefs.getLong("stateTs", 0L)
    }

    private fun persist() {
        getSharedPreferences("wear", MODE_PRIVATE).edit()
            .putString("names", wplayers.joinToString(SEP) { it.name })
            .putString("scores", wplayers.joinToString(SEP) { p -> p.strokes.joinToString(",") })
            .putInt("active", activePlayer)
            .putInt("hole", holeIdx)
            .putString("units", if (useMeters) "METERS" else "YARDS")
            .putBoolean("auto", auto)
            .putLong("stateTs", stateTs)
            .apply()
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
        names.forEachIndexed { i, nm ->
            wplayers[i].name = nm
            scores.getOrNull(i)?.split(",")?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == 18 }?.forEachIndexed { h, v -> wplayers[i].strokes[h] = v }
        }
        activePlayer = dm.getInt(KEY_ACTIVE, activePlayer).coerceIn(0, wplayers.size - 1)
        holeIdx = dm.getInt(KEY_HOLE, holeIdx).coerceIn(0, 17)
        useMeters = dm.getString(KEY_UNITS, if (useMeters) "METERS" else "YARDS") == "METERS"
        auto = dm.getBoolean(KEY_AUTO, auto)
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

    override fun onDestroy() {
        super.onDestroy()
        lm.removeUpdates(listener)
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
                val hole = WearCourse.holes[holeIdx]
                val feat = wearFeatures[hole.number] ?: WFeatures(0f, emptyList())
                val distM = if (lat != null && lng != null)
                    meters(lat!!, lng!!, hole.greenLat, hole.greenLng) else null
                val half = hole.depthM / 2.0
                val center = distM?.let { distVal(it) }
                val front = distM?.let { distVal((it - half).coerceAtLeast(0.0)) }
                val back = distM?.let { distVal(it + half) }
                val player = wplayers.getOrNull(activePlayer)
                val strokeVal = player?.strokes?.getOrNull(holeIdx) ?: 0

                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind { drawMiniHole(hole, feat, lat, lng) }
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
                                    "HOLE ${hole.number}",
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
                                    color = Amber,
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
                                    fontSize = 42.sp, fontWeight = FontWeight.Black, color = Amber
                                )
                                Text(
                                    front?.toString() ?: "–",
                                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                                Text(
                                    (if (useMeters) "M" else "YD") + " · B/C/F",
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Dim
                                )
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
                                    color = if (strokeVal > 0) scoreColor(strokeVal - hole.par) else Mint,
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
}
