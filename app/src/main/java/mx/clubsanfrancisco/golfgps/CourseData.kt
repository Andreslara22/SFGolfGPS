package mx.clubsanfrancisco.golfgps

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Hole(
    val number: Int,
    val par: Int,
    val teeLat: Double,
    val teeLng: Double,
    val greenLat: Double,
    val greenLng: Double,
    /**
     * Profundidad del green (frente a fondo) en metros, medida sobre la línea
     * de juego. Estimación por par; se puede afinar hoyo por hoyo midiendo
     * en imagen satelital. F/B = centro ∓ depth/2.
     */
    val greenDepthM: Double = 26.0,
    /**
     * Stroke index (índice de handicap del hoyo, 1 = más difícil). Reparte
     * los golpes de ventaja en juegos con handicap (Stableford). Estimado por
     * longitud relativa al par (impares en la ida, pares en la vuelta, como
     * marca la convención); sustitúyelo por el índice oficial de la tarjeta
     * del club cuando lo tengas a la mano.
     */
    val strokeIndex: Int = 18
) {
    /** Distancia de referencia tee -> centro del green, en metros. */
    val referenceMeters: Double
        get() = haversineMeters(teeLat, teeLng, greenLat, greenLng)
}

object CourseData {

    const val CLUB_NAME = "Club de Golf San Francisco"
    const val CITY = "Chihuahua, México"

    // greenDepthM: par 3 ≈ 22 m · par 4 ≈ 26 m · par 5 ≈ 30 m (afinable por hoyo).
    // strokeIndex (ventaja): tomado de la tarjeta oficial del club (jul 2026).
    val holes: List<Hole> = listOf(
        Hole(1, 4, 28.6613188, -106.1344914, 28.6637900, -106.1353286, greenDepthM = 26.0, strokeIndex = 12),
        Hole(2, 3, 28.6632884, -106.1362512, 28.6630801, -106.1377442, greenDepthM = 22.0, strokeIndex = 16),
        Hole(3, 4, 28.6631013, -106.1386025, 28.6647702, -106.1359937, greenDepthM = 26.0, strokeIndex = 2),
        Hole(4, 5, 28.6644922, -106.1348907, 28.6624691, -106.1317716, greenDepthM = 30.0, strokeIndex = 6),
        Hole(5, 4, 28.6619495, -106.1317498, 28.6590749, -106.1327969, greenDepthM = 26.0, strokeIndex = 8),
        Hole(6, 4, 28.6594812, -106.1328683, 28.6621990, -106.1323540, greenDepthM = 26.0, strokeIndex = 14),
        Hole(7, 3, 28.6612429, -106.1329173, 28.6598178, -106.1331181, greenDepthM = 22.0, strokeIndex = 18),
        Hole(8, 4, 28.6589552, -106.1333558, 28.6577331, -106.1361134, greenDepthM = 26.0, strokeIndex = 10),
        Hole(9, 5, 28.6570199, -106.1369845, 28.6610908, -106.1362338, greenDepthM = 30.0, strokeIndex = 4),
        Hole(10, 3, 28.6613291, -106.1383977, 28.6615612, -106.1395614, greenDepthM = 22.0, strokeIndex = 17),
        Hole(11, 4, 28.6603576, -106.1403909, 28.6633666, -106.1403051, greenDepthM = 26.0, strokeIndex = 1),
        Hole(12, 5, 28.6642063, -106.1409753, 28.6654939, -106.1445510, greenDepthM = 30.0, strokeIndex = 5),
        Hole(13, 4, 28.6647999, -106.1435958, 28.6632834, -106.1410095, greenDepthM = 26.0, strokeIndex = 11),
        Hole(14, 3, 28.6625444, -106.1411087, 28.6612467, -106.1408100, greenDepthM = 22.0, strokeIndex = 15),
        Hole(15, 4, 28.6598472, -106.1405002, 28.6570123, -106.1401371, greenDepthM = 26.0, strokeIndex = 7),
        Hole(16, 4, 28.6562691, -106.1408539, 28.6539022, -106.1412435, greenDepthM = 26.0, strokeIndex = 13),
        Hole(17, 4, 28.6532590, -106.1406973, 28.6560882, -106.1399239, greenDepthM = 26.0, strokeIndex = 9),
        Hole(18, 5, 28.6573424, -106.1383306, 28.6614197, -106.1376788, greenDepthM = 30.0, strokeIndex = 3)
    )

    val totalPar: Int = holes.sumOf { it.par } // 72

    // Para el handicap index (WHS): diferencial = (score − rating) × 113 / slope.
    // Rating/slope oficiales de la tarjeta del club, salidas BLANCAS (6088 yd).
    // Otras salidas: Azules 71.6/139 · Doradas 66.4/119 · Rojas 69.8/135.
    const val COURSE_RATING = 69.1
    const val SLOPE_RATING = 131

    /** Hoyo con el tee más cercano a la posición dada. */
    fun nearestHoleByTee(lat: Double, lng: Double): Hole =
        holes.minBy { haversineMeters(lat, lng, it.teeLat, it.teeLng) }
}

/** Fórmula de Haversine. Devuelve metros. */
fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

fun metersToYards(m: Double): Double = m * 1.0936133

/** Recomendación de palo según distancia en yardas al centro del green. */
fun recommendedClub(yards: Double): String = when {
    yards >= 240 -> "Driver"
    yards >= 220 -> "Madera 3"
    yards >= 200 -> "Madera 5 / Híbrido"
    yards >= 185 -> "Hierro 4"
    yards >= 170 -> "Hierro 5"
    yards >= 160 -> "Hierro 6"
    yards >= 150 -> "Hierro 7"
    yards >= 140 -> "Hierro 8"
    yards >= 130 -> "Hierro 9"
    yards >= 110 -> "Pitching Wedge"
    yards >= 95 -> "Gap Wedge (52°)"
    yards >= 80 -> "Sand Wedge (56°)"
    yards >= 40 -> "Lob Wedge / Approach"
    else -> "Chip / Putter"
}

// ---------------------------------------------------------------- Mapa por hoyo

/** t: posición 0..1 del tee al green · side: -1 izquierda, +1 derecha · size: escala relativa */
class Bunker(val t: Float, val side: Float, val size: Float = 1f)
class Water(val t: Float, val side: Float, val w: Float, val h: Float)

/** dogleg: curvatura del fairway (-1 izquierda fuerte ... +1 derecha fuerte) */
class HoleFeatures(
    val dogleg: Float,
    val bunkers: List<Bunker>,
    val water: Water? = null,
    val trees: Int = 4
)

// Trazado por hoyo basado en imagen satelital del club (jul 2026).
// Hoyos 1-6 verificados contra capturas satelitales de Andres (jul 2026):
// 1: bunker grande dcha del green, lago chico izq · 2: bunker grande dcha
// + chico izq, árboles densos dcha · 3: lago con fuente izq ~70%, bunker
// izq del green · 6: bunker medio-izq + racimo en green, árboles al centro.
// Agua confirmada resto: lago del 9 (izq) / 18 (der), circuito norte 12-13,
// lago sur junto al green del 15.
val holeFeatures: Map<Int, HoleFeatures> = mapOf(
    1 to HoleFeatures(0.10f, listOf(Bunker(0.92f, 1f, 1.15f)),
        water = Water(0.84f, -1f, 0.9f, 0.8f), trees = 4),
    2 to HoleFeatures(0f, listOf(Bunker(0.82f, 1f, 1.2f), Bunker(0.86f, -1f, 0.75f)), trees = 5),
    3 to HoleFeatures(-0.15f, listOf(Bunker(0.92f, -1f, 1.0f)),
        water = Water(0.70f, -1f, 1.4f, 1.2f), trees = 4),
    4 to HoleFeatures(0.30f, listOf(Bunker(0.50f, -1f), Bunker(0.90f, 1f, 0.9f)), trees = 6),
    5 to HoleFeatures(-0.10f, listOf(Bunker(0.55f, 1f), Bunker(0.90f, -1f, 1.0f)), trees = 4),
    6 to HoleFeatures(0.05f, listOf(Bunker(0.35f, -1f, 0.8f), Bunker(0.90f, -1f, 1.0f),
        Bunker(0.93f, 1f, 0.8f)), trees = 6),
    7 to HoleFeatures(0f, listOf(Bunker(0.82f, -1f), Bunker(0.85f, 1f)), trees = 3),
    8 to HoleFeatures(0.20f, listOf(Bunker(0.90f, -1f)), trees = 4),
    9 to HoleFeatures(-0.10f, listOf(Bunker(0.90f, 1f, 0.9f)),
        water = Water(0.55f, -1f, 1.8f, 1.4f), trees = 5),
    10 to HoleFeatures(0f, listOf(Bunker(0.85f, 1f)), trees = 3),
    11 to HoleFeatures(0.05f, listOf(Bunker(0.55f, 1f), Bunker(0.90f, -1f)), trees = 5),
    12 to HoleFeatures(0.35f, listOf(Bunker(0.70f, 1f), Bunker(0.90f, -1f, 0.8f)),
        water = Water(0.40f, -1f, 1.5f, 1.2f), trees = 6),
    13 to HoleFeatures(-0.30f, listOf(Bunker(0.88f, 1f)),
        water = Water(0.50f, 1f, 1.1f, 0.9f), trees = 5),
    14 to HoleFeatures(0f, listOf(Bunker(0.83f, -1f), Bunker(0.86f, 1f, 0.9f)), trees = 3),
    15 to HoleFeatures(0.05f, listOf(Bunker(0.60f, -1f)),
        water = Water(0.88f, 1f, 1.5f, 1.2f), trees = 4),
    16 to HoleFeatures(0.15f, listOf(Bunker(0.88f, -1f)), trees = 5),
    17 to HoleFeatures(-0.15f, listOf(Bunker(0.60f, 1f), Bunker(0.90f, -1f, 0.9f)), trees = 4),
    18 to HoleFeatures(0.25f, listOf(Bunker(0.45f, -1f), Bunker(0.90f, -1f)),
        water = Water(0.55f, 1f, 1.7f, 1.3f), trees = 6)
)

// ---------------------------------------------------------------- Palos por jugador

val clubNames = listOf(
    "Driver", "3 Wood", "5 Wood", "Hybrid", "4 Iron", "5 Iron", "6 Iron",
    "7 Iron", "8 Iron", "9 Iron", "PW", "GW", "SW", "LW"
)
val defaultClubYards = listOf(230, 215, 200, 190, 185, 175, 165, 155, 145, 135, 120, 105, 90, 75)

/** Índice en clubNames del palo sugerido (preselección al medir golpes). */
fun clubIndexForDistance(yards: Double, dist: List<Int>): Int {
    if (dist.size != clubNames.size) return clubNames.size - 1
    for (i in dist.indices.reversed()) {
        if (dist[i] >= yards) return i
    }
    return 0
}

/** El palo más corto cuyo alcance cubre la distancia. */
fun clubForDistance(yards: Double, dist: List<Int>): String {
    if (dist.size != clubNames.size) return recommendedClub(yards)
    if (yards < dist.last() * 0.55) return "Chip / Putter"
    for (i in dist.indices.reversed()) {
        if (dist[i] >= yards) return clubNames[i]
    }
    return clubNames[0]
}
