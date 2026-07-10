import SwiftUI

// ---- Paleta ilustración flat (referencia: aerial vector, desierto Chihuahua) ----
private let waste = Color(argb: 0xFFEBDFC6)        // terreno desértico
private let wasteDots = Color(argb: 0xFFD9C8A5)    // matorral / textura
private let wasteScrub = Color(argb: 0xFFC9B78E)   // arbustos secos
private let roughBand = Color(argb: 0xFF9EB47B)    // semi-rough alrededor del fairway
private let fairwayC = Color(argb: 0xFF74A257)
private let fairwayStripe = Color(argb: 0xFF7FAD61) // franja de podado diagonal
private let greenFringe = Color(argb: 0xFF93C06E)
private let greenTurf = Color(argb: 0xFFAFD489)
private let sandC = Color(argb: 0xFFF4EAD2)
private let sandShadow = Color(argb: 0xFFD8C69E)
private let waterDeep = Color(argb: 0xFF4C80B4)
private let waterBlue = Color(argb: 0xFF6FA9DA)
private let rippleC = Color(argb: 0xFFA9CBEA)
private let treeDark = Color(argb: 0xFF3F6C3C)
private let treeLight = Color(argb: 0xFF548549)
private let treeShadow = Color(argb: 0x2E2A4526)
private let trunkC = Color(argb: 0xFF6B4B2A)
private let flagRed = Color(argb: 0xFFE0584A)
private let poleC = Color(argb: 0xFFFFFDF6)
private let playerBlue = Color(argb: 0xFF3D8BFF)
private let lineDark = Color(argb: 0xFF4E5A42)

// ---- Utilidades de geometría ----

private func + (a: CGPoint, b: CGPoint) -> CGPoint { CGPoint(x: a.x + b.x, y: a.y + b.y) }
private func - (a: CGPoint, b: CGPoint) -> CGPoint { CGPoint(x: a.x - b.x, y: a.y - b.y) }
private func * (a: CGPoint, s: CGFloat) -> CGPoint { CGPoint(x: a.x * s, y: a.y * s) }
private extension CGPoint {
    var distance: CGFloat { sqrt(x * x + y * y) }
}

/// Random determinista con semilla (equivalente funcional del Random(seed) de
/// Kotlin: mismo hoyo -> mismo mapa en cada render).
private struct SeededRandom {
    private var state: UInt64
    init(_ seed: Int) {
        state = UInt64(bitPattern: Int64(seed)) &* 0x9E3779B97F4A7C15 &+ 0xDEADBEEF
        if state == 0 { state = 0x1234567 }
    }
    mutating func nextFloat() -> CGFloat {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return CGFloat(state % 100_000_000) / 100_000_000
    }
}

/// Ilustración custom por hoyo. Anclajes en fracciones de la imagen:
/// posición del tee y del centro del green. Con esos dos puntos se calibra la
/// transformación lat/lng -> pantalla para que el punto GPS, la línea a green
/// y el cursor caigan exactos sobre el arte.
private struct HoleArt {
    let resource: String
    let teeAnchor: CGPoint
    let greenAnchor: CGPoint
    let aspect: CGFloat
}

private let holeArtMap: [Int: HoleArt] = [
    1: HoleArt(resource: "hole_1", teeAnchor: CGPoint(x: 0.526, y: 0.884),
               greenAnchor: CGPoint(x: 0.420, y: 0.143), aspect: 1000.0 / 890.0)
]

/// Cache de las ilustraciones (webp del bundle; iOS lo decodifica nativo).
private var artImageCache: [String: UIImage] = [:]
private func artImage(_ name: String) -> UIImage? {
    if let img = artImageCache[name] { return img }
    guard let url = Bundle.main.url(forResource: name, withExtension: "webp"),
          let data = try? Data(contentsOf: url),
          let img = UIImage(data: data) else { return nil }
    artImageCache[name] = img
    return img
}

// ---- Vista ----

struct HoleMapCard: View {
    let hole: Hole
    let userLat: Double?
    let userLng: Double?
    let units: Units
    var flag: Int = -1

    // Cursor de medición: toca el mapa para medir a ese punto (layups).
    // Se reinicia al cambiar de hoyo. Tocar cerca del cursor lo quita.
    @State private var tapPoint: CGPoint?

    var body: some View {
        Group {
            if let art = holeArtMap[hole.number], let img = artImage(art.resource) {
                ZStack {
                    Image(uiImage: img)
                        .resizable()
                    Canvas { ctx, size in
                        drawArtOverlay(ctx, size, hole: hole, art: art,
                                       userLat: userLat, userLng: userLng,
                                       units: units, tapPoint: tapPoint)
                    }
                }
                .aspectRatio(art.aspect, contentMode: .fit)
                .background(waste)
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .onTapGesture(coordinateSpace: .local) { handleTap($0) }
            } else {
                Canvas { ctx, size in
                    drawHoleMap(ctx, size, hole: hole,
                                userLat: userLat, userLng: userLng,
                                units: units, tapPoint: tapPoint, flag: flag)
                }
                .frame(height: 280)
                .background(waste)
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .onTapGesture(coordinateSpace: .local) { handleTap($0) }
            }
        }
        .onChange(of: hole.number) { tapPoint = nil }
    }

    private func handleTap(_ tap: CGPoint) {
        if let current = tapPoint, (current - tap).distance < 44 {
            tapPoint = nil
        } else {
            tapPoint = tap
        }
    }
}

// ---- Helpers de dibujo compartidos ----

private func fmtDist(_ m: Double, _ units: Units) -> String {
    units == .yards ? "\(Int(metersToYards(m).rounded())) yd" : "\(Int(m.rounded())) m"
}

private func drawDistLabel(_ ctx: GraphicsContext, _ text: String, at: CGPoint, sizeFactor: CGFloat = 1) {
    var shadowed = ctx
    shadowed.addFilter(.shadow(color: Color(argb: 0xC82E3826), radius: 3, x: 0, y: 1))
    shadowed.draw(
        Text(text)
            .font(.system(size: 13 * sizeFactor, weight: .heavy))
            .foregroundColor(.white),
        at: at, anchor: .center
    )
}

private func strokeLine(_ ctx: GraphicsContext, from: CGPoint, to: CGPoint,
                        color: Color, width: CGFloat, dash: [CGFloat] = []) {
    var p = Path()
    p.move(to: from)
    p.addLine(to: to)
    ctx.stroke(p, with: .color(color),
               style: StrokeStyle(lineWidth: width, lineCap: .round, dash: dash))
}

private func fillCircle(_ ctx: GraphicsContext, center: CGPoint, radius: CGFloat, color: Color) {
    ctx.fill(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius,
                                    width: radius * 2, height: radius * 2)),
             with: .color(color))
}

private func fillOval(_ ctx: GraphicsContext, topLeft: CGPoint, size: CGSize, color: Color) {
    ctx.fill(Path(ellipseIn: CGRect(origin: topLeft, size: size)), with: .color(color))
}

/// Cursor de medición + líneas origen->cursor->green. Común al mapa
/// procedural y a la ilustración.
private func drawCursorAndLines(
    _ ctx: GraphicsContext, _ size: CGSize,
    hole: Hole, units: Units,
    user: CGPoint?, userLat: Double?, userLng: Double?,
    teeP: CGPoint, greenP: CGPoint,
    tapPoint: CGPoint?,
    toLatLng: (CGPoint) -> (Double, Double)
) {
    let w = size.width, h = size.height
    let pad: CGFloat = 18

    if let tap = tapPoint {
        let cursor = CGPoint(x: min(max(tap.x, pad), w - pad), y: min(max(tap.y, pad), h - pad))
        let (tapLat, tapLng) = toLatLng(cursor)
        let origin = user ?? teeP
        let originLat = userLat ?? hole.teeLat
        let originLng = userLng ?? hole.teeLng

        let d1 = haversineMeters(originLat, originLng, tapLat, tapLng)
        let d2 = haversineMeters(tapLat, tapLng, hole.greenLat, hole.greenLng)

        strokeLine(ctx, from: origin, to: cursor, color: playerBlue, width: 4)
        strokeLine(ctx, from: cursor, to: greenP, color: lineDark.opacity(0.85),
                   width: 3.5, dash: [16, 12])
        fillCircle(ctx, center: cursor + CGPoint(x: 2, y: 3), radius: 15, color: Color(argb: 0x59000000))
        ctx.stroke(Path(ellipseIn: CGRect(x: cursor.x - 13, y: cursor.y - 13, width: 26, height: 26)),
                   with: .color(poleC), style: StrokeStyle(lineWidth: 4))
        strokeLine(ctx, from: cursor + CGPoint(x: -19, y: 0), to: cursor + CGPoint(x: -8, y: 0), color: poleC, width: 3)
        strokeLine(ctx, from: cursor + CGPoint(x: 8, y: 0), to: cursor + CGPoint(x: 19, y: 0), color: poleC, width: 3)
        strokeLine(ctx, from: cursor + CGPoint(x: 0, y: -19), to: cursor + CGPoint(x: 0, y: -8), color: poleC, width: 3)
        strokeLine(ctx, from: cursor + CGPoint(x: 0, y: 8), to: cursor + CGPoint(x: 0, y: 19), color: poleC, width: 3)

        let mid1 = CGPoint(x: (origin.x + cursor.x) / 2 + 30, y: (origin.y + cursor.y) / 2)
        let mid2 = CGPoint(x: (cursor.x + greenP.x) / 2 + 30, y: (cursor.y + greenP.y) / 2)
        drawDistLabel(ctx, fmtDist(d1, units), at: mid1)
        drawDistLabel(ctx, fmtDist(d2, units), at: mid2)
        drawDistLabel(ctx, "toca el marcador para quitarlo", at: CGPoint(x: w / 2, y: h - 10), sizeFactor: 0.72)
    } else if let user, let userLat, let userLng {
        strokeLine(ctx, from: user, to: greenP, color: lineDark.opacity(0.85),
                   width: 3.5, dash: [16, 12])
        let distM = haversineMeters(userLat, userLng, hole.greenLat, hole.greenLng)
        let mid = CGPoint(x: (user.x + greenP.x) / 2 + 28, y: (user.y + greenP.y) / 2)
        drawDistLabel(ctx, fmtDist(distM, units), at: mid)
        drawDistLabel(ctx, "toca el mapa para medir un layup", at: CGPoint(x: w / 2, y: h - 10), sizeFactor: 0.72)
    } else {
        drawDistLabel(ctx, "toca el mapa para medir desde el tee", at: CGPoint(x: w / 2, y: h - 10), sizeFactor: 0.72)
    }
}

/// Punto del jugador (siempre encima de las líneas).
private func drawUserDot(_ ctx: GraphicsContext, user: CGPoint?, offMap: Bool) {
    guard let user else { return }
    fillCircle(ctx, center: user, radius: 20, color: playerBlue.opacity(0.28))
    fillCircle(ctx, center: user, radius: 10, color: poleC)
    fillCircle(ctx, center: user, radius: 7, color: playerBlue)
    if offMap {
        drawDistLabel(ctx, "(fuera del mapa)", at: user + CGPoint(x: 0, y: 34), sizeFactor: 0.8)
    }
}

// ---- Overlay GPS sobre la ilustración ----
// Transformación de similitud (rotación + escala + traslación) que lleva
// tee->teeAnchor y green->greenAnchor.

private func drawArtOverlay(
    _ ctx: GraphicsContext, _ size: CGSize,
    hole: Hole, art: HoleArt,
    userLat: Double?, userLng: Double?,
    units: Units, tapPoint: CGPoint?
) {
    let w = size.width, h = size.height

    let lat0 = (hole.teeLat + hole.greenLat) / 2
    let lng0 = (hole.teeLng + hole.greenLng) / 2
    func local(_ lat: Double, _ lng: Double) -> CGPoint {
        CGPoint(x: (lng - lng0) * cos(lat0 * .pi / 180) * 111320.0,
                y: -(lat - lat0) * 110540.0)
    }
    let teeL = local(hole.teeLat, hole.teeLng)
    let greenL = local(hole.greenLat, hole.greenLng)
    let teeP = CGPoint(x: art.teeAnchor.x * w, y: art.teeAnchor.y * h)
    let greenP = CGPoint(x: art.greenAnchor.x * w, y: art.greenAnchor.y * h)

    let src = greenL - teeL
    let dst = greenP - teeP
    let ang = atan2(dst.y, dst.x) - atan2(src.y, src.x)
    let sc = dst.distance / max(src.distance, 0.001)
    let ca = cos(ang), sa = sin(ang)
    func toScreen(_ p: CGPoint) -> CGPoint {
        let q = p - teeL
        return CGPoint(x: teeP.x + (q.x * ca - q.y * sa) * sc,
                       y: teeP.y + (q.x * sa + q.y * ca) * sc)
    }
    func toLatLng(_ s: CGPoint) -> (Double, Double) {
        let q = (s - teeP) * (1 / sc)
        let px = q.x * ca + q.y * sa
        let py = -q.x * sa + q.y * ca
        let lx = px + teeL.x
        let ly = py + teeL.y
        let lat = lat0 - ly / 110540.0
        let lng = lng0 + lx / (cos(lat0 * .pi / 180) * 111320.0)
        return (lat, lng)
    }

    let pad: CGFloat = 18
    var user: CGPoint?
    var offMap = false
    if let userLat, let userLng {
        let raw = toScreen(local(userLat, userLng))
        let clamped = CGPoint(x: min(max(raw.x, pad), w - pad), y: min(max(raw.y, pad), h - pad))
        user = clamped
        offMap = raw != clamped
    }

    drawCursorAndLines(ctx, size, hole: hole, units: units,
                       user: user, userLat: userLat, userLng: userLng,
                       teeP: teeP, greenP: greenP, tapPoint: tapPoint, toLatLng: toLatLng)
    drawUserDot(ctx, user: user, offMap: offMap)
}

// ---- Mapa procedural (hoyos sin ilustración) ----

private func drawHoleMap(
    _ ctx: GraphicsContext, _ size: CGSize,
    hole: Hole,
    userLat: Double?, userLng: Double?,
    units: Units, tapPoint: CGPoint?, flag: Int
) {
    let flagColor: Color
    switch flag {
    case 0: flagColor = Color(argb: 0xFFE85D4A)   // frente (rojo)
    case 1: flagColor = Color(argb: 0xFFF4F1E8)   // medio (blanco)
    case 2: flagColor = Color(argb: 0xFF5AB0FF)   // fondo (azul)
    default: flagColor = flagRed
    }
    let w = size.width
    let h = size.height
    let f = holeFeatures[hole.number] ?? HoleFeatures(dogleg: 0, bunkers: [])

    // ---- Proyección lat/lng -> metros locales -> rotación (green arriba) ----
    let lat0 = (hole.teeLat + hole.greenLat) / 2
    let lng0 = (hole.teeLng + hole.greenLng) / 2
    func local(_ lat: Double, _ lng: Double) -> CGPoint {
        CGPoint(x: (lng - lng0) * cos(lat0 * .pi / 180) * 111320.0,
                y: -(lat - lat0) * 110540.0)
    }
    let teeL = local(hole.teeLat, hole.teeLng)
    let greenL = local(hole.greenLat, hole.greenLng)
    let angle = atan2(greenL.y - teeL.y, greenL.x - teeL.x)
    let rot = -CGFloat.pi / 2 - angle
    func rotate(_ p: CGPoint) -> CGPoint {
        let c = cos(rot), s = sin(rot)
        return CGPoint(x: p.x * c - p.y * s, y: p.x * s + p.y * c)
    }
    let lengthM = rotate(teeL).y - rotate(greenL).y
    let greenY = h * 0.19
    let teeY = h * 0.87
    let scale = (teeY - greenY) / lengthM
    let cx = w / 2
    let cy = (teeY + greenY) / 2
    func toScreen(_ p: CGPoint) -> CGPoint {
        let r = rotate(p)
        return CGPoint(x: cx + r.x * scale, y: cy + r.y * scale)
    }
    /// Inversa de toScreen: píxel en pantalla -> lat/lng reales.
    func toLatLng(_ s: CGPoint) -> (Double, Double) {
        let rx = (s.x - cx) / scale
        let ry = (s.y - cy) / scale
        let c = cos(-rot), sn = sin(-rot)
        let px = rx * c - ry * sn
        let py = rx * sn + ry * c
        let lat = lat0 - py / 110540.0
        let lng = lng0 + px / (cos(lat0 * .pi / 180) * 111320.0)
        return (lat, lng)
    }

    let teeP = CGPoint(x: cx, y: teeY)
    let greenP = CGPoint(x: cx, y: greenY)
    let gr = min(w, h) * 0.125

    // ---- Fairway curvo (dogleg) como Bézier cuadrática ----
    let p0 = teeP
    let p2 = CGPoint(x: greenP.x, y: greenP.y + gr * 1.4)
    let p1 = CGPoint(x: cx + f.dogleg * w * 0.38, y: (teeY + greenY) / 2)
    func bez(_ t: CGFloat) -> CGPoint {
        let u = 1 - t
        return CGPoint(x: u * u * p0.x + 2 * u * t * p1.x + t * t * p2.x,
                       y: u * u * p0.y + 2 * u * t * p1.y + t * t * p2.y)
    }
    func perp(_ t: CGFloat) -> CGPoint {
        let tx = 2 * (1 - t) * (p1.x - p0.x) + 2 * t * (p2.x - p1.x)
        let ty = 2 * (1 - t) * (p1.y - p0.y) + 2 * t * (p2.y - p1.y)
        let len = max(sqrt(tx * tx + ty * ty), 0.001)
        return CGPoint(x: -ty / len, y: tx / len) // +1 = derecha del jugador
    }

    // ---- Terreno desértico: textura de puntos + matorral + número de fondo ----
    var rnd = SeededRandom(hole.number * 97)
    for _ in 0..<46 {
        let p = CGPoint(x: rnd.nextFloat() * w, y: rnd.nextFloat() * h)
        fillCircle(ctx, center: p, radius: 1.5 + rnd.nextFloat() * 2, color: wasteDots)
    }
    for _ in 0..<14 {
        // arbustos secos: racimos de 3 puntitos
        let p = CGPoint(x: rnd.nextFloat() * w, y: rnd.nextFloat() * h)
        let r = 2.2 + rnd.nextFloat() * 1.6
        fillCircle(ctx, center: p, radius: r, color: wasteScrub)
        fillCircle(ctx, center: p + CGPoint(x: r * 1.3, y: r * 0.4), radius: r * 0.8, color: wasteScrub)
        fillCircle(ctx, center: p + CGPoint(x: -r * 0.9, y: r * 0.9), radius: r * 0.7, color: wasteScrub)
    }
    ctx.draw(
        Text("\(hole.number)")
            .font(.system(size: h * 0.42, weight: .heavy))
            .foregroundColor(Color(argb: 0x1E796A48)),
        at: CGPoint(x: w - 14, y: h * 0.28), anchor: .trailing
    )

    // ---- Fairway: blob orgánico de ancho variable + semi-rough alrededor ----
    let fairwayW = hole.par == 3 ? w * 0.20 : w * 0.30
    let samples = 30
    let seed = CGFloat(hole.number) * 1.7
    func widthAt(_ t: CGFloat) -> CGFloat {
        // más angosto en salida, panza en zona de caída, cintura antes del green
        let base = 0.62 + 0.38 * sin(CGFloat.pi * (0.15 + 0.85 * t))
        let wobble = 1 + 0.13 * sin(t * 9.4 + seed) + 0.07 * sin(t * 17.3 + seed * 2.1)
        return fairwayW * 0.5 * base * wobble
    }
    func blobPath(_ scaleW: CGFloat) -> Path {
        var left: [CGPoint] = []
        var right: [CGPoint] = []
        for i in 0...samples {
            let t = CGFloat(i) / CGFloat(samples)
            let c = bez(t), n = perp(t), ww = widthAt(t) * scaleW
            left.append(c + n * -ww)
            right.append(c + n * ww)
        }
        var path = Path()
        path.move(to: left[0])
        for q in left { path.addLine(to: q) }
        for q in right.reversed() { path.addLine(to: q) }
        path.closeSubpath()
        return path
    }
    let roughPath = blobPath(1.55)
    let blob = blobPath(1)
    ctx.fill(roughPath, with: .color(roughBand))
    ctx.fill(blob, with: .color(fairwayC))
    // caps redondeados en tee y entrada al green
    fillCircle(ctx, center: p0, radius: widthAt(0) * 1.55, color: roughBand)
    fillCircle(ctx, center: p0, radius: widthAt(0), color: fairwayC)
    fillCircle(ctx, center: p2, radius: widthAt(1) * 1.55, color: roughBand)
    fillCircle(ctx, center: p2, radius: widthAt(1), color: fairwayC)
    // franjas de podado diagonales (recortadas al fairway)
    var clipped = ctx
    clipped.clip(to: blob)
    let stripeW = w * 0.075
    var x = -h * 0.7
    var k = 0
    while x < w + h * 0.7 {
        if k % 2 == 0 {
            strokeLine(clipped, from: CGPoint(x: x, y: h + 10),
                       to: CGPoint(x: x + h * 0.7, y: -10),
                       color: fairwayStripe, width: stripeW)
        }
        x += stripeW
        k += 1
    }

    // ---- Agua (si el hoyo tiene) ----
    if let wa = f.water {
        let c = bez(wa.t) + perp(wa.t) * (wa.side * (fairwayW * 0.5 + w * 0.10 * wa.w))
        let pw = w * 0.15 * wa.w
        let ph = w * 0.105 * wa.h
        fillOval(ctx, topLeft: CGPoint(x: c.x - pw, y: c.y - ph + 5), size: CGSize(width: pw * 2, height: ph * 2), color: waterDeep)
        fillOval(ctx, topLeft: CGPoint(x: c.x - pw, y: c.y - ph), size: CGSize(width: pw * 2, height: ph * 2), color: waterBlue)
        for i in 0...1 {
            let fi = CGFloat(i)
            let rect = CGRect(x: c.x - pw * (0.45 + fi * 0.25), y: c.y - ph * (0.35 + fi * 0.25),
                              width: pw * (0.9 + fi * 0.5), height: ph * (0.7 + fi * 0.5))
            var arc = Path()
            arc.addArc(center: CGPoint(x: rect.midX, y: rect.midY),
                       radius: rect.width / 2,
                       startAngle: .degrees(200), endAngle: .degrees(320), clockwise: false)
            // achatar el arco a la altura del óvalo
            let squash = CGAffineTransform(translationX: rect.midX, y: rect.midY)
                .scaledBy(x: 1, y: rect.height / rect.width)
                .translatedBy(x: -rect.midX, y: -rect.midY)
            clippedStroke(ctx, arc.applying(squash), color: rippleC, width: 2.5)
        }
    }

    // ---- Bunkers: blobs de arena suaves con sombra ----
    for b in f.bunkers {
        let c = bez(b.t) + perp(b.t) * (b.side * (fairwayW * 0.5 + w * 0.045 * b.size))
        let br = w * 0.052 * b.size
        fillOval(ctx, topLeft: CGPoint(x: c.x - br, y: c.y - br * 0.55 + 4), size: CGSize(width: br * 2, height: br * 1.1), color: sandShadow)
        fillOval(ctx, topLeft: CGPoint(x: c.x - br, y: c.y - br * 0.55), size: CGSize(width: br * 2, height: br * 1.1), color: sandC)
        fillOval(ctx, topLeft: CGPoint(x: c.x - br * 0.5, y: c.y - br * 0.85), size: CGSize(width: br * 1.3, height: br * 0.9), color: sandC)
    }

    // ---- Árboles: flat con sombra desplazada ----
    var treeRnd = SeededRandom(hole.number * 31 + 7)
    for i in 0..<f.trees {
        let t = 0.12 + treeRnd.nextFloat() * 0.72
        let side: CGFloat = (i + hole.number) % 2 == 0 ? 1 : -1
        let c = bez(t) + perp(t) * (side * (fairwayW * 0.5 + w * (0.11 + treeRnd.nextFloat() * 0.08)))
        let r = w * (0.040 + treeRnd.nextFloat() * 0.017)
        if c.x - r > 4 && c.x + r < w - 4 && c.y - r > 4 && c.y + r < h - 4 {
            fillOval(ctx, topLeft: CGPoint(x: c.x - r * 0.85 + r * 0.5, y: c.y - r * 0.35 + r * 0.55),
                     size: CGSize(width: r * 2.1, height: r * 1.1), color: treeShadow)
            strokeLine(ctx, from: CGPoint(x: c.x, y: c.y + r * 0.4),
                       to: CGPoint(x: c.x, y: c.y + r * 1.1), color: trunkC, width: r * 0.32)
            fillCircle(ctx, center: c, radius: r, color: treeDark)
            fillCircle(ctx, center: CGPoint(x: c.x + r * 0.55, y: c.y + r * 0.15), radius: r * 0.68, color: treeDark)
            fillCircle(ctx, center: CGPoint(x: c.x - r * 0.3, y: c.y - r * 0.3), radius: r * 0.52, color: treeLight)
        }
    }

    // ---- Green: blob orgánico + fringe + bandera ----
    let greenSeed = CGFloat(hole.number) * 2.3
    func greenBlob(_ radius: CGFloat, _ offset: CGPoint = .zero) -> Path {
        let pts = 26
        var path = Path()
        for i in 0...pts {
            let a = CGFloat(i) / CGFloat(pts) * 2 * .pi
            let rr = radius * (1 + 0.11 * sin(3 * a + greenSeed) + 0.05 * sin(5 * a - greenSeed))
            let p = CGPoint(x: greenP.x + offset.x + rr * cos(a), y: greenP.y + offset.y + rr * sin(a))
            if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        path.closeSubpath()
        return path
    }
    ctx.fill(greenBlob(gr * 1.22, CGPoint(x: 3, y: 5)), with: .color(Color(argb: 0x26203A1E)))
    ctx.fill(greenBlob(gr * 1.22), with: .color(greenFringe))
    ctx.fill(greenBlob(gr), with: .color(greenTurf))
    let poleTop = CGPoint(x: greenP.x, y: greenP.y - gr * 1.55)
    strokeLine(ctx, from: greenP, to: poleTop, color: poleC, width: 4)
    var flagPath = Path()
    flagPath.move(to: poleTop)
    flagPath.addLine(to: CGPoint(x: poleTop.x + gr * 0.75, y: poleTop.y + gr * 0.28))
    flagPath.addLine(to: CGPoint(x: poleTop.x, y: poleTop.y + gr * 0.56))
    flagPath.closeSubpath()
    ctx.fill(flagPath, with: .color(flagColor))
    fillCircle(ctx, center: CGPoint(x: greenP.x, y: greenP.y + 2), radius: 5, color: Color(argb: 0x33000000))
    fillCircle(ctx, center: greenP, radius: 3.5, color: poleC)

    // ---- Salida: caja de tee con dos marcas ----
    fillOval(ctx, topLeft: CGPoint(x: teeP.x - 30, y: teeP.y - 12), size: CGSize(width: 60, height: 24), color: fairwayC)
    fillCircle(ctx, center: CGPoint(x: teeP.x - 14, y: teeP.y), radius: 6.5, color: poleC)
    fillCircle(ctx, center: CGPoint(x: teeP.x + 14, y: teeP.y), radius: 6.5, color: poleC)
    fillCircle(ctx, center: CGPoint(x: teeP.x - 14, y: teeP.y), radius: 3.2, color: flagRed)
    fillCircle(ctx, center: CGPoint(x: teeP.x + 14, y: teeP.y), radius: 3.2, color: flagRed)

    // ---- Posición del jugador en pantalla (o tee si no hay GPS) ----
    let pad: CGFloat = 18
    var user: CGPoint?
    var offMap = false
    if let userLat, let userLng {
        let raw = toScreen(local(userLat, userLng))
        let clamped = CGPoint(x: min(max(raw.x, pad), w - pad), y: min(max(raw.y, pad), h - pad))
        user = clamped
        offMap = raw != clamped
    }

    drawCursorAndLines(ctx, size, hole: hole, units: units,
                       user: user, userLat: userLat, userLng: userLng,
                       teeP: teeP, greenP: greenP, tapPoint: tapPoint, toLatLng: toLatLng)
    drawUserDot(ctx, user: user, offMap: offMap)
}

private func clippedStroke(_ ctx: GraphicsContext, _ path: Path, color: Color, width: CGFloat) {
    ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round))
}
