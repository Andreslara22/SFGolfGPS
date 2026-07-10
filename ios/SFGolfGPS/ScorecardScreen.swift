import SwiftUI

// ---------------------------------------------------------------- Scorecard

struct ScorecardScreen: View {
    @ObservedObject var model: GolfModel
    @Environment(\.colorScheme) private var scheme
    @State private var showFinishDialog = false
    @State private var shareImage: UIImage?

    private static let dateFmt: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMM d · h:mm a"
        f.locale = Locale(identifier: "en_US")
        return f
    }()

    var body: some View {
        let pal = scheme.pal
        let anyScores = model.players.contains { $0.playedHoles() > 0 }

        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: 12)
                Text("📋 Scorecard")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(pal.onBackground)
                Text("\(CourseData.clubName) · Par \(CourseData.totalPar)")
                    .font(.system(size: 14))
                    .foregroundColor(pal.onSurfaceVariant)
                Spacer().frame(height: 12)

                // Resumen por jugador
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(model.players) { p in
                            SummaryChip(player: p)
                        }
                    }
                }
                Spacer().frame(height: 12)

                scorecardTable(pal)
                Spacer().frame(height: 6)
                Text("⭕ under par · ⬜ over par")
                    .font(.system(size: 11))
                    .foregroundColor(pal.onSurfaceVariant)
                    .frame(maxWidth: .infinity)
                Spacer().frame(height: 14)

                if anyScores {
                    roundStats(pal)
                    Spacer().frame(height: 14)
                }

                if model.players.count >= 2 {
                    gamesCard(pal)
                    Spacer().frame(height: 14)
                }

                Button { showFinishDialog = true } label: {
                    Text("🏁 Finish round & save")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(pal.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(RoundedRectangle(cornerRadius: 16).fill(pal.primary))
                }
                .buttonStyle(.plain)

                Spacer().frame(height: 8)
                Button {
                    shareImage = ScorecardImage.render(model: model)
                } label: {
                    Text("📤 Compartir tarjeta como imagen")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(pal.primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(pal.outline, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .disabled(!anyScores)
                .opacity(anyScores ? 1 : 0.5)

                Spacer().frame(height: 22)
                Text("Round history")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(pal.onBackground)
                Spacer().frame(height: 8)
                if model.history.isEmpty {
                    Text("No saved rounds yet. Finish a round to keep it here. 🌱")
                        .foregroundColor(pal.onSurfaceVariant)
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 16).fill(pal.surfaceVariant))
                }
                VStack(spacing: 8) {
                    ForEach(Array(model.history.enumerated()), id: \.element.id) { i, round in
                        RoundHistoryCard(round: round, fmt: Self.dateFmt) {
                            model.deleteRound(i)
                        }
                    }
                }
                Spacer().frame(height: 24)
            }
            .padding(.horizontal, 12)
        }
        .background(pal.background)
        .alert("Finish this round?", isPresented: $showFinishDialog) {
            Button("Save & reset") { model.finishRound() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Scores will be saved to your history and the scorecard will reset to hole 1.")
        }
        .sheet(isPresented: Binding(
            get: { shareImage != nil },
            set: { if !$0 { shareImage = nil } }
        )) {
            if let img = shareImage {
                ActivityView(image: img)
            }
        }
    }

    // ---- Tabla ----

    @ViewBuilder
    private func scorecardTable(_ pal: Pal) -> some View {
        VStack(spacing: 0) {
            HeaderRow(model: model)
                .background(pal.primaryContainer)
            ForEach(CourseData.holes, id: \.number) { hole in
                HoleRowView(model: model, hole: hole)
                if hole.number == 9 {
                    TotalsRow(model: model, label: "OUT", range: 0..<9)
                        .background(pal.surfaceVariant.opacity(0.6))
                }
            }
            TotalsRow(model: model, label: "IN", range: 9..<18)
                .background(pal.surfaceVariant.opacity(0.6))
            VStack(spacing: 0) {
                TotalsRow(model: model, label: "TOTAL", range: 0..<18, bold: true)
                RelativeRow(model: model)
            }
            .background(pal.primaryContainer)
        }
        .background(pal.surface)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    // ---- Stats de la ronda actual ----

    @ViewBuilder
    private func roundStats(_ pal: Pal) -> some View {
        Text("🎯 Round stats")
            .font(.system(size: 17, weight: .bold))
            .foregroundColor(pal.onBackground)
        Spacer().frame(height: 8)
        VStack(spacing: 0) {
            ForEach(model.players.filter { $0.playedHoles() > 0 }) { p in
                let (fh, fa) = p.firStats()
                let girT = p.girTracked()
                HStack {
                    Text(p.name.prefix(10))
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(pal.onSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    StatBadge(label: "FIR", value: fa > 0 ? "\(fh)/\(fa)" : "—")
                    Spacer().frame(width: 10)
                    StatBadge(label: "GIR", value: girT > 0 ? "\(p.girCount())/\(girT)" : "—")
                    Spacer().frame(width: 10)
                    StatBadge(label: "PUTTS", value: p.totalPutts() > 0 ? "\(p.totalPutts())" : "—")
                }
                .padding(.vertical, 4)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 16).fill(pal.surface))
    }

    // ---- Juegos: Skins + Match Play + Stableford ----

    @ViewBuilder
    private func gamesCard(_ pal: Pal) -> some View {
        Text("🏆 Games")
            .font(.system(size: 17, weight: .bold))
            .foregroundColor(pal.onBackground)
        Spacer().frame(height: 8)
        VStack(alignment: .leading, spacing: 2) {
            let (skins, pot) = model.skinsStandings()
            Text("SKINS")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(pal.onSurfaceVariant)
            if skins.allSatisfy({ $0 == 0 }) && pot == 0 {
                Text("Anota los golpes de todos los jugadores en un hoyo y aquí aparecen los skins. Empates se acarrean.")
                    .font(.system(size: 12))
                    .foregroundColor(pal.onSurfaceVariant)
            } else {
                let leaderSkins = skins.max() ?? 0
                ForEach(Array(model.players.enumerated()), id: \.element.id) { i, p in
                    HStack {
                        Text((skins[i] == leaderSkins && leaderSkins > 0 ? "👑 " : "") + p.name.prefix(12))
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(pal.onSurface)
                        Spacer()
                        Text("\(skins[i]) skin\(skins[i] == 1 ? "" : "s")")
                            .font(.system(size: 15, weight: .heavy))
                            .foregroundColor(pal.primary)
                    }
                    .padding(.vertical, 2)
                }
                if pot > 0 {
                    Text("🔥 \(pot) skin\(pot == 1 ? "" : "s") acarreado\(pot == 1 ? "" : "s") al siguiente hoyo")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(pal.error)
                }
            }

            if let status = model.matchPlayStatus() {
                Spacer().frame(height: 10)
                Text("MATCH PLAY")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(pal.onSurfaceVariant)
                Text(status)
                    .font(.system(size: 17, weight: .black))
                    .foregroundColor(pal.primary)
            }

            // ---- Stableford con handicap: puntos netos por hoyo ----
            if model.players.contains(where: { $0.playedHoles() > 0 }) {
                Spacer().frame(height: 10)
                Text("STABLEFORD · CON HANDICAP")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(pal.onSurfaceVariant)
                let pts = model.players.map { $0.stablefordPoints() }
                let best = pts.max() ?? 0
                ForEach(Array(model.players.enumerated()), id: \.element.id) { i, p in
                    HStack {
                        Text((pts[i] == best && best > 0 ? "👑 " : "") +
                             p.name.prefix(12) + "  · hcp \(p.hcp)")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(pal.onSurface)
                        Spacer()
                        Text("\(pts[i]) pts")
                            .font(.system(size: 15, weight: .heavy))
                            .foregroundColor(pal.primary)
                    }
                    .padding(.vertical, 2)
                }
                if model.players.allSatisfy({ $0.hcp == 0 }) {
                    Text("Par neto = 2 pts, birdie 3, bogey 1. Configura el handicap de cada jugador en Players para que reparta golpes de ventaja.")
                        .font(.system(size: 12))
                        .foregroundColor(pal.onSurfaceVariant)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 16).fill(pal.surface))
    }
}

// ---------------------------------------------------------------- Sub-vistas

struct SummaryChip: View {
    let player: Player
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        let rel = player.relativeToPar()
        let relLabel: String = {
            if player.playedHoles() == 0 { return "–" }
            if rel == 0 { return "E" }
            return rel > 0 ? "+\(rel)" : "\(rel)"
        }()
        VStack(alignment: .leading, spacing: 1) {
            Text(player.name.prefix(10))
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(pal.onSurface)
                .lineLimit(1)
            HStack(alignment: .bottom, spacing: 6) {
                Text("\(player.total())")
                    .font(.system(size: 20, weight: .heavy))
                    .foregroundColor(pal.onSurface)
                Text(relLabel)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(player.playedHoles() > 0
                                     ? pal.onSurface : pal.onSurfaceVariant)
                    .padding(.bottom, 2)
                Text("\(player.playedHoles())/18")
                    .font(.system(size: 12))
                    .foregroundColor(pal.onSurfaceVariant)
                    .padding(.bottom, 2)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 16).fill(pal.surfaceVariant))
    }
}

struct RoundHistoryCard: View {
    let round: SavedRound
    let fmt: DateFormatter
    let onDelete: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("🏆 " + fmt.string(from: round.date))
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(pal.onSurface)
                ForEach(Array(round.entries.enumerated()), id: \.offset) { _, e in
                    let relLabel = e.relative == 0 ? "E" : (e.relative > 0 ? "+\(e.relative)" : "\(e.relative)")
                    Text("\(e.name): \(e.strokes) strokes (\(relLabel) · \(e.holes)/18 holes)")
                        .font(.system(size: 12))
                        .foregroundColor(pal.onSurfaceVariant)
                    let bits: [String] = {
                        var b: [String] = []
                        if e.firAtt > 0 { b.append("FIR \(e.firHit)/\(e.firAtt)") }
                        if e.girTracked > 0 { b.append("GIR \(e.gir)/\(e.girTracked)") }
                        if e.putts > 0 { b.append("\(e.putts) putts") }
                        if e.points > 0 { b.append("\(e.points) pts" + (e.hcp > 0 ? " (hcp \(e.hcp))" : "")) }
                        return b
                    }()
                    if !bits.isEmpty {
                        Text("    " + bits.joined(separator: " · "))
                            .font(.system(size: 11))
                            .foregroundColor(pal.primary)
                    }
                }
            }
            Spacer()
            Button(action: onDelete) {
                Text("Delete")
                    .font(.system(size: 13))
                    .foregroundColor(pal.error)
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 16).fill(pal.surface))
    }
}

struct HeaderRow: View {
    @ObservedObject var model: GolfModel
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        HStack(spacing: 0) {
            Text("Hole").frame(width: 46, alignment: .leading)
            Text("Par").frame(width: 36)
            ForEach(model.players) { p in
                Text(p.name.prefix(6))
                    .frame(maxWidth: .infinity)
                    .lineLimit(1)
            }
        }
        .font(.system(size: 13, weight: .bold))
        .foregroundColor(pal.onPrimaryContainer)
        .padding(.horizontal, 10)
        .padding(.vertical, 9)
    }
}

struct HoleRowView: View {
    @ObservedObject var model: GolfModel
    let hole: Hole
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        let isCurrent = model.currentHoleIndex == hole.number - 1
        let stripe = hole.number % 2 == 0
        HStack(spacing: 0) {
            Text((isCurrent ? "⛳" : "") + "\(hole.number)")
                .font(.system(size: 15, weight: isCurrent ? .black : .regular))
                .foregroundColor(pal.onSurface)
                .frame(width: 46, alignment: .leading)
            Text("\(hole.par)")
                .font(.system(size: 15))
                .foregroundColor(pal.onSurfaceVariant)
                .frame(width: 36)
            ForEach(model.players) { p in
                ScoreCell(strokes: p.strokes[hole.number - 1], par: hole.par)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(
            isCurrent ? pal.primaryContainer.opacity(0.45)
            : (stripe ? pal.surfaceVariant.opacity(0.25) : .clear)
        )
    }
}

struct ScoreCell: View {
    let strokes: Int
    let par: Int
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        if strokes <= 0 {
            Text("·").foregroundColor(pal.onSurfaceVariant)
        } else {
            let diff = strokes - par
            let frame: Color = diff < 0 ? pal.primary : (diff > 0 ? pal.error : .clear)
            Text("\(strokes)")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(pal.onSurface)
                .frame(width: 27, height: 27)
                .overlay(
                    Group {
                        if diff < 0 {
                            Circle().strokeBorder(frame, lineWidth: 1.6)
                        } else {
                            RoundedRectangle(cornerRadius: 5).strokeBorder(frame, lineWidth: 1.6)
                        }
                    }
                )
        }
    }
}

struct TotalsRow: View {
    @ObservedObject var model: GolfModel
    let label: String
    let range: Range<Int>
    var bold = false
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        let parSum = range.reduce(0) { $0 + CourseData.holes[$1].par }
        HStack(spacing: 0) {
            Text(label)
                .font(.system(size: 12, weight: .bold))
                .lineLimit(1)
                .fixedSize()
                .frame(width: 52, alignment: .leading)
            Text("\(parSum)")
                .font(.system(size: 12, weight: .bold))
                .frame(width: 30)
            ForEach(model.players) { p in
                let sum = range.reduce(0) { $0 + p.strokes[$1] }
                Text(sum > 0 ? "\(sum)" : "·")
                    .font(.system(size: 15, weight: bold ? .black : .bold))
                    .frame(maxWidth: .infinity)
            }
        }
        .foregroundColor(pal.onSurface)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
    }
}

struct RelativeRow: View {
    @ObservedObject var model: GolfModel
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        HStack(spacing: 0) {
            Text("vs Par")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(pal.onSurface)
                .frame(width: 82, alignment: .leading)
            ForEach(model.players) { p in
                let rel = p.relativeToPar()
                let label: String = {
                    if p.playedHoles() == 0 { return "·" }
                    if rel == 0 { return "E" }
                    return rel > 0 ? "+\(rel)" : "\(rel)"
                }()
                Text(label)
                    .font(.system(size: 15, weight: .black))
                    .foregroundColor(p.playedHoles() > 0
                                     ? pal.onSurface : pal.onSurfaceVariant)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
    }
}

/// Share sheet de UIKit (para compartir el PNG de la tarjeta).
struct ActivityView: UIViewControllerRepresentable {
    let image: UIImage

    func makeUIViewController(context: Context) -> UIActivityViewController {
        // PNG con nombre de archivo, para que WhatsApp/Mail lo traten como imagen.
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("tarjeta_sfgolf.png")
        try? image.pngData()?.write(to: url)
        return UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
