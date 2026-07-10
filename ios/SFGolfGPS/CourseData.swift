import Foundation

/// Datos del campo — Club de Golf San Francisco (Chihuahua).
/// Espejo 1:1 de app/src/main/java/.../CourseData.kt: si afinas una coordenada
/// o un stroke index en Android, cópialo aquí también.

struct Hole {
    let number: Int
    let par: Int
    let teeLat: Double
    let teeLng: Double
    let greenLat: Double
    let greenLng: Double
    /// Profundidad del green (frente a fondo) en metros, medida sobre la línea
    /// de juego. F/B = centro ∓ depth/2.
    var greenDepthM: Double = 26.0
    /// Stroke index (1 = más difícil). Reparte los golpes de ventaja en
    /// juegos con handicap (Stableford).
    var strokeIndex: Int = 18

    /// Distancia de referencia tee -> centro del green, en metros.
    var referenceMeters: Double {
        haversineMeters(teeLat, teeLng, greenLat, greenLng)
    }
}

enum CourseData {

    static let clubName = "Club de Golf San Francisco"
    static let city = "Chihuahua, México"

    // greenDepthM: par 3 ≈ 22 m · par 4 ≈ 26 m · par 5 ≈ 30 m (afinable por hoyo).
    // strokeIndex (ventaja): tomado de la tarjeta oficial del club (jul 2026).
    static let holes: [Hole] = [
        Hole(number: 1, par: 4, teeLat: 28.6613188, teeLng: -106.1344914, greenLat: 28.6637900, greenLng: -106.1353286, greenDepthM: 26.0, strokeIndex: 12),
        Hole(number: 2, par: 3, teeLat: 28.6632884, teeLng: -106.1362512, greenLat: 28.6630801, greenLng: -106.1377442, greenDepthM: 22.0, strokeIndex: 16),
        Hole(number: 3, par: 4, teeLat: 28.6631013, teeLng: -106.1386025, greenLat: 28.6647702, greenLng: -106.1359937, greenDepthM: 26.0, strokeIndex: 2),
        Hole(number: 4, par: 5, teeLat: 28.6644922, teeLng: -106.1348907, greenLat: 28.6624691, greenLng: -106.1317716, greenDepthM: 30.0, strokeIndex: 6),
        Hole(number: 5, par: 4, teeLat: 28.6619495, teeLng: -106.1317498, greenLat: 28.6590749, greenLng: -106.1327969, greenDepthM: 26.0, strokeIndex: 8),
        Hole(number: 6, par: 4, teeLat: 28.6594812, teeLng: -106.1328683, greenLat: 28.6621990, greenLng: -106.1323540, greenDepthM: 26.0, strokeIndex: 14),
        Hole(number: 7, par: 3, teeLat: 28.6612429, teeLng: -106.1329173, greenLat: 28.6598178, greenLng: -106.1331181, greenDepthM: 22.0, strokeIndex: 18),
        Hole(number: 8, par: 4, teeLat: 28.6589552, teeLng: -106.1333558, greenLat: 28.6577331, greenLng: -106.1361134, greenDepthM: 26.0, strokeIndex: 10),
        Hole(number: 9, par: 5, teeLat: 28.6570199, teeLng: -106.1369845, greenLat: 28.6610908, greenLng: -106.1362338, greenDepthM: 30.0, strokeIndex: 4),
        Hole(number: 10, par: 3, teeLat: 28.6613291, teeLng: -106.1383977, greenLat: 28.6615612, greenLng: -106.1395614, greenDepthM: 22.0, strokeIndex: 17),
        Hole(number: 11, par: 4, teeLat: 28.6603576, teeLng: -106.1403909, greenLat: 28.6633666, greenLng: -106.1403051, greenDepthM: 26.0, strokeIndex: 1),
        Hole(number: 12, par: 5, teeLat: 28.6642063, teeLng: -106.1409753, greenLat: 28.6654939, greenLng: -106.1445510, greenDepthM: 30.0, strokeIndex: 5),
        Hole(number: 13, par: 4, teeLat: 28.6647999, teeLng: -106.1435958, greenLat: 28.6632834, greenLng: -106.1410095, greenDepthM: 26.0, strokeIndex: 11),
        Hole(number: 14, par: 3, teeLat: 28.6625444, teeLng: -106.1411087, greenLat: 28.6612467, greenLng: -106.1408100, greenDepthM: 22.0, strokeIndex: 15),
        Hole(number: 15, par: 4, teeLat: 28.6598472, teeLng: -106.1405002, greenLat: 28.6570123, greenLng: -106.1401371, greenDepthM: 26.0, strokeIndex: 7),
        Hole(number: 16, par: 4, teeLat: 28.6562691, teeLng: -106.1408539, greenLat: 28.6539022, greenLng: -106.1412435, greenDepthM: 26.0, strokeIndex: 13),
        Hole(number: 17, par: 4, teeLat: 28.6532590, teeLng: -106.1406973, greenLat: 28.6560882, greenLng: -106.1399239, greenDepthM: 26.0, strokeIndex: 9),
        Hole(number: 18, par: 5, teeLat: 28.6573424, teeLng: -106.1383306, greenLat: 28.6614197, greenLng: -106.1376788, greenDepthM: 30.0, strokeIndex: 3)
    ]

    static let totalPar: Int = holes.reduce(0) { $0 + $1.par } // 72

    // Para el handicap index (WHS): diferencial = (score − rating) × 113 / slope.
    // Rating/slope oficiales de la tarjeta del club, salidas BLANCAS (6088 yd).
    // Otras salidas: Azules 71.6/139 · Doradas 66.4/119 · Rojas 69.8/135.
    static let courseRating = 69.1
    static let slopeRating = 131

    /// Hoyo con el tee más cercano a la posición dada.
    static func nearestHoleByTee(lat: Double, lng: Double) -> Hole {
        holes.min { a, b in
            haversineMeters(lat, lng, a.teeLat, a.teeLng) <
            haversineMeters(lat, lng, b.teeLat, b.teeLng)
        }!
    }
}

/// Fórmula de Haversine. Devuelve metros.
func haversineMeters(_ lat1: Double, _ lng1: Double, _ lat2: Double, _ lng2: Double) -> Double {
    let r = 6371000.0
    let dLat = (lat2 - lat1) * .pi / 180
    let dLng = (lng2 - lng1) * .pi / 180
    let a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) *
            sin(dLng / 2) * sin(dLng / 2)
    let c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

func metersToYards(_ m: Double) -> Double { m * 1.0936133 }

/// Recomendación de palo según distancia en yardas al centro del green.
func recommendedClub(_ yards: Double) -> String {
    switch yards {
    case 240...: return "Driver"
    case 220...: return "Madera 3"
    case 200...: return "Madera 5 / Híbrido"
    case 185...: return "Hierro 4"
    case 170...: return "Hierro 5"
    case 160...: return "Hierro 6"
    case 150...: return "Hierro 7"
    case 140...: return "Hierro 8"
    case 130...: return "Hierro 9"
    case 110...: return "Pitching Wedge"
    case 95...:  return "Gap Wedge (52°)"
    case 80...:  return "Sand Wedge (56°)"
    case 40...:  return "Lob Wedge / Approach"
    default:     return "Chip / Putter"
    }
}

// ---------------------------------------------------------------- Mapa por hoyo

/// t: posición 0..1 del tee al green · side: -1 izquierda, +1 derecha · size: escala relativa
struct Bunker {
    let t: CGFloat
    let side: CGFloat
    var size: CGFloat = 1
}

struct Water {
    let t: CGFloat
    let side: CGFloat
    let w: CGFloat
    let h: CGFloat
}

/// dogleg: curvatura del fairway (-1 izquierda fuerte ... +1 derecha fuerte)
struct HoleFeatures {
    let dogleg: CGFloat
    let bunkers: [Bunker]
    var water: Water? = nil
    var trees: Int = 4
}

// Trazado por hoyo basado en imagen satelital del club (jul 2026).
// Mismo trazado que la app Android (ver comentarios en CourseData.kt).
let holeFeatures: [Int: HoleFeatures] = [
    1: HoleFeatures(dogleg: 0.10, bunkers: [Bunker(t: 0.92, side: 1, size: 1.15)],
                    water: Water(t: 0.84, side: -1, w: 0.9, h: 0.8), trees: 4),
    2: HoleFeatures(dogleg: 0, bunkers: [Bunker(t: 0.82, side: 1, size: 1.2), Bunker(t: 0.86, side: -1, size: 0.75)], trees: 5),
    3: HoleFeatures(dogleg: -0.15, bunkers: [Bunker(t: 0.92, side: -1, size: 1.0)],
                    water: Water(t: 0.70, side: -1, w: 1.4, h: 1.2), trees: 4),
    4: HoleFeatures(dogleg: 0.30, bunkers: [Bunker(t: 0.50, side: -1), Bunker(t: 0.90, side: 1, size: 0.9)], trees: 6),
    5: HoleFeatures(dogleg: -0.10, bunkers: [Bunker(t: 0.55, side: 1), Bunker(t: 0.90, side: -1, size: 1.0)], trees: 4),
    6: HoleFeatures(dogleg: 0.05, bunkers: [Bunker(t: 0.35, side: -1, size: 0.8), Bunker(t: 0.90, side: -1, size: 1.0),
                                            Bunker(t: 0.93, side: 1, size: 0.8)], trees: 6),
    7: HoleFeatures(dogleg: 0, bunkers: [Bunker(t: 0.82, side: -1), Bunker(t: 0.85, side: 1)], trees: 3),
    8: HoleFeatures(dogleg: 0.20, bunkers: [Bunker(t: 0.90, side: -1)], trees: 4),
    9: HoleFeatures(dogleg: -0.10, bunkers: [Bunker(t: 0.90, side: 1, size: 0.9)],
                    water: Water(t: 0.55, side: -1, w: 1.8, h: 1.4), trees: 5),
    10: HoleFeatures(dogleg: 0, bunkers: [Bunker(t: 0.85, side: 1)], trees: 3),
    11: HoleFeatures(dogleg: 0.05, bunkers: [Bunker(t: 0.55, side: 1), Bunker(t: 0.90, side: -1)], trees: 5),
    12: HoleFeatures(dogleg: 0.35, bunkers: [Bunker(t: 0.70, side: 1), Bunker(t: 0.90, side: -1, size: 0.8)],
                     water: Water(t: 0.40, side: -1, w: 1.5, h: 1.2), trees: 6),
    13: HoleFeatures(dogleg: -0.30, bunkers: [Bunker(t: 0.88, side: 1)],
                     water: Water(t: 0.50, side: 1, w: 1.1, h: 0.9), trees: 5),
    14: HoleFeatures(dogleg: 0, bunkers: [Bunker(t: 0.83, side: -1), Bunker(t: 0.86, side: 1, size: 0.9)], trees: 3),
    15: HoleFeatures(dogleg: 0.05, bunkers: [Bunker(t: 0.60, side: -1)],
                     water: Water(t: 0.88, side: 1, w: 1.5, h: 1.2), trees: 4),
    16: HoleFeatures(dogleg: 0.15, bunkers: [Bunker(t: 0.88, side: -1)], trees: 5),
    17: HoleFeatures(dogleg: -0.15, bunkers: [Bunker(t: 0.60, side: 1), Bunker(t: 0.90, side: -1, size: 0.9)], trees: 4),
    18: HoleFeatures(dogleg: 0.25, bunkers: [Bunker(t: 0.45, side: -1), Bunker(t: 0.90, side: -1)],
                     water: Water(t: 0.55, side: 1, w: 1.7, h: 1.3), trees: 6)
]

// ---------------------------------------------------------------- Palos por jugador

let clubNames = [
    "Driver", "3 Wood", "5 Wood", "Hybrid", "4 Iron", "5 Iron", "6 Iron",
    "7 Iron", "8 Iron", "9 Iron", "PW", "GW", "SW", "LW"
]
let defaultClubYards = [230, 215, 200, 190, 185, 175, 165, 155, 145, 135, 120, 105, 90, 75]

/// Índice en clubNames del palo sugerido (preselección al medir golpes).
func clubIndexForDistance(_ yards: Double, _ dist: [Int]) -> Int {
    if dist.count != clubNames.count { return clubNames.count - 1 }
    for i in dist.indices.reversed() where Double(dist[i]) >= yards {
        return i
    }
    return 0
}

/// El palo más corto cuyo alcance cubre la distancia.
func clubForDistance(_ yards: Double, _ dist: [Int]) -> String {
    if dist.count != clubNames.count { return recommendedClub(yards) }
    if yards < Double(dist.last!) * 0.55 { return "Chip / Putter" }
    for i in dist.indices.reversed() where Double(dist[i]) >= yards {
        return clubNames[i]
    }
    return clubNames[0]
}
