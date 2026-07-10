import SwiftUI

// Paleta pensada para fundirse sobre el negro del reloj (más viva que la del
// teléfono). Espejo de WearHoleMap.kt.
private let rough = Color(argb: 0xFF26402B)
private let fairway = Color(argb: 0xFF4F9350)
private let fairwayStripe = Color(argb: 0xFF579A57)
private let greenFringe = Color(argb: 0xFF6FB85E)
private let greenTurf = Color(argb: 0xFF9AD77A)
private let sand = Color(argb: 0xFFEAD9A6)
private let sandShadow = Color(argb: 0xFFCBB77E)
private let waterDeep = Color(argb: 0xFF3C79AE)
private let waterBlue = Color(argb: 0xFF4F92C9)
private let pole = Color.white
private let playerBlue = Color(argb: 0xFF3D8BFF)
private let lineLt = Color(argb: 0xFFE6F2D6)

private func + (a: CGPoint, b: CGPoint) -> CGPoint { CGPoint(x: a.x + b.x, y: a.y + b.y) }
private func * (a: CGPoint, s: CGFloat) -> CGPoint { CGPoint(x: a.x * s, y: a.y * s) }

/// Dibuja el hoyo (green arriba) ocupando la mitad derecha del lienzo. El borde
/// izquierdo se difumina a transparente con un degradado para fundirse con el
/// negro del UI. Pinta fairway, green, pin del día, tee, bunkers, agua y el
/// punto GPS del jugador con línea al green.
struct WatchHoleMap: View {
    let hole: Hole
    let userLat: Double?
    let userLng: Double?
    var flag: Int = -1

    var body: some View {
        Canvas { ctx, size in
            drawMiniHole(ctx, size)
        }
        .ignoresSafeArea()
    }

    private func drawMiniHole(_ ctx: GraphicsContext, _ size: CGSize) {
        let w = size.width
        let h = size.height
        let feat = holeFeatures[hole.number] ?? HoleFeatures(dogleg: 0, bunkers: [])

        // Base de terreno: transparente a la izquierda -> Rough hacia la derecha.
        ctx.fill(
            Path(CGRect(origin: .zero, size: size)),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .clear, location: 0),
                    .init(color: .clear, location: 0.40),
                    .init(color: rough, location: 0.60),
                    .init(color: rough, location: 1)
                ]),
                startPoint: CGPoint(x: 0, y: h / 2),
                endPoint: CGPoint(x: w, y: h / 2)
            )
        )

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
        let greenY = h * 0.17
        let teeY = h * 0.90
        let scale = (teeY - greenY) / lengthM
        let cx = w * 0.70          // hoyo centrado en la mitad derecha
        let cy = (teeY + greenY) / 2
        func toScreen(_ p: CGPoint) -> CGPoint {
            let r = rotate(p)
            return CGPoint(x: cx + r.x * scale, y: cy + r.y * scale)
        }
        let teeP = CGPoint(x: cx, y: teeY)
        let greenP = CGPoint(x: cx, y: greenY)
        let gr = min(w, h) * 0.13

        func fillCircle(_ center: CGPoint, _ radius: CGFloat, _ color: Color) {
            ctx.fill(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius,
                                            width: radius * 2, height: radius * 2)),
                     with: .color(color))
        }
        func fillOval(_ topLeft: CGPoint, _ ovalSize: CGSize, _ color: Color) {
            ctx.fill(Path(ellipseIn: CGRect(origin: topLeft, size: ovalSize)), with: .color(color))
        }

        // ---- Fairway curvo (dogleg) como Bézier cuadrática ----
        let p0 = teeP
        let p2 = CGPoint(x: greenP.x, y: greenP.y + gr * 1.4)
        let p1 = CGPoint(x: cx + feat.dogleg * w * 0.30, y: (teeY + greenY) / 2)
        func bez(_ t: CGFloat) -> CGPoint {
            let u = 1 - t
            return CGPoint(x: u * u * p0.x + 2 * u * t * p1.x + t * t * p2.x,
                           y: u * u * p0.y + 2 * u * t * p1.y + t * t * p2.y)
        }
        func perp(_ t: CGFloat) -> CGPoint {
            let tx = 2 * (1 - t) * (p1.x - p0.x) + 2 * t * (p2.x - p1.x)
            let ty = 2 * (1 - t) * (p1.y - p0.y) + 2 * t * (p2.y - p1.y)
            let len = max(sqrt(tx * tx + ty * ty), 0.001)
            return CGPoint(x: -ty / len, y: tx / len)
        }
        let fairwayW = hole.par == 3 ? w * 0.24 : w * 0.32
        let samples = 26
        let seed = CGFloat(hole.number) * 1.7
        func widthAt(_ t: CGFloat) -> CGFloat {
            let base = 0.62 + 0.38 * sin(CGFloat.pi * (0.15 + 0.85 * t))
            let wobble = 1 + 0.12 * sin(t * 9.4 + seed)
            return fairwayW * 0.5 * base * wobble
        }
        var left: [CGPoint] = []
        var right: [CGPoint] = []
        for i in 0...samples {
            let t = CGFloat(i) / CGFloat(samples)
            let c = bez(t), n = perp(t), ww = widthAt(t)
            left.append(c + n * -ww)
            right.append(c + n * ww)
        }
        var blob = Path()
        blob.move(to: left[0])
        for q in left { blob.addLine(to: q) }
        for q in right.reversed() { blob.addLine(to: q) }
        blob.closeSubpath()

        ctx.fill(blob, with: .color(fairway))
        fillCircle(p0, widthAt(0), fairway)
        fillCircle(p2, widthAt(1), fairway)
        // franjas de podado diagonales (recortadas al fairway)
        var clipped = ctx
        clipped.clip(to: blob)
        let stripeW = w * 0.085
        var x = -h * 0.7
        var k = 0
        while x < w + h * 0.7 {
            if k % 2 == 0 {
                var line = Path()
                line.move(to: CGPoint(x: x, y: h + 10))
                line.addLine(to: CGPoint(x: x + h * 0.7, y: -10))
                clipped.stroke(line, with: .color(fairwayStripe),
                               style: StrokeStyle(lineWidth: stripeW))
            }
            x += stripeW
            k += 1
        }

        // ---- Agua ----
        if let wa = feat.water {
            let c = bez(wa.t) + perp(wa.t) * (wa.side * (fairwayW * 0.5 + w * 0.09 * wa.w))
            let pw = w * 0.13 * wa.w
            let ph = w * 0.095 * wa.h
            fillOval(CGPoint(x: c.x - pw, y: c.y - ph + 4), CGSize(width: pw * 2, height: ph * 2), waterDeep)
            fillOval(CGPoint(x: c.x - pw, y: c.y - ph), CGSize(width: pw * 2, height: ph * 2), waterBlue)
        }

        // ---- Bunkers ----
        for b in feat.bunkers {
            let c = bez(b.t) + perp(b.t) * (b.side * (fairwayW * 0.5 + w * 0.05 * b.size))
            let br = w * 0.055 * b.size
            fillOval(CGPoint(x: c.x - br, y: c.y - br * 0.55 + 3), CGSize(width: br * 2, height: br * 1.1), sandShadow)
            fillOval(CGPoint(x: c.x - br, y: c.y - br * 0.55), CGSize(width: br * 2, height: br * 1.1), sand)
        }

        // ---- Green + pin ----
        let greenSeed = CGFloat(hole.number) * 2.3
        func greenBlob(_ radius: CGFloat) -> Path {
            let pts = 24
            var path = Path()
            for i in 0...pts {
                let a = CGFloat(i) / CGFloat(pts) * 2 * .pi
                let rr = radius * (1 + 0.10 * sin(3 * a + greenSeed))
                let p = CGPoint(x: greenP.x + rr * cos(a), y: greenP.y + rr * sin(a))
                if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
            }
            path.closeSubpath()
            return path
        }
        ctx.fill(greenBlob(gr * 1.22), with: .color(greenFringe))
        ctx.fill(greenBlob(gr), with: .color(greenTurf))
        // Punto de pin discreto (sin bandera), con el color del pin del día.
        let pinColor: Color
        switch flag {
        case 0: pinColor = Color(argb: 0xFFE85D4A)   // frente
        case 1: pinColor = .white                    // medio
        case 2: pinColor = Color(argb: 0xFF5AB0FF)   // fondo
        default: pinColor = pole
        }
        // Desplaza el punto dentro del green según el pin (frente = abajo).
        let pinP: CGPoint
        switch flag {
        case 0: pinP = CGPoint(x: greenP.x, y: greenP.y + gr * 0.45)
        case 2: pinP = CGPoint(x: greenP.x, y: greenP.y - gr * 0.45)
        default: pinP = greenP
        }
        fillCircle(pinP + CGPoint(x: 1, y: 1.5), 4.5, Color(argb: 0x66000000))
        fillCircle(pinP, 3.5, pinColor)

        // ---- Tee ----
        fillOval(CGPoint(x: teeP.x - 22, y: teeP.y - 8), CGSize(width: 44, height: 16), fairway)

        // ---- Jugador (GPS) + línea punteada al green ----
        if let userLat, let userLng {
            let pad: CGFloat = 10
            let raw = toScreen(local(userLat, userLng))
            let user = CGPoint(x: min(max(raw.x, pad), w - pad), y: min(max(raw.y, pad), h - pad))
            var line = Path()
            line.move(to: user)
            line.addLine(to: greenP)
            ctx.stroke(line, with: .color(lineLt.opacity(0.75)),
                       style: StrokeStyle(lineWidth: 3, lineCap: .round, dash: [12, 9]))
            fillCircle(user, 15, playerBlue.opacity(0.28))
            fillCircle(user, 8, pole)
            fillCircle(user, 5.5, playerBlue)
        }
    }
}
