import SwiftUI

// ---------------------------------------------------------------- Settings

struct SettingsScreen: View {
    @ObservedObject var model: GolfModel
    @Environment(\.colorScheme) private var scheme
    @State private var showResetDialog = false

    var body: some View {
        let pal = scheme.pal
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("⚙️ Ajustes")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(pal.onBackground)
                Spacer().frame(height: 18)

                sectionTitle("Unidades", pal)
                Spacer().frame(height: 8)
                HStack(spacing: 8) {
                    ChoiceButton(label: "Yardas", selected: model.units == .yards) {
                        model.setUnitsAndSave(.yards)
                    }
                    ChoiceButton(label: "Metros", selected: model.units == .meters) {
                        model.setUnitsAndSave(.meters)
                    }
                }

                Spacer().frame(height: 22)
                sectionTitle("Tema", pal)
                Spacer().frame(height: 8)
                HStack(spacing: 8) {
                    ChoiceButton(label: "Sistema", selected: model.themeMode == .system) {
                        model.setThemeAndSave(.system)
                    }
                    ChoiceButton(label: "Claro", selected: model.themeMode == .light) {
                        model.setThemeAndSave(.light)
                    }
                    ChoiceButton(label: "Oscuro", selected: model.themeMode == .dark) {
                        model.setThemeAndSave(.dark)
                    }
                }

                Spacer().frame(height: 22)
                sectionTitle("Elevación (\"juega como\")", pal)
                Text("La app aprende la elevación de cada green cuando lo pisas con el GPS activo. Después de una ronda, las distancias se ajustan solas en tiros cuesta arriba/abajo.")
                    .font(.system(size: 12))
                    .foregroundColor(pal.onSurfaceVariant)
                Spacer().frame(height: 8)
                HStack(spacing: 10) {
                    Pill(text: "\(model.calibratedGreens)/18 greens calibrados",
                         bg: pal.primaryContainer, fg: pal.onPrimaryContainer)
                    if model.calibratedGreens > 0 {
                        Button { model.resetElevations() } label: {
                            Text("✕ borrar")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(pal.error)
                        }
                        .buttonStyle(.plain)
                    }
                }

                Spacer().frame(height: 22)
                sectionTitle("Distancias de palos (yd)", pal)
                Text("Se usan para el palo sugerido, por jugador")
                    .font(.system(size: 12))
                    .foregroundColor(pal.onSurfaceVariant)
                Spacer().frame(height: 8)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(Array(model.players.enumerated()), id: \.element.id) { i, p in
                            MiniChip(label: String(p.name.prefix(8)),
                                     selected: i == model.activePlayerIndex) {
                                model.setActivePlayer(i)
                            }
                        }
                    }
                }
                Spacer().frame(height: 8)
                clubsCard(pal)

                Spacer().frame(height: 22)
                sectionTitle("Ronda", pal)
                Spacer().frame(height: 8)
                Button { showResetDialog = true } label: {
                    Text("Borrar golpes (sin guardar)")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(pal.onError)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 12)
                        .background(RoundedRectangle(cornerRadius: 16).fill(pal.error))
                }
                .buttonStyle(.plain)

                Spacer().frame(height: 28)
                Divider().background(pal.outline)
                Spacer().frame(height: 14)
                Text("⛳ \(CourseData.clubName)")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(pal.primary)
                Text("\(CourseData.city) · 18 hoyos · Par \(CourseData.totalPar)\nLa pantalla se mantiene encendida durante la ronda.\nDistancias medidas por GPS (Haversine) al centro del green.")
                    .font(.system(size: 12))
                    .foregroundColor(pal.onSurfaceVariant)
                Spacer().frame(height: 24)
            }
            .padding(16)
        }
        .background(pal.background)
        .alert("¿Borrar todos los golpes?", isPresented: $showResetDialog) {
            Button("Sí, borrar", role: .destructive) { model.clearStrokes() }
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Esto borra la tarjeta actual de todos los jugadores sin guardarla en el historial.")
        }
    }

    private func sectionTitle(_ text: String, _ pal: Pal) -> some View {
        Text(text)
            .font(.system(size: 17, weight: .semibold))
            .foregroundColor(pal.onBackground)
    }

    @ViewBuilder
    private func clubsCard(_ pal: Pal) -> some View {
        let idx = model.players.indices.contains(model.activePlayerIndex) ? model.activePlayerIndex : 0
        VStack(spacing: 0) {
            ForEach(Array(clubNames.enumerated()), id: \.offset) { ci, clubName in
                HStack {
                    Text(clubName)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(pal.onSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    RoundIconButton(label: "−", filled: false, size: 36) {
                        model.adjustClub(idx, ci, -5)
                    }
                    Text("\(model.players[idx].clubYards[ci])")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(pal.onSurface)
                        .frame(width: 52)
                    RoundIconButton(label: "+", filled: false, size: 36) {
                        model.adjustClub(idx, ci, 5)
                    }
                }
                .padding(.vertical, 2)
            }
            Button { model.resetClubs(idx) } label: {
                Text("Restablecer valores")
                    .font(.system(size: 14))
                    .foregroundColor(pal.primary)
                    .padding(.vertical, 10)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 6)
        .background(RoundedRectangle(cornerRadius: 18).fill(pal.surface))
    }
}
