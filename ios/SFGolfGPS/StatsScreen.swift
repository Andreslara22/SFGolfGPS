import SwiftUI

// ---------------------------------------------------------------- Stats

/// Nombres con historial o en la ronda actual, sin duplicar y en orden.
private func statNames(_ model: GolfModel) -> [String] {
    var names: [String] = []
    for p in model.players where !names.contains(p.name) { names.append(p.name) }
    for r in model.history {
        for e in r.entries where !names.contains(e.name) { names.append(e.name) }
    }
    return names
}

struct StatsScreen: View {
    @ObservedObject var model: GolfModel
    @Environment(\.colorScheme) private var scheme
    @State private var selected = ""

    var body: some View {
        let pal = scheme.pal
        let names = statNames(model)
        let effective = names.contains(selected) ? selected : (names.first ?? "")

        // Rondas del jugador (más nueva primero); "full" = rondas completas de 18 hoyos.
        let entries = model.history.compactMap { r in
            r.entries.first { $0.name == effective }
        }
        let full = entries.filter { $0.holes == 18 }

        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: 12)
                Text("📊 Stats")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(pal.onBackground)
                Text("Tendencias de tus rondas guardadas")
                    .font(.system(size: 14))
                    .foregroundColor(pal.onSurfaceVariant)
                Spacer().frame(height: 10)

                if names.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(names, id: \.self) { n in
                                MiniChip(label: String(n.prefix(10)), selected: n == effective) {
                                    selected = n
                                }
                            }
                        }
                    }
                    Spacer().frame(height: 12)
                }

                if entries.isEmpty {
                    Text("Aquí verás promedios, tendencia y tus hoyos más difíciles. Termina y guarda rondas en la Tarjeta para llenarlo. 🌱")
                        .foregroundColor(pal.onSurfaceVariant)
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 16).fill(pal.surfaceVariant))
                    Spacer().frame(height: 24)
                } else {
                    statsBody(pal, entries: entries, full: full, name: effective)
                }
            }
            .padding(.horizontal, 16)
        }
        .background(pal.background)
        .onAppear {
            if selected.isEmpty {
                selected = model.players.indices.contains(model.activePlayerIndex)
                    ? model.players[model.activePlayerIndex].name : ""
            }
        }
    }

    @ViewBuilder
    private func statsBody(_ pal: Pal, entries: [SavedRound.Entry],
                           full: [SavedRound.Entry], name: String) -> some View {
        // ---- Resumen ----
        let scores = full.map(\.strokes)
        let puttRounds = full.filter { $0.putts > 0 }
        let girHit = entries.reduce(0) { $0 + $1.gir }
        let girAtt = entries.reduce(0) { $0 + $1.girTracked }
        let firHit = entries.reduce(0) { $0 + $1.firHit }
        let firAtt = entries.reduce(0) { $0 + $1.firAtt }
        let avgScore = scores.isEmpty ? nil : Double(scores.reduce(0, +)) / Double(scores.count)

        HStack(spacing: 8) {
            StatTile(label: "RONDAS", value: "\(entries.count)")
            StatTile(label: "PROMEDIO", value: avgScore.map { String(format: "%.1f", $0) } ?? "—")
            StatTile(label: "MEJOR", value: scores.min().map(String.init) ?? "—")
        }
        Spacer().frame(height: 8)
        HStack(spacing: 8) {
            StatTile(label: "PUTTS/RONDA", value: puttRounds.isEmpty ? "—"
                     : String(format: "%.1f", Double(puttRounds.reduce(0) { $0 + $1.putts }) / Double(puttRounds.count)))
            StatTile(label: "GIR", value: girAtt > 0 ? "\(Int((Double(girHit) * 100 / Double(girAtt)).rounded()))%" : "—")
            StatTile(label: "FIR", value: firAtt > 0 ? "\(Int((Double(firHit) * 100 / Double(firAtt)).rounded()))%" : "—")
        }
        if scores.isEmpty {
            Spacer().frame(height: 4)
            Text("Promedio y mejor score usan solo rondas completas de 18 hoyos.")
                .font(.system(size: 11))
                .foregroundColor(pal.onSurfaceVariant)
        }
        Spacer().frame(height: 14)

        // ---- Handicap index (WHS) ----
        VStack(alignment: .leading, spacing: 2) {
            Text("HANDICAP INDEX (WHS)")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(pal.onSurfaceVariant)
            if let idx = model.handicapIndex(name) {
                Text(String(format: "%.1f", idx))
                    .font(.system(size: 36, weight: .black))
                    .foregroundColor(pal.primary)
                Text("Con \(full.count) ronda\(full.count == 1 ? "" : "s") completa\(full.count == 1 ? "" : "s") · mejores diferenciales de las últimas 20 · rating \(String(format: "%.1f", CourseData.courseRating)) / slope \(CourseData.slopeRating)")
                    .font(.system(size: 12))
                    .foregroundColor(pal.onSurfaceVariant)
            } else {
                Text("Se calcula con al menos 3 rondas completas de 18 hoyos — llevas \(full.count).")
                    .font(.system(size: 12))
                    .foregroundColor(pal.onSurfaceVariant)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 16).fill(pal.surface))
        Spacer().frame(height: 14)

        // ---- Tendencia: últimas rondas completas (izquierda = más vieja) ----
        let trend = Array(full.prefix(10).reversed())
        if trend.count >= 2 {
            Text("Tendencia")
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(pal.onBackground)
            Text("Últimas \(trend.count) rondas de 18 hoyos · barra más baja = mejor score")
                .font(.system(size: 11))
                .foregroundColor(pal.onSurfaceVariant)
            Spacer().frame(height: 8)
            let minS = trend.map(\.strokes).min()!
            let maxS = trend.map(\.strokes).max()!
            HStack(alignment: .bottom, spacing: 6) {
                ForEach(Array(trend.enumerated()), id: \.offset) { _, e in
                    let frac = maxS == minS ? 0.5 : Double(e.strokes - minS) / Double(maxS - minS)
                    VStack(spacing: 2) {
                        Text("\(e.strokes)")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(e.strokes == minS ? pal.primary : pal.onSurfaceVariant)
                        UnevenRoundedRectangle(topLeadingRadius: 6, topTrailingRadius: 6)
                            .fill(e.strokes == minS ? pal.primary : pal.onSurfaceVariant.opacity(0.35))
                            .frame(height: 30 + 42 * frac)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 16).fill(pal.surface))
            Spacer().frame(height: 14)
        }

        // ---- Promedio por hoyo: dónde pierdes golpes ----
        let holed = full.filter { $0.holeStrokes.count == 18 }
        Text("Promedio por hoyo")
            .font(.system(size: 17, weight: .bold))
            .foregroundColor(pal.onBackground)
        if holed.isEmpty {
            Text("Se llena con tus próximas rondas guardadas (las rondas viejas no guardaron el detalle hoyo por hoyo).")
                .font(.system(size: 12))
                .foregroundColor(pal.onSurfaceVariant)
            Spacer().frame(height: 24)
        } else {
            Text("🔥 = tus 3 hoyos más caros vs par (\(holed.count) ronda\(holed.count == 1 ? "" : "s"))")
                .font(.system(size: 11))
                .foregroundColor(pal.onSurfaceVariant)
            Spacer().frame(height: 8)
            let avgs: [Double?] = (0..<18).map { h in
                let vals = holed.compactMap { e -> Int? in
                    let s = e.holeStrokes[h]
                    return s > 0 ? s : nil
                }
                return vals.isEmpty ? nil : Double(vals.reduce(0, +)) / Double(vals.count)
            }
            let worst = Set(
                avgs.enumerated()
                    .compactMap { (h, a) in a.map { (h, $0 - Double(CourseData.holes[h].par)) } }
                    .filter { $0.1 > 0 }
                    .sorted { $0.1 > $1.1 }
                    .prefix(3)
                    .map(\.0)
            )
            VStack(spacing: 0) {
                ForEach(Array(CourseData.holes.enumerated()), id: \.element.number) { h, hole in
                    let avg = avgs[h]
                    let diff = avg.map { $0 - Double(hole.par) }
                    HStack {
                        Text("H\(hole.number)" + (worst.contains(h) ? " 🔥" : ""))
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(pal.onSurface)
                            .frame(width: 64, alignment: .leading)
                        Text("Par \(hole.par)")
                            .font(.system(size: 13))
                            .foregroundColor(pal.onSurfaceVariant)
                            .frame(width: 52, alignment: .leading)
                        Text(avg.map { String(format: "%.1f", $0) } ?? "—")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(pal.onSurface)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                        Text(diff.map { $0 >= 0 ? String(format: "+%.1f", $0) : String(format: "%.1f", $0) } ?? "")
                            .font(.system(size: 14, weight: .black))
                            .foregroundColor({
                                guard let diff else { return pal.onSurfaceVariant }
                                if diff <= 0 { return pal.primary }
                                if diff < 0.5 { return pal.onSurfaceVariant }
                                return pal.error
                            }())
                            .frame(width: 58, alignment: .trailing)
                    }
                    .padding(.vertical, 3)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 16).fill(pal.surface))
            Spacer().frame(height: 24)
        }
    }
}
