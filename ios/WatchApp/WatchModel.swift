import Foundation
import CoreLocation
import SwiftUI

/// Jugador en el reloj: nombre + 18 golpes + yardas por palo (espejo del cel).
struct WPlayer {
    var name: String
    var strokes: [Int] = Array(repeating: 0, count: 18)
    var clubs: [Int] = defaultClubYards
}

/// Estado de la app de reloj. Port del MainActivity del módulo Wear OS:
/// GPS propio del reloj, snapshot sincronizado con el iPhone por
/// WatchConnectivity (last-write-wins por timestamp) y persistencia local
/// en UserDefaults para abrir la app sin el teléfono cerca.
final class WatchModel: NSObject, ObservableObject, CLLocationManagerDelegate {

    private let prefs = UserDefaults.standard
    private let locationManager = CLLocationManager()
    private let sync = RoundSync()
    private var stateTs: Int64 = 0

    @Published private(set) var lat: Double?
    @Published private(set) var lng: Double?
    @Published var granted = false
    @Published private(set) var holeIdx = 0
    @Published private(set) var auto = true
    @Published var useMeters = false
    @Published var players: [WPlayer] = []
    @Published private(set) var activePlayer = 0
    @Published var flags = [Int](repeating: -1, count: 18)

    override init() {
        super.init()
        loadLocal()
        if players.isEmpty { players.append(WPlayer(name: "P1")) }

        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.activityType = .fitness

        sync.onReceive = { [weak self] dict in self?.applyIncoming(dict) }
        sync.activate()
    }

    var hole: Hole { CourseData.holes[holeIdx] }

    // ---- GPS ----

    func requestLocation() {
        switch locationManager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            granted = true
            locationManager.startUpdatingLocation()
        case .notDetermined:
            locationManager.requestWhenInUseAuthorization()
        default:
            granted = false
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let ok = manager.authorizationStatus == .authorizedWhenInUse ||
                 manager.authorizationStatus == .authorizedAlways
        granted = ok
        if ok { manager.startUpdatingLocation() }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last, loc.horizontalAccuracy >= 0 else { return }
        lat = loc.coordinate.latitude
        lng = loc.coordinate.longitude
        if auto {
            let n = CourseData.nearestHoleByTee(lat: loc.coordinate.latitude,
                                                lng: loc.coordinate.longitude)
            if haversineMeters(loc.coordinate.latitude, loc.coordinate.longitude,
                               n.teeLat, n.teeLng) <= 150.0 && holeIdx != n.number - 1 {
                holeIdx = n.number - 1
                persist() // GPS local; no publica para evitar ping-pong
            }
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}

    // ---- Persistencia local ----

    private func loadLocal() {
        let names = prefs.string(forKey: "names")?
            .components(separatedBy: SyncKeys.sep).filter { !$0.isEmpty } ?? []
        let scores = prefs.string(forKey: "scores")?.components(separatedBy: SyncKeys.sep)
        let clubs = prefs.string(forKey: "clubs")?.components(separatedBy: SyncKeys.sep)
        for (i, nm) in names.enumerated() {
            var wp = WPlayer(name: nm)
            if let scores, i < scores.count {
                let list = scores[i].split(separator: ",").compactMap { Int($0) }
                if list.count == 18 { wp.strokes = list }
            }
            if let clubs, i < clubs.count {
                let list = clubs[i].split(separator: ",").compactMap { Int($0) }
                if list.count == clubNames.count { wp.clubs = list }
            }
            players.append(wp)
        }
        activePlayer = min(max(prefs.integer(forKey: "active"), 0), max(players.count - 1, 0))
        holeIdx = min(max(prefs.integer(forKey: "hole"), 0), 17)
        useMeters = prefs.string(forKey: "units") == "METERS"
        auto = prefs.object(forKey: "auto") as? Bool ?? true
        if let f = prefs.string(forKey: "flags")?
            .split(separator: ",", omittingEmptySubsequences: false).compactMap({ Int($0) }),
           f.count == 18 {
            flags = f
        }
        stateTs = Int64(prefs.object(forKey: "stateTs") as? Double ?? 0)
    }

    private func persist() {
        prefs.set(players.map(\.name).joined(separator: SyncKeys.sep), forKey: "names")
        prefs.set(players.map { $0.strokes.map(String.init).joined(separator: ",") }
            .joined(separator: SyncKeys.sep), forKey: "scores")
        prefs.set(players.map { $0.clubs.map(String.init).joined(separator: ",") }
            .joined(separator: SyncKeys.sep), forKey: "clubs")
        prefs.set(activePlayer, forKey: "active")
        prefs.set(holeIdx, forKey: "hole")
        prefs.set(useMeters ? "METERS" : "YARDS", forKey: "units")
        prefs.set(auto, forKey: "auto")
        prefs.set(flags.map(String.init).joined(separator: ","), forKey: "flags")
        prefs.set(Double(stateTs), forKey: "stateTs")
    }

    // ---- Sincronización con el teléfono ----

    private func pushState() {
        sync.push([
            SyncKeys.names: players.map(\.name).joined(separator: SyncKeys.sep),
            SyncKeys.scores: players.map { $0.strokes.map(String.init).joined(separator: ",") }
                .joined(separator: SyncKeys.sep),
            SyncKeys.active: activePlayer,
            SyncKeys.hole: holeIdx,
            SyncKeys.units: useMeters ? "METERS" : "YARDS",
            SyncKeys.auto: auto,
            SyncKeys.flags: flags.map(String.init).joined(separator: ","),
            SyncKeys.clubs: players.map { $0.clubs.map(String.init).joined(separator: ",") }
                .joined(separator: SyncKeys.sep),
            SyncKeys.ts: stateTs
        ])
    }

    private func applyIncoming(_ dict: [String: Any]) {
        let ts = (dict[SyncKeys.ts] as? NSNumber)?.int64Value ?? 0
        if ts <= stateTs { return }
        guard let namesStr = dict[SyncKeys.names] as? String,
              let scoresStr = dict[SyncKeys.scores] as? String else { return }
        let names = namesStr.components(separatedBy: SyncKeys.sep).filter { !$0.isEmpty }
        if names.isEmpty { return }
        let scores = scoresStr.components(separatedBy: SyncKeys.sep)
        // El reloj sí adopta la lista de jugadores del teléfono.
        while players.count < names.count { players.append(WPlayer(name: "P\(players.count + 1)")) }
        while players.count > names.count { players.removeLast() }
        let clubsIn = (dict[SyncKeys.clubs] as? String)?.components(separatedBy: SyncKeys.sep)
        for (i, nm) in names.enumerated() {
            players[i].name = nm
            if i < scores.count {
                let list = scores[i].split(separator: ",").compactMap { Int($0) }
                if list.count == 18 { players[i].strokes = list }
            }
            if let clubsIn, i < clubsIn.count {
                let list = clubsIn[i].split(separator: ",").compactMap { Int($0) }
                if list.count == clubNames.count { players[i].clubs = list }
            }
        }
        if let a = dict[SyncKeys.active] as? Int {
            activePlayer = min(max(a, 0), players.count - 1)
        }
        if let h = dict[SyncKeys.hole] as? Int { holeIdx = min(max(h, 0), 17) }
        if let u = dict[SyncKeys.units] as? String { useMeters = u == "METERS" }
        if let a = dict[SyncKeys.auto] as? Bool { auto = a }
        if let f = (dict[SyncKeys.flags] as? String)?
            .split(separator: ",", omittingEmptySubsequences: false).compactMap({ Int($0) }),
           f.count == 18 {
            flags = f
        }
        stateTs = ts
        persist()
    }

    // ---- Mutaciones locales (guardan y publican) ----

    private func bumpAndSync() {
        stateTs = nowMillis()
        persist()
        pushState()
    }

    func prevHole() {
        auto = false
        holeIdx = (holeIdx + 17) % 18
        bumpAndSync()
    }

    func nextHole() {
        auto = false
        holeIdx = (holeIdx + 1) % 18
        bumpAndSync()
    }

    func toggleAuto() {
        auto.toggle()
        bumpAndSync()
    }

    func cyclePlayer() {
        if players.count > 1 {
            activePlayer = (activePlayer + 1) % players.count
            bumpAndSync()
        }
    }

    func changeStroke(_ delta: Int) {
        guard players.indices.contains(activePlayer) else { return }
        let v = players[activePlayer].strokes[holeIdx] + delta
        if (0...15).contains(v) {
            players[activePlayer].strokes[holeIdx] = v
            bumpAndSync()
        }
    }

    func distVal(_ m: Double) -> Int {
        Int((useMeters ? m : metersToYards(m)).rounded())
    }
}
