import UIKit

/// Dibuja la tarjeta de la ronda actual en un PNG para compartirla
/// (WhatsApp, correo, etc.). Todo con Core Graphics: mismo layout y paleta
/// que la versión Android (ScorecardImage.kt) para que la imagen sea idéntica
/// en ambas plataformas.
enum ScorecardImage {

    // Paleta de la imagen (independiente del tema de la app, para que se vea
    // igual y legible en WhatsApp con fondo claro).
    private static let green = UIColor(red: 0x1B / 255, green: 0x5E / 255, blue: 0x3F / 255, alpha: 1)
    private static let greenSoft = UIColor(red: 0xE6 / 255, green: 0xF0 / 255, blue: 0xEA / 255, alpha: 1)
    private static let headerSub = UIColor(red: 0xCD / 255, green: 0xE4 / 255, blue: 0xD6 / 255, alpha: 1)
    private static let ink = UIColor(red: 0x1A / 255, green: 0x1A / 255, blue: 0x1A / 255, alpha: 1)
    private static let grid = UIColor(red: 0xD9 / 255, green: 0xD9 / 255, blue: 0xD9 / 255, alpha: 1)
    private static let muted = UIColor(red: 0x6B / 255, green: 0x6B / 255, blue: 0x6B / 255, alpha: 1)
    private static let birdie = UIColor(red: 0x2E / 255, green: 0x7D / 255, blue: 0xD1 / 255, alpha: 1)
    private static let eagle = UIColor(red: 0xE0 / 255, green: 0x7A / 255, blue: 0x1F / 255, alpha: 1)
    private static let bogey = UIColor(red: 0xC0 / 255, green: 0x39 / 255, blue: 0x2B / 255, alpha: 1)

    private static let W: CGFloat = 1080
    private static let pad: CGFloat = 36
    private static let labelW: CGFloat = 200
    private static let rowH: CGFloat = 62

    private static func scoreColor(_ diff: Int) -> UIColor {
        switch diff {
        case ...(-2): return eagle
        case -1: return birdie
        case 0: return ink
        default: return bogey
        }
    }

    private static func attrs(_ size: CGFloat, _ color: UIColor, bold: Bool) -> [NSAttributedString.Key: Any] {
        [.font: UIFont.systemFont(ofSize: size, weight: bold ? .bold : .regular),
         .foregroundColor: color]
    }

    private static func drawText(_ text: String, x: CGFloat, y: CGFloat,
                                 size: CGFloat, color: UIColor, bold: Bool, center: Bool = false) {
        let a = attrs(size, color, bold: bold)
        let s = NSAttributedString(string: text, attributes: a)
        let w = s.size().width
        // y es la baseline (como en el Canvas de Android): compensa el ascent.
        let font = a[.font] as! UIFont
        let origin = CGPoint(x: center ? x - w / 2 : x, y: y - font.ascender)
        s.draw(at: origin)
    }

    static func render(model: GolfModel) -> UIImage? {
        let players = model.players
        if players.isEmpty { return nil }

        let n = CGFloat(players.count)
        let titleH: CGFloat = 168
        let blockH = rowH * (2 + n)          // fila Hoyo + Par + jugadores
        let gap: CGFloat = 28
        let footHeadH: CGFloat = 54
        let footH = footHeadH + rowH * n
        let height = titleH + gap + blockH + gap + blockH + gap + footH + pad

        let fmt = UIGraphicsImageRendererFormat()
        fmt.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: W, height: height), format: fmt)
        return renderer.image { ctx in
            let c = ctx.cgContext
            c.setFillColor(UIColor.white.cgColor)
            c.fill(CGRect(x: 0, y: 0, width: W, height: height))

            // ---- Encabezado ----
            c.setFillColor(green.cgColor)
            c.fill(CGRect(x: 0, y: 0, width: W, height: titleH))
            drawText("⛳ SF Golf GPS", x: pad, y: 74, size: 52, color: .white, bold: true)
            drawText("\(CourseData.clubName) · Par \(CourseData.totalPar)", x: pad, y: 116, size: 30, color: .white, bold: false)
            let df = DateFormatter()
            df.locale = Locale(identifier: "es_MX")
            df.dateFormat = "EEEE d 'de' MMMM, yyyy · h:mm a"
            let fecha = df.string(from: Date())
            drawText(fecha.prefix(1).uppercased() + fecha.dropFirst(), x: pad, y: 150, size: 26, color: headerSub, bold: false)

            var y = titleH + gap
            y = drawBlock(c, players, range: 0..<9, sumLabel: "OUT", totLabel: nil, top: y)
            y += gap
            y = drawBlock(c, players, range: 9..<18, sumLabel: "IN", totLabel: "TOT", top: y)
            y += gap

            // ---- Resumen por jugador ----
            drawText("Resumen", x: pad, y: y + 36, size: 34, color: green, bold: true)
            y += footHeadH
            for p in players {
                drawSummary(p, top: y)
                y += rowH
            }
        }
    }

    /// Un bloque de 9 hoyos (ida o vuelta) con su fila Hoyo, Par y cada jugador.
    private static func drawBlock(_ c: CGContext, _ players: [Player], range: Range<Int>,
                                  sumLabel: String, totLabel: String?, top: CGFloat) -> CGFloat {
        let extraCols: CGFloat = totLabel != nil ? 2 : 1  // OUT, o IN+TOTAL
        let cols = 9 + extraCols
        let cellW = (W - 2 * pad - labelW) / cols
        let x0 = pad

        func colX(_ i: Int) -> CGFloat { x0 + labelW + CGFloat(i) * cellW }

        // Fila de hoyos (fondo verde)
        var y = top
        c.setFillColor(green.cgColor)
        c.fill(CGRect(x: x0, y: y, width: W - 2 * pad, height: rowH))
        drawText("Hoyo", x: x0 + 14, y: y + 40, size: 30, color: .white, bold: true)
        for (i, h) in range.enumerated() {
            drawText("\(h + 1)", x: colX(i) + cellW / 2, y: y + 40, size: 30, color: .white, bold: true, center: true)
        }
        drawText(sumLabel, x: colX(9) + cellW / 2, y: y + 40, size: 28, color: .white, bold: true, center: true)
        if let totLabel {
            drawText(totLabel, x: colX(10) + cellW / 2, y: y + 40, size: 28, color: .white, bold: true, center: true)
        }

        // Fila de par (fondo suave)
        y += rowH
        c.setFillColor(greenSoft.cgColor)
        c.fill(CGRect(x: x0, y: y, width: W - 2 * pad, height: rowH))
        drawText("Par", x: x0 + 14, y: y + 40, size: 28, color: muted, bold: true)
        for (i, h) in range.enumerated() {
            drawText("\(CourseData.holes[h].par)", x: colX(i) + cellW / 2, y: y + 40, size: 28, color: muted, bold: true, center: true)
        }
        let parSum = range.reduce(0) { $0 + CourseData.holes[$1].par }
        drawText("\(parSum)", x: colX(9) + cellW / 2, y: y + 40, size: 28, color: muted, bold: true, center: true)
        if totLabel != nil {
            drawText("\(CourseData.totalPar)", x: colX(10) + cellW / 2, y: y + 40, size: 28, color: muted, bold: true, center: true)
        }

        // Filas por jugador
        for p in players {
            y += rowH
            drawText(String(p.name.prefix(11)), x: x0 + 14, y: y + 40, size: 28, color: ink, bold: true)
            for (i, h) in range.enumerated() {
                let s = p.strokes[h]
                let cx = colX(i) + cellW / 2
                if s > 0 {
                    let diff = s - CourseData.holes[h].par
                    drawScoreMark(c, cx: cx, cy: y + rowH / 2, diff: diff)
                    drawText("\(s)", x: cx, y: y + 40, size: 30, color: scoreColor(diff), bold: true, center: true)
                } else {
                    drawText("·", x: cx, y: y + 40, size: 30, color: grid, bold: true, center: true)
                }
            }
            let outSum = range.reduce(0) { $0 + p.strokes[$1] }
            drawText(outSum > 0 ? "\(outSum)" : "·", x: colX(9) + cellW / 2, y: y + 40, size: 30, color: ink, bold: true, center: true)
            if totLabel != nil {
                let tot = p.total()
                drawText(tot > 0 ? "\(tot)" : "·", x: colX(10) + cellW / 2, y: y + 40, size: 32, color: green, bold: true, center: true)
            }
        }

        // Borde del bloque
        c.setStrokeColor(grid.cgColor)
        c.setLineWidth(2)
        c.stroke(CGRect(x: x0, y: top, width: W - 2 * pad, height: y + rowH - top))
        return y + rowH
    }

    /// Círculo para bajo par, cuadro para sobre par (como en la app).
    private static func drawScoreMark(_ c: CGContext, cx: CGFloat, cy: CGFloat, diff: Int) {
        if diff == 0 { return }
        let r: CGFloat = 24
        c.setStrokeColor((diff < 0 ? birdie : bogey).cgColor)
        c.setLineWidth(2.5)
        let rect = CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)
        if diff < 0 {
            c.strokeEllipse(in: rect)
        } else {
            c.addPath(UIBezierPath(roundedRect: rect, cornerRadius: 6).cgPath)
            c.strokePath()
        }
    }

    private static func drawSummary(_ p: Player, top: CGFloat) {
        let rel = p.relativeToPar()
        let relTxt: String = {
            if p.playedHoles() == 0 { return "–" }
            if rel == 0 { return "E" }
            return rel > 0 ? "+\(rel)" : "\(rel)"
        }()
        var bits = ["\(p.total()) (\(relTxt))"]
        let pts = p.stablefordPoints()
        if pts > 0 { bits.append("\(pts) pts" + (p.hcp > 0 ? " · hcp \(p.hcp)" : "")) }
        let (fh, fa) = p.firStats()
        if fa > 0 { bits.append("FIR \(fh)/\(fa)") }
        if p.girTracked() > 0 { bits.append("GIR \(p.girCount())/\(p.girTracked())") }
        if p.totalPutts() > 0 { bits.append("\(p.totalPutts()) putts") }

        drawText(String(p.name.prefix(12)), x: pad, y: top + 40, size: 30, color: ink, bold: true)
        drawText(bits.joined(separator: "   ·   "), x: pad + 260, y: top + 40, size: 26, color: muted, bold: false)
    }
}
