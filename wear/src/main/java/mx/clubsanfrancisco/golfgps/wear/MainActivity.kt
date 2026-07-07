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

private val Mint = Color(0xFF7ADFA8)
private val Dim = Color(0xFF9BB8A8)
private val Amber = Color(0xFFF3B61F)

class MainActivity : ComponentActivity() {

    private lateinit var lm: LocationManager

    private var lat by mutableStateOf<Double?>(null)
    private var lng by mutableStateOf<Double?>(null)
    private var granted by mutableStateOf(false)
    private var holeIdx by mutableStateOf(0)
    private var auto by mutableStateOf(true)
    private val strokes = mutableStateListOf<Int>().apply { repeat(18) { add(0) } }

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

        setContent { WatchApp() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            granted = true
            startGps()
        } else {
            permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
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
            .apply()
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

                        // ---- Golpes (grande y fácil de picar) ----
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { if (strokes[holeIdx] > 0) { strokes[holeIdx]--; persist() } },
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
                                onClick = { if (strokes[holeIdx] < 15) { strokes[holeIdx]++; persist() } },
                                modifier = Modifier.size(42.dp)
                            ) { Text("+", fontSize = 22.sp) }
                        }
                    }
                }
            }
        }
    }
}
