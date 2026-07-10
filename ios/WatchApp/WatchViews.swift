import SwiftUI

private let mint = Color(argb: 0xFF7ADFA8)
private let dim = Color(argb: 0xFF9BB8A8)
private let amber = Color(argb: 0xFFF3B61F)

/// Raíz del reloj: página principal arriba, scorecard con swipe vertical
/// (equivalente al swipe-up del módulo Wear OS).
struct WatchRootView: View {
    @ObservedObject var model: WatchModel

    var body: some View {
        TabView {
            WatchMainView(model: model)
            WatchScorecardView(model: model)
        }
        .tabViewStyle(.verticalPage)
        .onAppear { model.requestLocation() }
    }
}

/// Pantalla principal: mini-mapa de fondo, hoyo/par/jugador arriba,
/// B/C/F + palo sugerido a la izquierda y contador de golpes abajo.
struct WatchMainView: View {
    @ObservedObject var model: WatchModel

    var body: some View {
        let hole = model.hole
        // Ajuste por pin del día (sincronizado desde el cel):
        // frente ≈ -depth/4 · fondo ≈ +depth/4
        let flag = model.flags.indices.contains(model.holeIdx) ? model.flags[model.holeIdx] : -1
        let pinShift: Double = flag == 0 ? -hole.greenDepthM / 4.0
            : (flag == 2 ? hole.greenDepthM / 4.0 : 0)
        let distM: Double? = {
            guard let lat = model.lat, let lng = model.lng else { return nil }
            return max(haversineMeters(lat, lng, hole.greenLat, hole.greenLng) + pinShift, 0)
        }()
        let half = hole.greenDepthM / 2.0
        let center = distM.map { model.distVal($0) }
        let front = distM.map { model.distVal(max($0 - half, 0)) }
        let back = distM.map { model.distVal($0 + half) }
        let player = model.players.indices.contains(model.activePlayer)
            ? model.players[model.activePlayer] : nil
        let strokeVal = player?.strokes[model.holeIdx] ?? 0

        ZStack {
            WatchHoleMap(hole: hole, userLat: model.lat, userLng: model.lng, flag: flag)

            VStack {
                // ---- Encabezado: hoyo + par + jugador activo ----
                VStack(spacing: 0) {
                    HStack(spacing: 6) {
                        Text("‹").font(.system(size: 15)).foregroundColor(dim)
                        Text("HOLE \(hole.number)")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(model.auto ? mint : .white)
                        Text("›").font(.system(size: 15)).foregroundColor(dim)
                    }
                    .onTapGesture { model.toggleAuto() }
                    Text("PAR \(hole.par)" + (model.auto ? " · GPS" : ""))
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(dim)
                    if model.players.count > 1, let player {
                        Text("▸ \(player.name)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(amber)
                            .onTapGesture { model.cyclePlayer() }
                    }
                }

                Spacer()

                // ---- Distancias (izquierda) · mapa a la derecha ----
                HStack {
                    VStack(spacing: -2) {
                        Text(back.map(String.init) ?? "–")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                        Text(center.map(String.init) ?? (model.granted ? "– –" : "GPS?"))
                            .font(.system(size: 42, weight: .black))
                            .foregroundColor(amber)
                            .minimumScaleFactor(0.6)
                            .lineLimit(1)
                        Text(front.map(String.init) ?? "–")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                        // Palo sugerido (siempre calculado en yardas).
                        if let distM, let player {
                            Text(clubForDistance(metersToYards(distM), player.clubs))
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(mint)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    Spacer().frame(maxWidth: .infinity)
                }

                Spacer()

                // ---- Golpes del jugador activo (sincroniza con el cel) ----
                HStack(spacing: 8) {
                    Button { model.changeStroke(-1) } label: {
                        Text("−")
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 42, height: 42)
                            .background(Circle().fill(Color.white.opacity(0.15)))
                    }
                    .buttonStyle(.plain)
                    VStack(spacing: 0) {
                        Text("\(strokeVal)")
                            .font(.system(size: 26, weight: .black))
                            .foregroundColor(.white)
                            .frame(width: 40)
                        Text("GOLPES")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundColor(dim)
                    }
                    Button { model.changeStroke(1) } label: {
                        Text("+")
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundColor(Color(argb: 0xFF06281A))
                            .frame(width: 42, height: 42)
                            .background(Circle().fill(mint))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 8)
            .padding(.bottom, 4)
        }
        // Swipe horizontal para cambiar de hoyo (como en Wear OS).
        .gesture(
            DragGesture(minimumDistance: 25)
                .onEnded { g in
                    if abs(g.translation.width) > abs(g.translation.height) {
                        if g.translation.width <= -45 { model.nextHole() }
                        else if g.translation.width >= 45 { model.prevHole() }
                    }
                }
        )
        .ignoresSafeArea(edges: .bottom)
    }
}

/// Scorecard de la ronda del jugador activo, con OUT/IN/TOTAL y vs par.
struct WatchScorecardView: View {
    @ObservedObject var model: WatchModel

    var body: some View {
        let p = model.players.indices.contains(model.activePlayer)
            ? model.players[model.activePlayer] : nil
        ScrollView {
            VStack(spacing: 4) {
                Text("SCORECARD" + (model.players.count > 1 ? " · ▸ \(p?.name ?? "")" : ""))
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(amber)
                    .onTapGesture { model.cyclePlayer() }

                ForEach(0..<18, id: \.self) { i in
                    let h = CourseData.holes[i]
                    let s = p?.strokes[i] ?? 0
                    HStack {
                        Text("H\(i + 1) · Par \(h.par)")
                            .font(.system(size: 13, weight: i == model.holeIdx ? .bold : .regular))
                            .foregroundColor(i == model.holeIdx ? mint : dim)
                        Spacer()
                        Text(s > 0 ? "\(s)" : "–")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(s > 0 ? .white : dim)
                    }
                    .padding(.horizontal, 6)
                }

                let strokes = p?.strokes ?? []
                let out = (0..<9).reduce(0) { $0 + (strokes.indices.contains($1) ? strokes[$1] : 0) }
                let inn = (9..<18).reduce(0) { $0 + (strokes.indices.contains($1) ? strokes[$1] : 0) }
                let rel = strokes.enumerated().reduce(0) { acc, e in
                    e.element > 0 ? acc + e.element - CourseData.holes[e.offset].par : acc
                }
                let relTxt = rel == 0 ? "E" : (rel > 0 ? "+\(rel)" : "\(rel)")
                VStack(spacing: 0) {
                    Text("OUT \(out) · IN \(inn)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(dim)
                    Text("TOTAL \(out + inn) · \(relTxt)")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(mint)
                }
                .padding(.top, 4)
            }
        }
        .background(Color.black)
    }
}
