import SwiftUI

@main
struct SFGolfGPSApp: App {
    @StateObject private var model = GolfModel()

    var body: some Scene {
        WindowGroup {
            GolfAppView(model: model)
                .preferredColorScheme(model.themeMode.colorScheme)
        }
    }
}

struct GolfAppView: View {
    @ObservedObject var model: GolfModel
    @Environment(\.colorScheme) private var scheme
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        let pal = scheme.pal
        TabView {
            RangeScreen(model: model)
                .tabItem { Label("Range", systemImage: "flag.circle.fill") }
            ScorecardScreen(model: model)
                .tabItem { Label("Score", systemImage: "list.clipboard.fill") }
            StatsScreen(model: model)
                .tabItem { Label("Stats", systemImage: "chart.bar.fill") }
            PlayersScreen(model: model)
                .tabItem { Label("Players", systemImage: "figure.golf") }
            SettingsScreen(model: model)
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
        }
        .tint(pal.primary)
        .background(pal.background)
        .onAppear {
            // Pantalla siempre encendida durante la ronda (como en Android).
            UIApplication.shared.isIdleTimerDisabled = true
            model.requestLocation()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                model.requestLocation()
            } else if phase == .background {
                model.saveState()
            }
        }
    }
}
