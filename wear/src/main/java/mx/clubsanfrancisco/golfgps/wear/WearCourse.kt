package mx.clubsanfrancisco.golfgps.wear

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class WHole(
    val number: Int, val par: Int,
    val teeLat: Double, val teeLng: Double,
    val greenLat: Double, val greenLng: Double
) {
    /** Profundidad del green en metros (misma tabla que la app de teléfono). */
    val depthM: Double get() = when (par) { 3 -> 22.0; 5 -> 30.0; else -> 26.0 }
}

object WearCourse {
    val holes = listOf(
        WHole(1, 4, 28.6613188, -106.1344914, 28.6637900, -106.1353286),
        WHole(2, 3, 28.6632884, -106.1362512, 28.6630801, -106.1377442),
        WHole(3, 4, 28.6631013, -106.1386025, 28.6647702, -106.1359937),
        WHole(4, 5, 28.6644922, -106.1348907, 28.6624691, -106.1317716),
        WHole(5, 4, 28.6619495, -106.1317498, 28.6590749, -106.1327969),
        WHole(6, 4, 28.6594812, -106.1328683, 28.6621990, -106.1323540),
        WHole(7, 3, 28.6612429, -106.1329173, 28.6598178, -106.1331181),
        WHole(8, 4, 28.6589552, -106.1333558, 28.6577331, -106.1361134),
        WHole(9, 5, 28.6570199, -106.1369845, 28.6610908, -106.1362338),
        WHole(10, 3, 28.6613291, -106.1383977, 28.6615612, -106.1395614),
        WHole(11, 4, 28.6603576, -106.1403909, 28.6633666, -106.1403051),
        WHole(12, 5, 28.6642063, -106.1409753, 28.6654939, -106.1445510),
        WHole(13, 4, 28.6647999, -106.1435958, 28.6632834, -106.1410095),
        WHole(14, 3, 28.6625444, -106.1411087, 28.6612467, -106.1408100),
        WHole(15, 4, 28.6598472, -106.1405002, 28.6570123, -106.1401371),
        WHole(16, 4, 28.6562691, -106.1408539, 28.6539022, -106.1412435),
        WHole(17, 4, 28.6532590, -106.1406973, 28.6560882, -106.1399239),
        WHole(18, 5, 28.6573424, -106.1383306, 28.6614197, -106.1376788)
    )

    fun nearestByTee(lat: Double, lng: Double): WHole =
        holes.minBy { meters(lat, lng, it.teeLat, it.teeLng) }
}

// ---------------------------------------------------------------- Mapa por hoyo
// Portado de la app de teléfono (CourseData.holeFeatures). t: 0..1 tee->green,
// side: -1 izq / +1 der, size: escala. dogleg: curvatura del fairway.

class WBunker(val t: Float, val side: Float, val size: Float = 1f)
class WWater(val t: Float, val side: Float, val w: Float, val h: Float)
class WFeatures(val dogleg: Float, val bunkers: List<WBunker>, val water: WWater? = null)

val wearFeatures: Map<Int, WFeatures> = mapOf(
    1 to WFeatures(0.10f, listOf(WBunker(0.92f, 1f, 1.15f)), WWater(0.84f, -1f, 0.9f, 0.8f)),
    2 to WFeatures(0f, listOf(WBunker(0.82f, 1f, 1.2f), WBunker(0.86f, -1f, 0.75f))),
    3 to WFeatures(-0.15f, listOf(WBunker(0.92f, -1f, 1.0f)), WWater(0.70f, -1f, 1.4f, 1.2f)),
    4 to WFeatures(0.30f, listOf(WBunker(0.50f, -1f), WBunker(0.90f, 1f, 0.9f))),
    5 to WFeatures(-0.10f, listOf(WBunker(0.55f, 1f), WBunker(0.90f, -1f, 1.0f))),
    6 to WFeatures(0.05f, listOf(WBunker(0.35f, -1f, 0.8f), WBunker(0.90f, -1f, 1.0f),
        WBunker(0.93f, 1f, 0.8f))),
    7 to WFeatures(0f, listOf(WBunker(0.82f, -1f), WBunker(0.85f, 1f))),
    8 to WFeatures(0.20f, listOf(WBunker(0.90f, -1f))),
    9 to WFeatures(-0.10f, listOf(WBunker(0.90f, 1f, 0.9f)), WWater(0.55f, -1f, 1.8f, 1.4f)),
    10 to WFeatures(0f, listOf(WBunker(0.85f, 1f))),
    11 to WFeatures(0.05f, listOf(WBunker(0.55f, 1f), WBunker(0.90f, -1f))),
    12 to WFeatures(0.35f, listOf(WBunker(0.70f, 1f), WBunker(0.90f, -1f, 0.8f)),
        WWater(0.40f, -1f, 1.5f, 1.2f)),
    13 to WFeatures(-0.30f, listOf(WBunker(0.88f, 1f)), WWater(0.50f, 1f, 1.1f, 0.9f)),
    14 to WFeatures(0f, listOf(WBunker(0.83f, -1f), WBunker(0.86f, 1f, 0.9f))),
    15 to WFeatures(0.05f, listOf(WBunker(0.60f, -1f)), WWater(0.88f, 1f, 1.5f, 1.2f)),
    16 to WFeatures(0.15f, listOf(WBunker(0.88f, -1f))),
    17 to WFeatures(-0.15f, listOf(WBunker(0.60f, 1f), WBunker(0.90f, -1f, 0.9f))),
    18 to WFeatures(0.25f, listOf(WBunker(0.45f, -1f), WBunker(0.90f, -1f)),
        WWater(0.55f, 1f, 1.7f, 1.3f))
)

fun meters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

fun yards(m: Double): Int = (m * 1.0936133).toInt()

// ---------------------------------------------------------------- Palos
// Misma tabla que la app de teléfono (CourseData.kt).

val clubNames = listOf(
    "Driver", "3 Wood", "5 Wood", "Hybrid", "4 Iron", "5 Iron", "6 Iron",
    "7 Iron", "8 Iron", "9 Iron", "PW", "GW", "SW", "LW"
)
val defaultClubYards = listOf(230, 215, 200, 190, 185, 175, 165, 155, 145, 135, 120, 105, 90, 75)

/** El palo más corto cuyo alcance cubre la distancia (yardas). */
fun clubForDistance(yards: Double, dist: List<Int>): String {
    if (dist.size != clubNames.size) return ""
    if (yards < dist.last() * 0.55) return "Chip / Putter"
    for (i in dist.indices.reversed()) {
        if (dist[i] >= yards) return clubNames[i]
    }
    return clubNames[0]
}
