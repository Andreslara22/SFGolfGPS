package mx.clubsanfrancisco.golfgps.wear

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.WindowManager
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
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

private val Mint = Color(0xFF7ADFA8)
private val Dim = Color(0xFF9BB8A8)
private val Amber = Color(0xFFF3B61F)

// Data Layer: ruta y llaves compartidas con la app de teléfono.
private const val STROKES_PATH = "/round/strokes"
private const val KEY_CSV = "csv"
private const val KEY_TS = "ts"

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private lateinit var lm: LocationManager
    private val dataClient: DataClient by lazy { Wearable.getDataClient(this) }

    private var lat by mutableStateOf<Double?>(null)
    private var lng by mutableStateOf<Double?>(null)
    private var granted by mutableStateOf(false)
    private var holeIdx by mutableStateOf(0)
    private var auto by mutableStateOf(true)
    private val strokes = mutableStateListOf<Int>().apply { repeat(18) { add(0) } }
    private var strokesTs = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            lat = l.latitude; lng = l.longitude
            if (auto) {
                val n = WearCourse.nearestByTee(l.latitude, l.longitude)
                if (meters(l.latitude, l.longitude, n.teeLat, n.teeLng) <= 150.0) {
                    holeIdx = n.number - 1
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lm = getSystemService(LOCATION_SERVICE) as LocationManager

        val prefs = getSharedPreferences("wear", MODE_PRIVATE)
        prefs.getString("strokes", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 18 }?.forEachIndexed { i, v -> strokes[i] = v }
        strokesTs = prefs.getLong("strokesTs", 0L)

        setContent { WatchApp() }

        // Trae el último score publicado por el teléfono (si es más nuevo).
        dataClient.dataItems.addOnSuccessListener { items ->
            for (item in items) {
                if (item.uri.path == STROKES_PATH) {
                    val dm = DataMapItem.fromDataItem(item).dataMap
                    applyIncoming(dm.getString(KEY_CSV), dm.getLong(KEY_TS))
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

    private fun persist() {
        getSharedPreferences("wear", MODE_PRIVATE).edit()
            .putString("strokes", strokes.joinToString(","))
            .putLong("strokesTs", strokesTs)
            .apply()
    }

    /** Cambia los golpes del hoyo actual, guarda y publica al teléfono. */
    private fun changeStroke(delta: Int) {
        val v = strokes[holeIdx] + delta
        if (v in 0..15) {
            strokes[holeIdx] = v
            strokesTs = System.currentTimeMillis()
            persist()
            pushStrokes()
        }
    }

    /** Publica los 18 golpes en la Data Layer para que el teléfono los reciba. */
    private fun pushStrokes() {
        val req = PutDataMapRequest.create(STROKES_PATH).apply {
            dataMap.putString(KEY_CSV, strokes.joinToString(","))
            dataMap.putLong(KEY_TS, strokesTs)
        }
        dataClient.putDataItem(req.asPutDataRequest().setUrgent())
    }

    /** Aplica un score entrante si es más nuevo que el local (last-write-wins). */
    private fun applyIncoming(csv: String?, ts: Long) {
        if (csv == null || ts <= strokesTs) return
        val arr = csv.split(",").mapNotNull { it.toIntOrNull() }
        if (arr.size != 18) return
        arr.forEachIndexed { i, v -> strokes[i] = v }
        strokesTs = ts
        persist()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == STROKES_PATH) {
                val dm = DataMapItem.fromDataItem(event.dataItem).dataMap
                applyIncoming(dm.getString(KEY_CSV), dm.getLong(KEY_TS))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lm.removeUpdates(listener)
    }

    private fun prevHole() { auto = false; holeIdx = (holeIdx + 17) % 18 }
    private fun nextHole() { auto = false; holeIdx = (holeIdx + 1) % 18 }

    @androidx.compose.runtime.Composable
    private fun WatchApp() {
        MaterialTheme {
            Scaffold {
                val hole = WearCourse.holes[holeIdx]
                val feat = wearFeatures[hole.number] ?: WFeatures(0f, emptyList())
                val distM = if (lat != null && lng != null)
                    meters(lat!!, lng!!, hole.greenLat, hole.greenLng) else null
                val half = hole.depthM / 2.0
                val center = distM?.let { yards(it) }
                val front = distM?.let { yards((it - half).coerceAtLeast(0.0)) }
                val back = distM?.let { yards(it + half) }

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
                            .padding(top = 16.dp, bottom = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // ---- Encabezado: hoyo + par (toca para on/off GPS auto) ----
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { auto = !auto }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Dim
                            )
                        }

                        // ---- Distancias (izquierda) · mapa se ve a la derecha ----
                        Row(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    back?.toString() ?: "–",
                                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                                Text(
                                    center?.toString() ?: (if (granted) "– –" else "GPS?"),
                                    fontSize = 46.sp, fontWeight = FontWeight.Black, color = Amber
                                )
                                Text(
                                    front?.toString() ?: "–",
                                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                                Text(
                                    "YD · B/C/F",
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Dim
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }

                        // ---- Golpes (grande y fácil de picar, sincroniza con el cel) ----
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
                                    "${strokes[holeIdx]}",
                                    Modifier.width(40.dp),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Mint,
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
