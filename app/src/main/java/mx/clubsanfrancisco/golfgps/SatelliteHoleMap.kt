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
import com.google.maps.android.compose.Circle
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
private val RingWhite = Color(0x66FFFFFF)
private val ClubGold = Color(0xF2FFD24A)
private val FrontRed = Color(0xFFE85D4A)
private val BackBlue = Color(0xFF5AB0FF)

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
 * overlay que la ilustración y algunos extras de rangefinder:
 *  - orientación "green arriba" (como la ilustración),
 *  - puntos Front / Center / Back del green con sus distancias,
 *  - anillos de distancia desde tu posición (layups),
 *  - anillo del palo sugerido (hasta dónde llega tu palo),
 *  - línea al green y cursor de medición (toca para medir, toca ✕ para quitar).
 *
 * @param clubRangeM alcance en metros del palo sugerido (para el anillo dorado)
 * @param clubLabel  nombre del palo sugerido (se ve al tocar el anillo)
 */
@Composable
fun SatelliteHoleMap(
    hole: Hole,
    userLat: Double?,
    userLng: Double?,
    units: Units,
    clubRangeM: Double? = null,
    clubLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val tee = LatLng(hole.teeLat, hole.teeLng)
    val green = LatLng(hole.greenLat, hole.greenLng)
    val mid = LatLng((tee.latitude + green.latitude) / 2, (tee.longitude + green.longitude) / 2)

    // Front / Center / Back: se proyectan sobre el eje tee→green según la
    // profundidad del green (greenDepthM), igual que la bandera de la pantalla.
    val brgTeeGreen = bearingBetween(tee.latitude, tee.longitude, green.latitude, green.longitude)
    val half = hole.greenDepthM / 2.0
    val frontPt = destinationPoint(green.latitude, green.longitude, (brgTeeGreen + 180.0) % 360.0, half)
    val backPt = destinationPoint(green.latitude, green.longitude, brgTeeGreen, half)
    val front = LatLng(frontPt.first, frontPt.second)
    val back = LatLng(backPt.first, backPt.second)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mid, 16f)
    }
    var measure by remember(hole.number) { mutableStateOf<LatLng?>(null) }
    var mapLoaded by remember { mutableStateOf(false) }

    // Encuadra tee↔green y luego rota a "green arriba". El zoom lo calcula el SDK
    // según el tamaño real de la vista, así sale bien en cualquier pantalla.
    LaunchedEffect(hole.number, mapLoaded) {
        if (mapLoaded) {
            val bounds = LatLngBounds.builder().include(tee).include(green).build()
            runCatching {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 170), 450)
                val z = (cameraPositionState.position.zoom - 0.35f).coerceAtLeast(1f)
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(mid).zoom(z).bearing(brgTeeGreen.toFloat()).build()
                    ),
                    450
                )
            }
        }
    }

    val origin = if (userLat != null && userLng != null) LatLng(userLat, userLng) else tee
    val originLat = userLat ?: hole.teeLat
    val originLng = userLng ?: hole.teeLng

    fun fmt(m: Double): String = if (units == Units.YARDS)
        "${metersToYards(m).roundToInt()} yd" else "${m.roundToInt()} m"
    fun fmtN(m: Double): String = if (units == Units.YARDS)
        "${metersToYards(m).roundToInt()}" else "${m.roundToInt()}"

    val dCenter = haversineMeters(originLat, originLng, hole.greenLat, hole.greenLng)
    val dFront = haversineMeters(originLat, originLng, front.latitude, front.longitude)
    val dBack = haversineMeters(originLat, originLng, back.latitude, back.longitude)

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
            // Anillos de distancia desde tu posición (solo los que caen antes del
            // green, para no saturar) + anillo del palo sugerido en dorado.
            if (userLat != null && userLng != null) {
                val targets = if (units == Units.YARDS)
                    listOf(100.0, 150.0, 200.0).map { yardsToMeters(it) }
                else listOf(100.0, 150.0, 200.0)
                targets.forEach { rM ->
                    if (rM < dCenter - 5) {
                        Circle(
                            center = origin, radius = rM,
                            strokeColor = RingWhite, strokeWidth = 3f,
                            strokePattern = listOf(Dash(22f), Gap(16f)),
                            fillColor = Color.Transparent
                        )
                    }
                }
                clubRangeM?.let { rM ->
                    if (rM in 20.0..350.0) {
                        Circle(
                            center = origin, radius = rM,
                            strokeColor = ClubGold, strokeWidth = 5f,
                            fillColor = Color.Transparent
                        )
                    }
                }
            }

            // Front / Center / Back del green.
            Marker(
                state = rememberMarkerState(key = "green-${hole.number}", position = green),
                title = "Green (centro)",
                snippet = fmt(dCenter),
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
            Circle(center = front, radius = 3.0, strokeColor = Color.White, strokeWidth = 2f, fillColor = FrontRed)
            Circle(center = back, radius = 3.0, strokeColor = Color.White, strokeWidth = 2f, fillColor = BackBlue)

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

        // Pastilla de distancia siempre visible abajo: al medir muestra el layup,
        // si no muestra Front / Center / Back del green.
        val unit = if (units == Units.YARDS) "yd" else "m"
        val label = measure.let { m ->
            if (m != null) {
                val d1 = haversineMeters(originLat, originLng, m.latitude, m.longitude)
                val d2 = haversineMeters(m.latitude, m.longitude, hole.greenLat, hole.greenLng)
                "${fmt(d1)}  →  green ${fmt(d2)}"
            } else {
                "F ${fmtN(dFront)} · C ${fmtN(dCenter)} · B ${fmtN(dBack)} $unit"
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

        // Etiqueta del palo sugerido (arriba, junto al anillo dorado).
        if (measure == null && clubLabel != null && userLat != null && userLng != null) {
            Surface(
                color = Color(0xE6B8860B),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 12.dp)
            ) {
                Text(
                    "🏌 $clubLabel",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
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
