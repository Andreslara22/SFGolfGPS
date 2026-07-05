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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

private val Mint = Color(0xFF7ADFA8)
private val Dim = Color(0xFF9BB8A8)

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

        // Restore strokes
        val saved = getSharedPreferences("wear", MODE_PRIVATE)
            .getString("strokes", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
        if (saved?.size == 18) saved.forEachIndexed { i, v -> strokes[i] = v }

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
            .putString("strokes", strokes.joinToString(",")).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        lm.removeUpdates(listener)
    }

    @androidx.compose.runtime.Composable
    private fun WatchApp() {
        MaterialTheme {
            Scaffold(timeText = { TimeText() }) {
                val hole = WearCourse.holes[holeIdx]
                val dist = if (lat != null && lng != null)
                    yards(meters(lat!!, lng!!, hole.greenLat, hole.greenLng)) else null

                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "HOLE ${hole.number} · PAR ${hole.par}" + if (auto) " · A" else "",
                        fontSize = 12.sp,
                        color = Mint,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        dist?.toString() ?: (if (granted) "– – –" else "GPS?"),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text("yd to green", fontSize = 11.sp, color = Dim)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { auto = false; holeIdx = (holeIdx + 17) % 18 },
                            modifier = Modifier.size(34.dp),
                            colors = ButtonDefaults.secondaryButtonColors()
                        ) { Text("◀", fontSize = 13.sp) }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { auto = !auto },
                            modifier = Modifier.size(34.dp),
                            colors = if (auto) ButtonDefaults.primaryButtonColors()
                                     else ButtonDefaults.secondaryButtonColors()
                        ) { Text("A", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { auto = false; holeIdx = (holeIdx + 1) % 18 },
                            modifier = Modifier.size(34.dp),
                            colors = ButtonDefaults.secondaryButtonColors()
                        ) { Text("▶", fontSize = 13.sp) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { if (strokes[holeIdx] > 0) { strokes[holeIdx]--; persist() } },
                            modifier = Modifier.size(38.dp),
                            colors = ButtonDefaults.secondaryButtonColors()
                        ) { Text("−", fontSize = 17.sp) }
                        Text(
                            "${strokes[holeIdx]}",
                            Modifier.width(44.dp),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = Mint,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { if (strokes[holeIdx] < 15) { strokes[holeIdx]++; persist() } },
                            modifier = Modifier.size(38.dp)
                        ) { Text("+", fontSize = 17.sp) }
                    }
                }
            }
        }
    }
}
