import SwiftUI

// ---------------------------------------------------------------- Range (GPS)

struct RangeScreen: View {
    @ObservedObject var model: GolfModel
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        let hole = model.currentHole
        let distM = model.distanceToGreenMeters()
        let yards = model.units == .yards

        // El pin del día desplaza el objetivo proporcionalmente a la
        // profundidad de este green: rojo (frente) ≈ -depth/4 · azul (fondo) ≈ +depth/4
        let flag = model.flags[model.currentHoleIndex]
        let pinShiftM = hole.greenDepthM / 4.0
        let flagOffsetM: Double = flag == 0 ? -pinShiftM : (flag == 2 ? pinShiftM : 0)
        let distAdjM = distM.map { max($0 + flagOffsetM, 0) }

        let distValue: Int? = distAdjM.map {
            Int((yards ? metersToYards($0) : $0).rounded())
        }
        let unitShort = yards ? "yd" : "m"
        let refDist = Int((yards ? metersToYards(hole.referenceMeters) : hole.referenceMeters).rounded())

        // "Plays like": distancia efectiva por elevación (autocalibrada en greens).
        let elevDeltaM = model.elevationDeltaM()
        let playsLikeAdjM = model.playsLikeMeters().map { max($0 + flagOffsetM, 0) }

        // El palo sugerido usa la distancia efectiva cuando existe.
        let clubYards = (playsLikeAdjM ?? distAdjM).map { metersToYards($0) }

        ScrollView {
            VStack(alignment: .center, spacing: 0) {
                Spacer().frame(height: 14)
                HStack(spacing: 10) {
                    Text("⛳ HOYO \(hole.number)")
                        .font(.system(size: 28, weight: .black))
                        .foregroundColor(pal.primary)
                    Pill(text: "PAR \(hole.par)", bg: pal.primary, fg: pal.onPrimary)
                    if model.autoDetect {
                        Pill(text: "AUTO", bg: pal.secondaryContainer, fg: pal.onSecondaryContainer)
                    }
                }
                Text("Tee → green: \(refDist) \(unitShort)")
                    .font(.system(size: 14))
                    .foregroundColor(pal.onSurfaceVariant)
                Spacer().frame(height: 10)
                FlagChip(flag: flag) {
                    let next = flag == -1 ? 0 : (flag == 0 ? 1 : (flag == 1 ? 2 : -1))
                    if next == -1 {
                        model.clearFlags()
                    } else {
                        model.setFlagRotation(holeIdx: model.currentHoleIndex, color: next)
                    }
                }
                Spacer().frame(height: 10)

                if !model.hasLocationPermission {
                    permissionPrompt(pal)
                } else {
                    distanceBlock(pal, hole: hole, flag: flag, yards: yards,
                                  distValue: distValue, unitShort: unitShort,
                                  distAdjM: distAdjM, elevDeltaM: elevDeltaM,
                                  playsLikeAdjM: playsLikeAdjM)
                }

                Spacer().frame(height: 14)
                suggestedClubCard(pal, clubYards: clubYards)
                Spacer().frame(height: 12)

                if model.hasLocationPermission {
                    ShotMeasureCard(model: model, suggestedYards: clubYards)
                    Spacer().frame(height: 12)
                }

                HoleMapCard(hole: hole, userLat: model.userLat, userLng: model.userLng,
                            units: model.units, flag: flag)
                Spacer().frame(height: 12)

                holeButtons(pal)
                Spacer().frame(height: 8)
                Text("◀ Desliza para cambiar de hoyo ▶")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(pal.onSurfaceVariant)

                Spacer().frame(height: 16)
                Text("GOLPES · HOYO \(hole.number)")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(pal.onBackground)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Spacer().frame(height: 6)

                VStack(spacing: 8) {
                    ForEach(Array(model.players.enumerated()), id: \.element.id) { i, player in
                        StrokeRow(
                            name: player.name,
                            strokes: player.strokes[model.currentHoleIndex],
                            par: hole.par,
                            onAdd: {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                model.addStroke(i)
                            },
                            onRemove: { model.removeStroke(i) },
                            putts: player.putts[model.currentHoleIndex],
                            onPuttAdd: { model.addPutt(i) },
                            onPuttRemove: { model.removePutt(i) },
                            fir: player.fir[model.currentHoleIndex],
                            onFirCycle: { model.cycleFir(i) },
                            showFir: hole.par >= 4
                        )
                    }
                }
                Spacer().frame(height: 16)
            }
            .padding(.horizontal, 16)
        }
        .background(pal.background)
        // Swipe horizontal para cambiar de hoyo (como en Android).
        .simultaneousGesture(
            DragGesture(minimumDistance: 40)
                .onEnded { g in
                    if abs(g.translation.width) > abs(g.translation.height) {
                        if g.translation.width <= -70 { model.nextHole() }
                        else if g.translation.width >= 70 { model.previousHole() }
                    }
                }
        )
    }

    @ViewBuilder
    private func permissionPrompt(_ pal: Pal) -> some View {
        Spacer().frame(height: 24)
        Text("Se necesita permiso de ubicación para medir tu distancia al green.")
            .multilineTextAlignment(.center)
            .foregroundColor(pal.onSurfaceVariant)
        Spacer().frame(height: 12)
        Button {
            model.requestLocation()
        } label: {
            Text("Activar GPS")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(pal.onPrimary)
                .padding(.horizontal, 24)
                .padding(.vertical, 12)
                .background(Capsule().fill(pal.primary))
        }
        .buttonStyle(.plain)
        Spacer().frame(height: 24)
    }

    @ViewBuilder
    private func distanceBlock(
        _ pal: Pal, hole: Hole, flag: Int, yards: Bool,
        distValue: Int?, unitShort: String,
        distAdjM: Double?, elevDeltaM: Double?, playsLikeAdjM: Double?
    ) -> some View {
        HStack(alignment: .bottom, spacing: 0) {
            Text(distValue.map(String.init) ?? "– – –")
                .font(.system(size: 104, weight: .black))
                .foregroundColor(pal.onBackground)
                .minimumScaleFactor(0.5)
                .lineLimit(1)
            Text(" \(unitShort)")
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(pal.primary)
                .padding(.bottom, 18)
        }
        Text({
            switch flag {
            case 0: return "al pin ROJO (frente)"
            case 1: return "al pin BLANCO (medio)"
            case 2: return "al pin AZUL (fondo)"
            default: return "al centro del green"
            }
        }())
        .font(.system(size: 17, weight: .semibold))
        .foregroundColor({
            switch flag {
            case 0: return Color(argb: 0xFFE85D4A)
            case 2: return Color(argb: 0xFF5AB0FF)
            default: return pal.primary
            }
        }())
        Text(model.gpsAccuracyM.map { "🛰️ Precisión GPS: ±\(Int($0.rounded())) m" }
             ?? "🛰️ Buscando señal GPS…")
            .font(.system(size: 12))
            .foregroundColor(pal.onSurfaceVariant)

        // "Plays like" por elevación (aparece tras pisar este green una vez)
        if let playsLikeAdjM, let elevDeltaM {
            let plV = Int((yards ? metersToYards(playsLikeAdjM) : playsLikeAdjM).rounded())
            let up = elevDeltaM > 0
            let color = up ? Color(argb: 0xFFE85D4A) : Color(argb: 0xFF5AB0FF)
            Spacer().frame(height: 4)
            HStack(spacing: 0) {
                Text("JUEGA COMO ")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(pal.onSurfaceVariant)
                Text("\(plV) \(unitShort)")
                    .font(.system(size: 22, weight: .black))
                    .foregroundColor(color)
                Text(up ? "  ▲ +\(Int(elevDeltaM.rounded())) m" : "  ▼ \(Int(elevDeltaM.rounded())) m")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(color)
            }
        }

        if let distAdjM {
            // Front/back usando la distancia ajustada al pin del día
            let half = hole.greenDepthM / 2.0
            let fM = max(distAdjM - half, 0)
            let bM = distAdjM + half
            let fV = Int((yards ? metersToYards(fM) : fM).rounded())
            let bV = Int((yards ? metersToYards(bM) : bM).rounded())
            Spacer().frame(height: 4)
            HStack(spacing: 18) {
                FcbLabel(letter: "F", value: fV)
                FcbLabel(letter: "C", value: distValue ?? 0)
                FcbLabel(letter: "B", value: bV)
            }
        }
    }

    @ViewBuilder
    private func suggestedClubCard(_ pal: Pal, clubYards: Double?) -> some View {
        let activeP = model.players.indices.contains(model.activePlayerIndex)
            ? model.players[model.activePlayerIndex] : model.players[0]
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                Text("🏌️").font(.system(size: 30))
                VStack(alignment: .leading, spacing: 1) {
                    Text("PALO SUGERIDO · \(activeP.name.uppercased().prefix(12))")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(pal.onSurfaceVariant)
                    Text(clubYards.map { clubForDistance($0, activeP.clubYards) } ?? "Esperando GPS")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(pal.primary)
                }
            }
            if model.players.count > 1 {
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
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 20).fill(pal.surfaceVariant))
    }

    @ViewBuilder
    private func holeButtons(_ pal: Pal) -> some View {
        HStack(spacing: 8) {
            navButton("◀ Ant.", pal, highlighted: false) { model.previousHole() }
            navButton("AUTO", pal, highlighted: model.autoDetect) { model.toggleAutoDetect() }
            navButton("Sig. ▶", pal, highlighted: false) { model.nextHole() }
        }
    }

    private func navButton(_ label: String, _ pal: Pal, highlighted: Bool,
                           action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 15, weight: .semibold))
                .lineLimit(1)
                .foregroundColor(pal.primary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(Capsule().fill(highlighted ? pal.primaryContainer : .clear))
                .overlay(Capsule().strokeBorder(pal.outline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Mide el vuelo de un golpe con GPS y aprende la distancia real del palo:
/// marca la bola antes de pegar, camina hasta donde cayó y guarda. El palo
/// queda preseleccionado con el sugerido, corregible con ‹ ›.
struct ShotMeasureCard: View {
    @ObservedObject var model: GolfModel
    let suggestedYards: Double?
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        let yards = model.units == .yards
        Group {
            if model.shotClubIdx < 0 {
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("📏 MEDIR GOLPE")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(pal.onSurfaceVariant)
                        Text("Marca la bola antes de pegar y la app aprende tus distancias reales por palo.")
                            .font(.system(size: 12))
                            .foregroundColor(pal.onSurfaceVariant)
                    }
                    Spacer()
                    Button {
                        let p = model.players.indices.contains(model.activePlayerIndex)
                            ? model.players[model.activePlayerIndex] : nil
                        let idx = (suggestedYards != nil && p != nil)
                            ? clubIndexForDistance(suggestedYards!, p!.clubYards) : 0
                        model.markShot(clubIdx: idx)
                    } label: {
                        Text("Marcar bola")
                            .font(.system(size: 15, weight: .semibold))
                            .lineLimit(1)
                            .foregroundColor(pal.onPrimary)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(RoundedRectangle(cornerRadius: 14).fill(pal.primary))
                    }
                    .buttonStyle(.plain)
                    .disabled(model.userLat == nil)
                    .opacity(model.userLat == nil ? 0.5 : 1)
                }
            } else {
                let distM = model.shotDistanceM() ?? 0
                let shown = Int((yards ? metersToYards(distM) : distM).rounded())
                let measuredYd = Int(metersToYards(distM).rounded())
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("📏 GOLPE EN CURSO")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(pal.onSurfaceVariant)
                        HStack(spacing: 0) {
                            Button { model.changeShotClub(-1) } label: {
                                Text("‹").font(.system(size: 22))
                                    .foregroundColor(pal.primary)
                                    .padding(.horizontal, 8)
                            }
                            .buttonStyle(.plain)
                            Text(clubNames.indices.contains(model.shotClubIdx)
                                 ? clubNames[model.shotClubIdx] : "?")
                                .font(.system(size: 17, weight: .bold))
                                .foregroundColor(pal.primary)
                            Button { model.changeShotClub(1) } label: {
                                Text("›").font(.system(size: 22))
                                    .foregroundColor(pal.primary)
                                    .padding(.horizontal, 8)
                            }
                            .buttonStyle(.plain)
                        }
                        Text("\(shown) \(yards ? "yd" : "m") desde la marca")
                            .font(.system(size: 22, weight: .black))
                            .foregroundColor(pal.onSurface)
                        Text("Camina hasta donde cayó y guarda para afinar el palo.")
                            .font(.system(size: 12))
                            .foregroundColor(pal.onSurfaceVariant)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 4) {
                        Button {
                            model.saveShotToClub()
                        } label: {
                            Text("Guardar")
                                .font(.system(size: 15, weight: .semibold))
                                .lineLimit(1)
                                .foregroundColor(pal.onPrimary)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 10)
                                .background(RoundedRectangle(cornerRadius: 14).fill(pal.primary))
                        }
                        .buttonStyle(.plain)
                        .disabled(!(30...350).contains(measuredYd))
                        .opacity((30...350).contains(measuredYd) ? 1 : 0.5)
                        Button { model.cancelShot() } label: {
                            Text("Cancelar")
                                .font(.system(size: 13))
                                .foregroundColor(pal.error)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 20).fill(scheme.pal.surfaceVariant))
    }
}
