package mx.clubsanfrancisco.golfgps

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
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    private val vm: GolfViewModel by viewModels()
    private var locationManager: LocationManager? = null

    // GPS adaptativo: quieto (tee/green/carrito) baja el ritmo; al moverse sube.
    private var gpsFast = true
    private var lastFix: Location? = null
    private var stillSinceMs = 0L

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            vm.onLocation(
                location.latitude, location.longitude, location.accuracy,
                if (location.hasAltitude()) location.altitude else null
            )
            val prev = lastFix
            lastFix = location
            val now = System.currentTimeMillis()
            val moved = prev == null || prev.distanceTo(location) > 4f
            if (moved) {
                stillSinceMs = now
                if (!gpsFast) { gpsFast = true; startLocationUpdates() }
            } else if (gpsFast && now - stillSinceMs > 20_000) {
                gpsFast = false; startLocationUpdates()
            }
        }
        @Deprecated("Deprecated in API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.hasLocationPermission = granted
            if (granted) startLocationUpdates()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantener la pantalla encendida durante la ronda.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        setContent {
            SFGolfTheme(mode = vm.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GolfApp(vm = vm, onRequestPermission = ::requestLocation)
                }
            }
        }

        requestLocation()
    }

    private fun requestLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            vm.hasLocationPermission = true
            startLocationUpdates()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onResume() {
        super.onResume()
        if (vm.hasLocationPermission) startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        locationManager?.removeUpdates(locationListener)
    }

    private fun startLocationUpdates() {
        try {
            val lm = locationManager ?: return
            lm.removeUpdates(locationListener) // avoid duplicate registrations
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    if (gpsFast) 1000L else 6000L,  // en movimiento 1 s · quieto 6 s
                    0f,
                    locationListener
                )
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                    vm.onLocation(
                        it.latitude, it.longitude, it.accuracy,
                        if (it.hasAltitude()) it.altitude else null
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permiso revocado en tiempo de ejecución.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager?.removeUpdates(locationListener)
    }
}
