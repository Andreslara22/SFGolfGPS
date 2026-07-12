import SwiftUI

// ---------------------------------------------------------------- Players

struct PlayersScreen: View {
    @ObservedObject var model: GolfModel
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("🏌️ Jugadores")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(pal.onBackground)
                Text("Hasta 5 jugadores por ronda")
                    .font(.system(size: 14))
                    .foregroundColor(pal.onSurfaceVariant)
                Spacer().frame(height: 14)

                VStack(spacing: 10) {
                    ForEach(Array(model.players.enumerated()), id: \.element.id) { i, player in
                        PlayerCard(model: model, index: i)
                    }
                }
                Spacer().frame(height: 14)

                if model.players.count < 5 {
                    Button { model.addPlayer() } label: {
                        Text("＋ Agregar jugador")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(pal.onPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                            .background(RoundedRectangle(cornerRadius: 16).fill(pal.primary))
                    }
                    .buttonStyle(.plain)
                } else {
                    Text("Máximo de 5 jugadores alcanzado")
                        .foregroundColor(pal.onSurfaceVariant)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(16)
        }
        .background(pal.background)
    }
}

private struct PlayerCard: View {
    @ObservedObject var model: GolfModel
    let index: Int
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        VStack(spacing: 6) {
            HStack {
                TextField("Jugador \(index + 1)", text: Binding(
                    get: { model.players.indices.contains(index) ? model.players[index].name : "" },
                    set: { model.renamePlayer(index, String($0.prefix(14))) }
                ))
                .font(.system(size: 16))
                .foregroundColor(pal.onSurface)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(pal.outline, lineWidth: 1))

                if model.players.count > 1 {
                    Button { model.removePlayer(index) } label: {
                        Text("Quitar")
                            .font(.system(size: 14))
                            .foregroundColor(pal.error)
                    }
                    .buttonStyle(.plain)
                }
            }
            // Handicap de juego: reparte golpes de ventaja en Stableford.
            HStack(spacing: 10) {
                Text("HANDICAP")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(pal.onSurfaceVariant)
                RoundIconButton(label: "−", filled: false, size: 34) {
                    model.adjustHandicap(index, -1)
                }
                Text(model.players.indices.contains(index) ? "\(model.players[index].hcp)" : "0")
                    .font(.system(size: 16, weight: .black))
                    .foregroundColor(pal.onSurface)
                    .frame(width: 44)
                RoundIconButton(label: "+", filled: false, size: 34) {
                    model.adjustHandicap(index, 1)
                }
                Spacer()
                if model.players.indices.contains(index),
                   let idx = model.handicapIndex(model.players[index].name) {
                    Text("índice \(String(format: "%.1f", idx))")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(pal.primary)
                }
            }
            .padding(.top, 6)
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 18).fill(pal.surface))
    }
}
