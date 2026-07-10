import Foundation
import WatchConnectivity

/// Sincronización iPhone ↔ Apple Watch (equivalente del Data Layer de Android):
/// se publica un snapshot completo de la ronda por WatchConnectivity y gana el
/// más nuevo por timestamp (last-write-wins). Las llaves son las mismas que en
/// el módulo Wear OS: names, scores, active, hole, units, auto, flags, clubs,
/// hcps, ts. Los jugadores se separan con "|" y las listas con ",".
enum SyncKeys {
    static let names = "names"
    static let scores = "scores"
    static let active = "active"
    static let hole = "hole"
    static let units = "units"
    static let auto = "auto"
    static let flags = "flags"
    static let clubs = "clubs"
    static let hcps = "hcps"
    static let ts = "ts"
    static let sep = "|"
}

final class RoundSync: NSObject, WCSessionDelegate {

    /// Llega un snapshot del otro dispositivo (ya en main thread).
    var onReceive: (([String: Any]) -> Void)?

    func activate() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    /// Publica el snapshot actual. updateApplicationContext entrega siempre el
    /// último estado aunque el otro lado esté dormido — igual que un DataItem.
    func push(_ snapshot: [String: Any]) {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        guard session.activationState == .activated else { return }
        try? session.updateApplicationContext(snapshot)
    }

    func session(_ session: WCSession,
                 activationDidCompleteWith activationState: WCSessionActivationState,
                 error: Error?) {
        // Al activar, aplica el último snapshot que dejó el otro dispositivo.
        let ctx = session.receivedApplicationContext
        if !ctx.isEmpty {
            DispatchQueue.main.async { self.onReceive?(ctx) }
        }
    }

    func session(_ session: WCSession, didReceiveApplicationContext ctx: [String: Any]) {
        DispatchQueue.main.async { self.onReceive?(ctx) }
    }

    #if os(iOS)
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }
    #endif
}

/// Milisegundos desde epoch (mismo reloj que System.currentTimeMillis()).
func nowMillis() -> Int64 {
    Int64(Date().timeIntervalSince1970 * 1000)
}
