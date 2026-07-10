import Foundation
import CoreLocation
import SwiftUI

// ---------------------------------------------------------------- Jugador

struct Player: Identifiable {
    let id = UUID()
    var name: String
    var strokes: [Int] = Array(repeating: 0, count: 18)
    var clubYards: [Int] = defaultClubYards
    /// Putts por hoyo (0 = sin registrar).
    var putts: [Int] = Array(repeating: 0, count: 18)
    /// Fairway por hoyo: -1 sin dato · 0 fallado · 1 acertado. Solo aplica en par 4/5.
    var fir: [Int] = Array(repeating: -1, count: 18)
    /// Handicap de juego (0-40) para Stableford; 0 = scratch.
    var hcp: Int = 0

    func total() -> Int { strokes.reduce(0, +) }
    func totalPutts() -> Int { putts.reduce(0, +) }

    /// Score vs par contando solo hoyos con golpes anotados.
    func relativeToPar() -> Int {
        var rel = 0
        for (i, s) in strokes.enumerated() where s > 0 {
            rel += s - CourseData.holes[i].par
        }
        return rel
    }

    func playedHoles() -> Int { strokes.filter { $0 > 0 }.count }

    /// Greens in regulation: llegaste al green en (par − 2) golpes.
    /// Derivado de golpes − putts, solo en hoyos con ambos registrados.
    func girCount() -> Int {
        var gir = 0
        for i in 0..<18 {
            let s = strokes[i], p = putts[i]
            if s > 0 && p > 0 && s - p <= CourseData.holes[i].par - 2 { gir += 1 }
        }
        return gir
    }

    /// Hoyos con golpes Y putts registrados (denominador del GIR%).
    func girTracked() -> Int {
        (0..<18).filter { strokes[$0] > 0 && putts[$0] > 0 }.count
    }

    /// Fairways: (acertados, intentados) en par 4/5 con dato.
    func firStats() -> (hit: Int, att: Int) {
        var hit = 0, att = 0
        for (i, v) in fir.enumerated() where CourseData.holes[i].par >= 4 && v >= 0 {
            att += 1
            if v == 1 { hit += 1 }
        }
        return (hit, att)
    }

    /// Golpes de ventaja que recibe en un hoyo según su stroke index (reparto clásico).
    func strokesReceived(_ holeIdx: Int) -> Int {
        let si = CourseData.holes[holeIdx].strokeIndex
        return hcp / 18 + (hcp % 18 >= si ? 1 : 0)
    }

    /// Puntos Stableford tradicionales con handicap: score neto = golpes −
    /// ventaja del hoyo. Doble bogey neto o peor 0 pts · bogey 1 · par 2 ·
    /// birdie 3 · eagle 4 · albatross 5. Solo hoyos con golpes anotados.
    func stablefordPoints() -> Int {
        var pts = 0
        for (i, s) in strokes.enumerated() where s > 0 {
            let net = s - strokesReceived(i)
            pts += max(CourseData.holes[i].par + 2 - net, 0)
        }
        return pts
    }
}

// ---------------------------------------------------------------- Historial

struct SavedRound: Identifiable {
    let id = UUID()
    let date: Date
    let entries: [Entry]

    struct Entry {
        let name: String
        let strokes: Int
        let relative: Int
        let holes: Int
        var putts: Int = 0
        var gir: Int = 0
        var girTracked: Int = 0
        var firHit: Int = 0
        var firAtt: Int = 0
        /// Golpes hoyo por hoyo (para el promedio por hoyo en Stats). Vacío en rondas viejas.
        var holeStrokes: [Int] = []
        /// Handicap y puntos Stableford con los que se cerró la ronda (0 = sin handicap).
        var hcp: Int = 0
        var points: Int = 0
    }
}

// ---------------------------------------------------------------- Constantes

/// El cambio automático de hoyo solo actúa a esta distancia de un tee (m).
private let autoDetectRadiusM = 150.0
/// Radio alrededor del centro del green para autocalibrar elevación (m).
private let greenCalibrationRadiusM = 22.0
/// Precisión GPS mínima para confiar en la altitud (m).
private let maxAccuracyForAltM = 15.0
/// Suavizado de la altitud del jugador.
private let altEmaAlpha = 0.25
/// Suavizado de la elevación aprendida de cada green.
private let greenEmaAlpha = 0.15

// ---------------------------------------------------------------- Modelo

/// Estado de la app + GPS. Port del GolfViewModel de Android (sin el respaldo
/// Firebase; la app iOS es 100% local, con persistencia en UserDefaults usando
/// las mismas llaves y formatos serializados que SharedPreferences en Android).
/// Sincroniza la ronda con el Apple Watch vía RoundSync (snapshot completo,
/// last-write-wins por timestamp — igual que el Data Layer en Android).
final class GolfModel: NSObject, ObservableObject, CLLocationManagerDelegate {

    private let prefs = UserDefaults.standard
    private let locationManager = CLLocationManager()
    private let sync = RoundSync()
    private var stateTs: Int64 = 0

    // --- GPS ---
    @Published private(set) var userLat: Double?
    @Published private(set) var userLng: Double?
    @Published private(set) var gpsAccuracyM: Double?
    @Published var hasLocationPermission = false

    // --- Altitud (para "plays like") ---
    /// Altitud del jugador, suavizada con media móvil exponencial.
    @Published private(set) var userAltM: Double?
    /// Elevación de cada green, autocalibrada al pisarlo con GPS activo.
    /// .nan = sin dato aún.
    private var greenElevM = [Double](repeating: .nan, count: 18)
    @Published private(set) var calibratedGreens = 0

    // --- Hoyo actual (siempre arranca en el 1) ---
    @Published private(set) var currentHoleIndex = 0
    @Published private(set) var autoDetect = false

    // --- Jugadores (máx 5) ---
    @Published var players: [Player] = []
    @Published var activePlayerIndex = 0

    // --- Pin del día por hoyo: -1 sin dato · 0 rojo (frente) · 1 blanco (medio) · 2 azul (fondo) ---
    // Regla del club: las banderas rotan rojo -> blanco -> azul hoyo tras hoyo,
    // así que elegir la de un hoyo determina todo el campo.
    @Published var flags = [Int](repeating: -1, count: 18)

    // --- Historial de rondas ---
    @Published var history: [SavedRound] = []

    // --- Ajustes ---
    @Published var units: Units = .yards
    @Published var themeMode: ThemeMode = .system

    override init() {
        super.init()
        loadState()
        if players.isEmpty { players.append(Player(name: "Player 1")) }
        stateTs = Int64(prefs.object(forKey: "stateTs") as? Double ?? 0)

        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.distanceFilter = kCLDistanceFilterNone
        locationManager.activityType = .fitness

        // Sincronización con el reloj: trae el último snapshot y escucha cambios.
        sync.onReceive = { [weak self] dict in self?.applyIncoming(dict) }
        sync.activate()
    }

    var currentHole: Hole { CourseData.holes[currentHoleIndex] }

    // --- Sincronización con el reloj (snapshot completo de la ronda) ---

    /// Marca cambio local, guarda y publica el estado al reloj.
    private func syncOut() {
        stateTs = nowMillis()
        saveState()
        pushState()
    }

    /// Publica el estado actual (nombres, golpes, activo, hoyo, unidades…).
    private func pushState() {
        sync.push([
            SyncKeys.names: players.map(\.name).joined(separator: SyncKeys.sep),
            SyncKeys.scores: players.map { $0.strokes.map(String.init).joined(separator: ",") }
                .joined(separator: SyncKeys.sep),
            SyncKeys.active: activePlayerIndex,
            SyncKeys.hole: currentHoleIndex,
            SyncKeys.units: units.rawValue,
            SyncKeys.auto: autoDetect,
            SyncKeys.flags: flags.map(String.init).joined(separator: ","),
            SyncKeys.clubs: players.map { $0.clubYards.map(String.init).joined(separator: ",") }
                .joined(separator: SyncKeys.sep),
            SyncKeys.hcps: players.map { String($0.hcp) }.joined(separator: ","),
            SyncKeys.ts: stateTs
        ])
    }

    /// Aplica un snapshot entrante si es más nuevo (last-write-wins).
    private func applyIncoming(_ dict: [String: Any]) {
        let ts = (dict[SyncKeys.ts] as? NSNumber)?.int64Value ?? 0
        if ts <= stateTs { return }
        guard let scoresStr = dict[SyncKeys.scores] as? String else { return }
        // Actualiza golpes y yardas de palos de los jugadores existentes
        // (el reloj no agrega ni quita jugadores en el teléfono).
        let scores = scoresStr.components(separatedBy: SyncKeys.sep)
        let clubsIn = (dict[SyncKeys.clubs] as? String)?.components(separatedBy: SyncKeys.sep)
        for i in players.indices {
            if i < scores.count {
                let list = scores[i].split(separator: ",").compactMap { Int($0) }
                if list.count == 18 { players[i].strokes = list }
            }
            if let clubsIn, i < clubsIn.count {
                let list = clubsIn[i].split(separator: ",").compactMap { Int($0) }
                if list.count == clubNames.count { players[i].clubYards = list }
            }
        }
        if let a = dict[SyncKeys.active] as? Int, players.indices.contains(a) {
            activePlayerIndex = a
        }
        if let h = dict[SyncKeys.hole] as? Int, (0..<18).contains(h) {
            currentHoleIndex = h
        }
        if let u = dict[SyncKeys.units] as? String, let parsed = Units(rawValue: u) {
            units = parsed
        }
        if let auto = dict[SyncKeys.auto] as? Bool { autoDetect = auto }
        if let f = (dict[SyncKeys.flags] as? String)?
            .split(separator: ",", omittingEmptySubsequences: false).compactMap({ Int($0) }),
           f.count == 18 {
            flags = f
        }
        if let hcps = (dict[SyncKeys.hcps] as? String)?.split(separator: ",").compactMap({ Int($0) }) {
            for (i, v) in hcps.enumerated() where players.indices.contains(i) {
                players[i].hcp = min(max(v, 0), 40)
            }
        }
        stateTs = ts
        saveState()
    }

    // --- Permiso y updates de ubicación ---

    func requestLocation() {
        switch locationManager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            hasLocationPermission = true
            locationManager.startUpdatingLocation()
        case .notDetermined:
            locationManager.requestWhenInUseAuthorization()
        default:
            hasLocationPermission = false
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let ok = manager.authorizationStatus == .authorizedWhenInUse ||
                 manager.authorizationStatus == .authorizedAlways
        hasLocationPermission = ok
        if ok { manager.startUpdatingLocation() }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last, loc.horizontalAccuracy >= 0 else { return }
        onLocation(
            lat: loc.coordinate.latitude,
            lng: loc.coordinate.longitude,
            accuracy: loc.horizontalAccuracy,
            altitudeM: loc.verticalAccuracy > 0 ? loc.altitude : nil
        )
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Sin señal momentánea: se conserva la última posición conocida.
    }

    func onLocation(lat: Double, lng: Double, accuracy: Double, altitudeM: Double? = nil) {
        userLat = lat
        userLng = lng
        gpsAccuracyM = accuracy

        if let alt = altitudeM, accuracy <= maxAccuracyForAltM {
            // EMA: suaviza el ruido vertical del GPS (±5-10 m por lectura).
            userAltM = userAltM.map { $0 * (1 - altEmaAlpha) + alt * altEmaAlpha } ?? alt
            calibrateGreenIfOnIt(lat: lat, lng: lng)
        }

        if autoDetect {
            let nearest = CourseData.nearestHoleByTee(lat: lat, lng: lng)
            let dist = haversineMeters(lat, lng, nearest.teeLat, nearest.teeLng)
            // Solo cambia cuando estás realmente junto a un tee — evita saltos
            // cuando andas lejos del campo.
            if dist <= autoDetectRadiusM && currentHoleIndex != nearest.number - 1 {
                currentHoleIndex = nearest.number - 1
                syncOut()
            }
        }
    }

    /// Si el jugador está parado sobre un green, aprende su elevación.
    private func calibrateGreenIfOnIt(lat: Double, lng: Double) {
        guard let alt = userAltM else { return }
        for (i, h) in CourseData.holes.enumerated()
        where haversineMeters(lat, lng, h.greenLat, h.greenLng) <= greenCalibrationRadiusM {
            greenElevM[i] = greenElevM[i].isNaN ? alt
                : greenElevM[i] * (1 - greenEmaAlpha) + alt * greenEmaAlpha
            let count = greenElevM.filter { !$0.isNaN }.count
            if count != calibratedGreens { calibratedGreens = count }
            saveElevations()
        }
    }

    func distanceToGreenMeters() -> Double? {
        guard let lat = userLat, let lng = userLng else { return nil }
        let h = currentHole
        return haversineMeters(lat, lng, h.greenLat, h.greenLng)
    }

    /// Diferencia de elevación jugador → green del hoyo actual, en metros.
    /// Positivo = green cuesta arriba. nil si aún no hay calibración o altitud.
    func elevationDeltaM() -> Double? {
        guard let alt = userAltM else { return nil }
        let g = greenElevM[currentHoleIndex]
        if g.isNaN { return nil }
        return g - alt
    }

    /// Distancia "plays like": distancia real + ajuste por elevación.
    /// Cada metro de subida/bajada suma/resta ~1 m efectivo al tiro.
    /// Se ignoran deltas menores a 2 m (ruido GPS).
    func playsLikeMeters() -> Double? {
        guard let dist = distanceToGreenMeters(), let delta = elevationDeltaM() else { return nil }
        if abs(delta) < 2.0 { return nil }
        return max(dist + delta, 0)
    }

    // --- Medición de golpes (aprende tus distancias reales por palo) ---

    /// Posición de la bola marcada antes de pegar (nil = sin golpe en curso).
    @Published private(set) var shotLat: Double?
    @Published private(set) var shotLng: Double?
    /// Palo con el que se pegó el golpe marcado (-1 = sin golpe en curso).
    @Published private(set) var shotClubIdx = -1

    /// Marca la posición actual como el punto donde se pega el golpe.
    func markShot(clubIdx: Int) {
        guard let lat = userLat, let lng = userLng else { return }
        shotLat = lat
        shotLng = lng
        shotClubIdx = min(max(clubIdx, 0), clubNames.count - 1)
    }

    /// Corrige el palo del golpe en curso (por si el sugerido no fue el que usaste).
    func changeShotClub(_ delta: Int) {
        if shotClubIdx >= 0 {
            shotClubIdx = (shotClubIdx + delta + clubNames.count) % clubNames.count
        }
    }

    /// Distancia de la marca del golpe a tu posición actual, en metros.
    func shotDistanceM() -> Double? {
        guard let sl = shotLat, let sg = shotLng,
              let lat = userLat, let lng = userLng else { return nil }
        return haversineMeters(sl, sg, lat, lng)
    }

    func cancelShot() {
        shotLat = nil
        shotLng = nil
        shotClubIdx = -1
    }

    /// Guarda el golpe medido como distancia del palo del jugador activo:
    /// media móvil (70% lo que ya sabía + 30% este golpe). Devuelve las yardas
    /// medidas, o nil si el golpe no es creíble (< 30 yd o > 350 yd).
    @discardableResult
    func saveShotToClub() -> Int? {
        guard let m = shotDistanceM(), players.indices.contains(activePlayerIndex) else { return nil }
        let idx = shotClubIdx
        guard clubNames.indices.contains(idx) else { return nil }
        let yd = Int(metersToYards(m).rounded())
        if yd < 30 || yd > 350 { return nil }
        let old = players[activePlayerIndex].clubYards[idx]
        players[activePlayerIndex].clubYards[idx] =
            min(max(Int((Double(old) * 0.7 + Double(yd) * 0.3).rounded()), 30), 350)
        cancelShot()
        syncOut()
        return yd
    }

    // --- Handicap index (WHS) ---

    /// Handicap index estilo WHS de un jugador (por nombre): diferencial de
    /// cada ronda completa (18 hoyos) = (score − rating) × 113 / slope, y se
    /// promedian los mejores según cuántas rondas hay (tabla WHS).
    /// nil con menos de 3 rondas.
    func handicapIndex(_ name: String) -> Double? {
        let diffs = history.compactMap { r in
            r.entries.first { $0.name == name && $0.holes == 18 }
        }.prefix(20).map {
            (Double($0.strokes) - CourseData.courseRating) * 113.0 / Double(CourseData.slopeRating)
        }
        if diffs.count < 3 { return nil }
        let (use, adj): (Int, Double)
        switch diffs.count {
        case 3: (use, adj) = (1, -2.0)
        case 4: (use, adj) = (1, -1.0)
        case 5: (use, adj) = (1, 0.0)
        case 6: (use, adj) = (2, -1.0)
        case 7, 8: (use, adj) = (2, 0.0)
        case 9...11: (use, adj) = (3, 0.0)
        case 12...14: (use, adj) = (4, 0.0)
        case 15, 16: (use, adj) = (5, 0.0)
        case 17, 18: (use, adj) = (6, 0.0)
        case 19: (use, adj) = (7, 0.0)
        default: (use, adj) = (8, 0.0)
        }
        let best = diffs.sorted().prefix(use)
        let avg = best.reduce(0, +) / Double(best.count) + adj
        return (avg * 10).rounded() / 10
    }

    /// Rondas completas (18 hoyos) de un jugador que cuentan para su handicap.
    func handicapRounds(_ name: String) -> Int {
        history.filter { r in r.entries.contains { $0.name == name && $0.holes == 18 } }.count
    }

    /// Borra las elevaciones aprendidas (por si una calibración salió mal).
    func resetElevations() {
        greenElevM = [Double](repeating: .nan, count: 18)
        calibratedGreens = 0
        saveElevations()
    }

    private func saveElevations() {
        let s = greenElevM.map { $0.isNaN ? "" : String(format: "%.1f", $0) }.joined(separator: ",")
        prefs.set(s, forKey: "greenElev")
    }

    // --- Hoyo ---

    func nextHole() {
        autoDetect = false
        currentHoleIndex = (currentHoleIndex + 1) % 18
        syncOut()
    }

    func previousHole() {
        autoDetect = false
        currentHoleIndex = (currentHoleIndex + 17) % 18
        syncOut()
    }

    func toggleAutoDetect() {
        autoDetect.toggle()
        if autoDetect, let lat = userLat, let lng = userLng {
            let nearest = CourseData.nearestHoleByTee(lat: lat, lng: lng)
            let dist = haversineMeters(lat, lng, nearest.teeLat, nearest.teeLng)
            if dist <= autoDetectRadiusM { currentHoleIndex = nearest.number - 1 }
        }
        syncOut()
    }

    // --- Golpes ---

    func addStroke(_ playerIdx: Int, holeIdx: Int? = nil) {
        let h = holeIdx ?? currentHoleIndex
        guard players.indices.contains(playerIdx) else { return }
        if players[playerIdx].strokes[h] < 15 { players[playerIdx].strokes[h] += 1 }
        syncOut()
    }

    func removeStroke(_ playerIdx: Int, holeIdx: Int? = nil) {
        let h = holeIdx ?? currentHoleIndex
        guard players.indices.contains(playerIdx) else { return }
        if players[playerIdx].strokes[h] > 0 { players[playerIdx].strokes[h] -= 1 }
        syncOut()
    }

    func setActivePlayer(_ index: Int) {
        if players.indices.contains(index) && index != activePlayerIndex {
            activePlayerIndex = index
            syncOut()
        }
    }

    // --- Putts y fairways ---

    func addPutt(_ playerIdx: Int, holeIdx: Int? = nil) {
        let h = holeIdx ?? currentHoleIndex
        guard players.indices.contains(playerIdx) else { return }
        if players[playerIdx].putts[h] < 9 { players[playerIdx].putts[h] += 1 }
        saveState()
    }

    func removePutt(_ playerIdx: Int, holeIdx: Int? = nil) {
        let h = holeIdx ?? currentHoleIndex
        guard players.indices.contains(playerIdx) else { return }
        if players[playerIdx].putts[h] > 0 { players[playerIdx].putts[h] -= 1 }
        saveState()
    }

    /// Cicla el fairway: sin dato -> acertado -> fallado -> sin dato.
    func cycleFir(_ playerIdx: Int, holeIdx: Int? = nil) {
        let h = holeIdx ?? currentHoleIndex
        guard players.indices.contains(playerIdx) else { return }
        let v = players[playerIdx].fir[h]
        players[playerIdx].fir[h] = v == -1 ? 1 : (v == 1 ? 0 : -1)
        saveState()
    }

    // --- Juegos ---

    /// Skins con acarreo: en cada hoyo donde TODOS los jugadores registraron
    /// golpes, el score único más bajo gana el pozo (1 skin + acarreados).
    /// Empate en el más bajo -> el pozo se acarrea al siguiente hoyo completo.
    func skinsStandings() -> (skins: [Int], pot: Int) {
        var won = [Int](repeating: 0, count: players.count)
        var pot = 0
        for h in 0..<18 {
            let scores = players.map { $0.strokes[h] }
            if scores.contains(0) { continue } // hoyo incompleto: no cuenta ni acarrea
            pot += 1
            let minScore = scores.min()!
            let winners = scores.enumerated().filter { $0.element == minScore }
            if winners.count == 1 {
                won[winners[0].offset] += pot
                pot = 0
            } // empate: pot se acarrea
        }
        return (won, pot)
    }

    /// Match play clásico para exactamente 2 jugadores.
    /// Devuelve el estado legible ("Andres 2 UP thru 7", "All square thru 7",
    /// "Andres gana 3&2") o nil si no aplica.
    func matchPlayStatus() -> String? {
        guard players.count == 2 else { return nil }
        let a = players[0], b = players[1]
        var diff = 0 // >0 = jugador A arriba
        var thru = 0
        var decidedAt = -1
        for h in 0..<18 {
            if a.strokes[h] == 0 || b.strokes[h] == 0 { continue }
            thru = h + 1
            if a.strokes[h] < b.strokes[h] { diff += 1 }
            else if b.strokes[h] < a.strokes[h] { diff -= 1 }
            let remaining = 18 - (h + 1)
            if decidedAt < 0 && abs(diff) > remaining { decidedAt = h + 1 }
        }
        if thru == 0 { return nil }
        let leader = diff > 0 ? a.name : b.name
        let up = abs(diff)
        if decidedAt > 0 { return "\(leader) gana \(up)&\(18 - decidedAt)" }
        if diff == 0 { return "All square · thru \(thru)" }
        if thru == 18 { return "\(leader) gana \(up) UP" }
        return "\(leader) \(up) UP · thru \(thru)"
    }

    // --- Jugadores ---

    func addPlayer() {
        if players.count < 5 {
            players.append(Player(name: "Player \(players.count + 1)"))
            syncOut()
        }
    }

    func removePlayer(_ index: Int) {
        if players.count > 1 && players.indices.contains(index) {
            players.remove(at: index)
            if activePlayerIndex >= players.count { activePlayerIndex = players.count - 1 }
            syncOut()
        }
    }

    func setFlagRotation(holeIdx: Int, color: Int) {
        for i in 0..<18 {
            flags[i] = (((color + (i - holeIdx)) % 3) + 3) % 3
        }
        syncOut()
    }

    func clearFlags() {
        flags = [Int](repeating: -1, count: 18)
        syncOut()
    }

    func adjustClub(_ playerIdx: Int, _ clubIdx: Int, _ delta: Int) {
        guard players.indices.contains(playerIdx), clubNames.indices.contains(clubIdx) else { return }
        players[playerIdx].clubYards[clubIdx] =
            min(max(players[playerIdx].clubYards[clubIdx] + delta, 30), 350)
        syncOut()
    }

    func resetClubs(_ playerIdx: Int) {
        guard players.indices.contains(playerIdx) else { return }
        players[playerIdx].clubYards = defaultClubYards
        syncOut()
    }

    func renamePlayer(_ index: Int, _ newName: String) {
        let trimmed = newName.trimmingCharacters(in: .whitespaces)
        guard players.indices.contains(index), !trimmed.isEmpty else { return }
        players[index].name = String(trimmed.prefix(14))
        syncOut()
    }

    /// Ajusta el handicap de juego de un jugador (para Stableford).
    func adjustHandicap(_ index: Int, _ delta: Int) {
        guard players.indices.contains(index) else { return }
        players[index].hcp = min(max(players[index].hcp + delta, 0), 40)
        syncOut()
    }

    /// Guarda la ronda actual al historial (si hay golpes) y arranca de cero en el hoyo 1.
    func finishRound() {
        if players.contains(where: { $0.total() > 0 }) {
            let entries = players.map { p -> SavedRound.Entry in
                let (fh, fa) = p.firStats()
                return SavedRound.Entry(
                    name: p.name, strokes: p.total(), relative: p.relativeToPar(),
                    holes: p.playedHoles(), putts: p.totalPutts(),
                    gir: p.girCount(), girTracked: p.girTracked(),
                    firHit: fh, firAtt: fa,
                    holeStrokes: p.strokes,
                    hcp: p.hcp, points: p.stablefordPoints()
                )
            }
            history.insert(SavedRound(date: Date(), entries: entries), at: 0)
            while history.count > 30 { history.removeLast() }
        }
        for i in players.indices {
            players[i].strokes = Array(repeating: 0, count: 18)
            players[i].putts = Array(repeating: 0, count: 18)
            players[i].fir = Array(repeating: -1, count: 18)
        }
        flags = [Int](repeating: -1, count: 18)
        currentHoleIndex = 0
        autoDetect = false
        syncOut()
    }

    /// Limpia los golpes actuales sin guardar al historial.
    func clearStrokes() {
        for i in players.indices {
            players[i].strokes = Array(repeating: 0, count: 18)
            players[i].putts = Array(repeating: 0, count: 18)
            players[i].fir = Array(repeating: -1, count: 18)
        }
        flags = [Int](repeating: -1, count: 18)
        currentHoleIndex = 0
        syncOut()
    }

    func deleteRound(_ index: Int) {
        if history.indices.contains(index) {
            history.remove(at: index)
            saveState()
        }
    }

    func setUnitsAndSave(_ u: Units) { units = u; syncOut() }
    func setThemeAndSave(_ t: ThemeMode) { themeMode = t; saveState() }

    // --- Persistencia ---
    // Mismas llaves y formatos serializados que SharedPreferences en Android
    // (nombres con "|", listas con ",", historial en JSON con llaves cortas).

    func saveState() {
        var historyJson: [[String: Any]] = []
        for r in history {
            var entries: [[String: Any]] = []
            for e in r.entries {
                var o: [String: Any] = [
                    "n": e.name, "s": e.strokes, "r": e.relative, "h": e.holes,
                    "p": e.putts, "g": e.gir, "gt": e.girTracked,
                    "fh": e.firHit, "fa": e.firAtt,
                    "hc": e.hcp, "pt": e.points
                ]
                if e.holeStrokes.count == 18 {
                    o["hs"] = e.holeStrokes.map(String.init).joined(separator: ",")
                }
                entries.append(o)
            }
            historyJson.append(["d": Int64(r.date.timeIntervalSince1970 * 1000), "e": entries])
        }
        let historyStr = (try? JSONSerialization.data(withJSONObject: historyJson))
            .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"

        prefs.set(players.map(\.name).joined(separator: "|"), forKey: "names")
        prefs.set(players.map { $0.strokes.map(String.init).joined(separator: ",") }.joined(separator: "|"), forKey: "scores")
        prefs.set(players.map { $0.clubYards.map(String.init).joined(separator: ",") }.joined(separator: "|"), forKey: "clubs")
        prefs.set(players.map { $0.putts.map(String.init).joined(separator: ",") }.joined(separator: "|"), forKey: "putts")
        prefs.set(players.map { $0.fir.map(String.init).joined(separator: ",") }.joined(separator: "|"), forKey: "firs")
        prefs.set(players.map { String($0.hcp) }.joined(separator: ","), forKey: "hcps")
        prefs.set(flags.map(String.init).joined(separator: ","), forKey: "flags")
        prefs.set(units.rawValue, forKey: "units")
        prefs.set(themeMode.rawValue, forKey: "theme")
        prefs.set(historyStr, forKey: "history")
        prefs.set(Double(stateTs), forKey: "stateTs")
    }

    private func loadState() {
        units = Units(rawValue: prefs.string(forKey: "units") ?? "YARDS") ?? .yards
        themeMode = ThemeMode(rawValue: prefs.string(forKey: "theme") ?? "SYSTEM") ?? .system

        if let f = prefs.string(forKey: "flags")?.split(separator: ",", omittingEmptySubsequences: false).compactMap({ Int($0) }),
           f.count == 18 {
            flags = f
        }

        if let elev = prefs.string(forKey: "greenElev")?
            .split(separator: ",", omittingEmptySubsequences: false).map(String.init),
           elev.count == 18 {
            for (i, s) in elev.enumerated() { greenElevM[i] = Double(s) ?? .nan }
        }
        calibratedGreens = greenElevM.filter { !$0.isNaN }.count

        if let data = prefs.string(forKey: "history")?.data(using: .utf8),
           let arr = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] {
            for o in arr {
                guard let d = o["d"] as? Double,
                      let es = o["e"] as? [[String: Any]] else { continue }
                let entries = es.compactMap { e -> SavedRound.Entry? in
                    guard let n = e["n"] as? String,
                          let s = e["s"] as? Int, let r = e["r"] as? Int,
                          let h = e["h"] as? Int else { return nil }
                    let hsStr = e["hs"] as? String ?? ""
                    let hs = hsStr.split(separator: ",").compactMap { Int($0) }
                    return SavedRound.Entry(
                        name: n, strokes: s, relative: r, holes: h,
                        putts: e["p"] as? Int ?? 0,
                        gir: e["g"] as? Int ?? 0,
                        girTracked: e["gt"] as? Int ?? 0,
                        firHit: e["fh"] as? Int ?? 0,
                        firAtt: e["fa"] as? Int ?? 0,
                        holeStrokes: hs.count == 18 ? hs : [],
                        hcp: e["hc"] as? Int ?? 0,
                        points: e["pt"] as? Int ?? 0
                    )
                }
                history.append(SavedRound(date: Date(timeIntervalSince1970: d / 1000), entries: entries))
            }
        }

        guard let namesStr = prefs.string(forKey: "names") else { return }
        let names = namesStr.split(separator: "|").map(String.init).filter { !$0.isEmpty }
        let scores = prefs.string(forKey: "scores")?
            .split(separator: "|", omittingEmptySubsequences: false).map(String.init) ?? []
        let clubs = prefs.string(forKey: "clubs")?
            .split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        let puttsAll = prefs.string(forKey: "putts")?
            .split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        let firsAll = prefs.string(forKey: "firs")?
            .split(separator: "|", omittingEmptySubsequences: false).map(String.init)

        func intList(_ s: String?, count: Int) -> [Int]? {
            guard let s else { return nil }
            let list = s.split(separator: ",", omittingEmptySubsequences: false).compactMap { Int($0) }
            return list.count == count ? list : nil
        }

        for (i, name) in names.enumerated() where players.count < 5 {
            players.append(Player(
                name: name,
                strokes: intList(i < scores.count ? scores[i] : nil, count: 18) ?? Array(repeating: 0, count: 18),
                clubYards: intList(clubs?.indices.contains(i) == true ? clubs![i] : nil, count: clubNames.count) ?? defaultClubYards,
                putts: intList(puttsAll?.indices.contains(i) == true ? puttsAll![i] : nil, count: 18) ?? Array(repeating: 0, count: 18),
                fir: intList(firsAll?.indices.contains(i) == true ? firsAll![i] : nil, count: 18) ?? Array(repeating: -1, count: 18)
            ))
        }
        if let hcps = prefs.string(forKey: "hcps")?.split(separator: ",").compactMap({ Int($0) }) {
            for (i, v) in hcps.enumerated() where players.indices.contains(i) {
                players[i].hcp = min(max(v, 0), 40)
            }
        }
    }
}
