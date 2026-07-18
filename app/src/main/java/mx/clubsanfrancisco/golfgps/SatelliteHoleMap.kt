package mx.clubsanfrancisco.golfgps

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlin.math.roundToInt

private val MeasureBlue = Color(0xFF3D8BFF)

/**
 * ¿Hay llave de Google Maps configurada? Sin ella los mosaicos salen en gris,
 * así que en ese caso NO ofrecemos la vista satelital y seguimos con la
 * ilustración. La llave se inyecta como meta-data desde build.gradle.
 */
fun mapsApiKeyPresent(context: Context): Boolean = runCatching {
    val ai = context.packageManager.getApplicationInfo(
        context.packageName, PackageManager.GET_META_DATA
    )
    val key = ai.metaData?.getString("com.google.android.geo.API_KEY")?.trim()
    !key.isNullOrEmpty()
}.getOrDefault(false)

/**
 * Vista satelital real del hoyo (imágenes aéreas de Google Maps) con el mismo
 * overlay que la ilustración: tu posición, la línea al green con la distancia y
 * el cursor de medición (toca el mapa para un layup, toca de nuevo para quitar).
 * El encuadre se ajusta a tee↔green; el mapa se puede rotar y hacer paneo.
 */
@Composable
fun SatelliteHoleMap(
    hole: Hole,
    userLat: Double?,
    userLng: Double?,
    units: Units,
    modifier: Modifier = Modifier
) {
    val tee = LatLng(hole.teeLat, hole.teeLng)
    val green = LatLng(hole.greenLat, hole.greenLng)
    val mid = LatLng((tee.latitude + green.latitude) / 2, (tee.longitude + green.longitude) / 2)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mid, 16f)
    }
    var measure by remember(hole.number) { mutableStateOf<LatLng?>(null) }
    var mapLoaded by remember { mutableStateOf(false) }

    // Encuadra tee↔green cuando el mapa está listo (o al cambiar de hoyo). El SDK
    // calcula el zoom según el tamaño real de la vista, así que sale bien en
    // cualquier pantalla. Norte arriba; el usuario puede rotar con dos dedos.
    LaunchedEffect(hole.number, mapLoaded) {
        if (mapLoaded) {
            val bounds = LatLngBounds.builder().include(tee).include(green).build()
            runCatching {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, 150), 650
                )
            }
        }
    }

    val origin = if (userLat != null && userLng != null) LatLng(userLat, userLng) else tee
    val originLat = userLat ?: hole.teeLat
    val originLng = userLng ?: hole.teeLng

    fun fmt(m: Double): String = if (units == Units.YARDS)
        "${metersToYards(m).roundToInt()} yd" else "${m.roundToInt()} m"

    Box(modifier) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp)),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.SATELLITE),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = true,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
                tiltGesturesEnabled = false,
                rotationGesturesEnabled = true
            ),
            onMapLoaded = { mapLoaded = true },
            onMapClick = { latLng -> measure = latLng }
        ) {
            Marker(
                state = rememberMarkerState(key = "green-${hole.number}", position = green),
                title = "Green",
                snippet = fmt(haversineMeters(originLat, originLng, hole.greenLat, hole.greenLng)),
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
            Marker(
                state = rememberMarkerState(key = "tee-${hole.number}", position = tee),
                title = "Tee ${hole.number} · par ${hole.par}",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
            )

            // Línea de origen (tú o el tee) al green.
            Polyline(
                points = listOf(origin, green),
                color = Color.White,
                width = 6f,
                pattern = listOf(Dash(26f), Gap(18f))
            )

            if (userLat != null && userLng != null) {
                Marker(
                    state = rememberMarkerState(key = "me-$userLat-$userLng", position = origin),
                    title = "Tú",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            measure?.let { m ->
                val d1 = haversineMeters(originLat, originLng, m.latitude, m.longitude)
                val d2 = haversineMeters(m.latitude, m.longitude, hole.greenLat, hole.greenLng)
                Polyline(points = listOf(origin, m), color = MeasureBlue, width = 8f)
                Polyline(
                    points = listOf(m, green),
                    color = Color.White,
                    width = 5f,
                    pattern = listOf(Dash(20f), Gap(16f))
                )
                Marker(
                    state = rememberMarkerState(key = "m-${m.latitude}-${m.longitude}", position = m),
                    title = fmt(d1),
                    snippet = "→ green ${fmt(d2)}",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                )
            }
        }

        // Pastilla de distancia siempre visible abajo.
        val label = measure.let { m ->
            if (m != null) {
                val d1 = haversineMeters(originLat, originLng, m.latitude, m.longitude)
                val d2 = haversineMeters(m.latitude, m.longitude, hole.greenLat, hole.greenLng)
                "${fmt(d1)}  →  green ${fmt(d2)}"
            } else {
                "Green ${fmt(haversineMeters(originLat, originLng, hole.greenLat, hole.greenLng))}  ·  toca para medir"
            }
        }
        Surface(
            color = Color(0xCC1B3A2E),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
            )
        }

        if (measure != null) {
            Surface(
                color = Color(0xCC7A1B1B),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clickable { measure = null }
            ) {
                Text(
                    "✕",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                )
            }
        }
    }
}
