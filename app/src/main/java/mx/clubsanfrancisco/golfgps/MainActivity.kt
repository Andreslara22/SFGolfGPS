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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: GolfViewModel by viewModels()
    private var locationManager: LocationManager? = null

    // GPS adaptativo: quieto (tee/green/carrito) baja el ritmo; al moverse sube.
    private var gpsFast = true
    private var lastFix: Location? = null
    private var stillSinceMs = 0L
    private var resumed = false

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
            syncGps()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        // Reacciona a: pantalla activa (Range), permiso y modo ahorro. Solo en
        // Range mantenemos la pantalla encendida y el GPS activo; fuera de ahí
        // se apagan ambos para ahorrar batería. Cambiar el ahorro re-registra
        // el GPS con la nueva cadencia.
        lifecycleScope.launch {
            snapshotFlow {
                Triple(vm.onRangeScreen, vm.hasLocationPermission, vm.batterySaver)
            }.distinctUntilChanged().collect { (onRange, _, _) ->
                keepScreenOn(onRange)
                syncGps()
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
            syncGps()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        syncGps()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        stopLocationUpdates()
    }

    private fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Enciende o apaga el GPS según pantalla activa, permiso y foreground. */
    private fun syncGps() {
        if (resumed && vm.onRangeScreen && vm.hasLocationPermission) startLocationUpdates()
        else stopLocationUpdates()
    }

    private fun startLocationUpdates() {
        try {
            val lm = locationManager ?: return
            lm.removeUpdates(locationListener) // avoid duplicate registrations
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                // Ahorro de batería: más segundos entre lecturas y filtro por
                // distancia, para que el chip GPS no trabaje de más.
                val moveMs = if (vm.batterySaver) 2000L else 1000L
                val stillMs = if (vm.batterySaver) 12000L else 6000L
                val minDist = if (vm.batterySaver) 3f else 0f
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    if (gpsFast) moveMs else stillMs,
                    minDist,
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

    private fun stopLocationUpdates() {
        locationManager?.removeUpdates(locationListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }
}
