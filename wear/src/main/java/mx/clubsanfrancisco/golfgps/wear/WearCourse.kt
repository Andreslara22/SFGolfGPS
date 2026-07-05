package mx.clubsanfrancisco.golfgps.wear

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class WHole(
    val number: Int, val par: Int,
    val teeLat: Double, val teeLng: Double,
    val greenLat: Double, val greenLng: Double
)

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
