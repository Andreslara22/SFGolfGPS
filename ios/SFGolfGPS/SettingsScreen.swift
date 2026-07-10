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
                Text("⚙️ Settings")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(pal.onBackground)
                Spacer().frame(height: 18)

                sectionTitle("Units", pal)
                Spacer().frame(height: 8)
                HStack(spacing: 8) {
                    ChoiceButton(label: "Yards", selected: model.units == .yards) {
                        model.setUnitsAndSave(.yards)
                    }
                    ChoiceButton(label: "Meters", selected: model.units == .meters) {
                        model.setUnitsAndSave(.meters)
                    }
                }

                Spacer().frame(height: 22)
                sectionTitle("Theme", pal)
                Spacer().frame(height: 8)
                HStack(spacing: 8) {
                    ChoiceButton(label: "System", selected: model.themeMode == .system) {
                        model.setThemeAndSave(.system)
                    }
                    ChoiceButton(label: "Light", selected: model.themeMode == .light) {
                        model.setThemeAndSave(.light)
                    }
                    ChoiceButton(label: "Dark", selected: model.themeMode == .dark) {
                        model.setThemeAndSave(.dark)
                    }
                }

                Spacer().frame(height: 22)
                sectionTitle("Elevation (\"plays like\")", pal)
                Text("The app learns each green's elevation when you walk onto it with GPS on. After one round, distances adjust automatically for uphill/downhill shots.")
                    .font(.system(size: 12))
                    .foregroundColor(pal.onSurfaceVariant)
                Spacer().frame(height: 8)
                HStack(spacing: 10) {
                    Pill(text: "\(model.calibratedGreens)/18 greens calibrated",
                         bg: pal.primaryContainer, fg: pal.onPrimaryContainer)
                    if model.calibratedGreens > 0 {
                        Button { model.resetElevations() } label: {
                            Text("✕ reset")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(pal.error)
                        }
                        .buttonStyle(.plain)
                    }
                }

                Spacer().frame(height: 22)
                sectionTitle("Club distances (yd)", pal)
                Text("Used for the suggested club, per player")
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
                sectionTitle("Round", pal)
                Spacer().frame(height: 8)
                Button { showResetDialog = true } label: {
                    Text("Clear strokes (without saving)")
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
                Text("\(CourseData.city) · 18 holes · Par \(CourseData.totalPar)\nScreen stays awake during your round.\nDistances measured by GPS (Haversine) to the center of the green.")
                    .font(.system(size: 12))
                    .foregroundColor(pal.onSurfaceVariant)
                Spacer().frame(height: 24)
            }
            .padding(16)
        }
        .background(pal.background)
        .alert("Clear all strokes?", isPresented: $showResetDialog) {
            Button("Yes, clear", role: .destructive) { model.clearStrokes() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This wipes the current scorecard for every player without saving it to history.")
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
                Text("Reset to defaults")
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
