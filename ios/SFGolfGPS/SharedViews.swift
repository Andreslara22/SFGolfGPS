import SwiftUI

// Componentes chicos compartidos entre pantallas (espejo de los de Screens.kt).

struct Pill: View {
    let text: String
    let bg: Color
    let fg: Color

    var body: some View {
        Text(text)
            .font(.system(size: 16, weight: .heavy))
            .foregroundColor(fg)
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
            .background(Capsule().fill(bg))
    }
}

struct MiniChip: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        Button(action: action) {
            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .lineLimit(1)
                .foregroundColor(selected ? pal.onPrimary : pal.onSurface)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(Capsule().fill(selected ? pal.primary : pal.surface))
        }
        .buttonStyle(.plain)
    }
}

/// Botón de opción: relleno si está seleccionada, contorno si no.
struct ChoiceButton: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        Button(action: action) {
            Text(label)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(selected ? pal.onPrimary : pal.primary)
                .padding(.horizontal, 18)
                .padding(.vertical, 9)
                .background(
                    Capsule().fill(selected ? pal.primary : .clear)
                )
                .overlay(
                    Capsule().strokeBorder(selected ? .clear : pal.outline, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}

/// Botón circular −/+ con contorno.
struct RoundIconButton: View {
    let label: String
    let filled: Bool
    var size: CGFloat = 44
    let action: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        Button(action: action) {
            Text(label)
                .font(.system(size: size * 0.5, weight: .semibold))
                .foregroundColor(filled ? pal.onPrimary : pal.primary)
                .frame(width: size, height: size)
                .background(Circle().fill(filled ? pal.primary : .clear))
                .overlay(Circle().strokeBorder(filled ? .clear : pal.outline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct FcbLabel: View {
    let letter: String
    let value: Int
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        HStack(alignment: .bottom, spacing: 3) {
            Text(letter)
                .font(.system(size: 13, weight: .heavy))
                .foregroundColor(pal.primary)
            Text("\(value)")
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(pal.onBackground)
        }
    }
}

struct StatBadge: View {
    let label: String
    let value: String
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        VStack(spacing: 1) {
            Text(label)
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(pal.onSurfaceVariant)
            Text(value)
                .font(.system(size: 14, weight: .heavy))
                .foregroundColor(pal.onSurface)
        }
    }
}

/// Un solo botón de bandera para elegir la posición del pin del día.
/// Toca para ciclar: sin pin → frente (rojo) → medio (blanco) → fondo (azul).
struct FlagChip: View {
    let flag: Int
    let action: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        let color: Color
        let label: String
        switch flag {
        case 0: color = Color(argb: 0xFFE85D4A); label = "Pin al frente"
        case 1: color = Color(argb: 0xFFF4F1E8); label = "Pin al medio"
        case 2: color = Color(argb: 0xFF5AB0FF); label = "Pin al fondo"
        default: color = pal.onSurfaceVariant; label = "Elegir pin"
        }
        return Button(action: action) {
            HStack(spacing: 8) {
                Text("⚑")
                    .font(.system(size: 20))
                    .foregroundColor(color)
                Text(label)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(pal.onSurface)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(Capsule().fill(pal.surfaceVariant))
        }
        .buttonStyle(.plain)
    }
}

struct StatTile: View {
    let label: String
    let value: String
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        VStack(spacing: 2) {
            Text(label)
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(pal.onSurfaceVariant)
                .lineLimit(1)
            Text(value)
                .font(.system(size: 20, weight: .heavy))
                .foregroundColor(pal.onSurface)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 8)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 14).fill(pal.surface))
    }
}

/// Fila de golpes por jugador: nombre + resultado, − N +, putts y fairway.
struct StrokeRow: View {
    let name: String
    let strokes: Int
    let par: Int
    let onAdd: () -> Void
    let onRemove: () -> Void
    var putts = 0
    var onPuttAdd: () -> Void = {}
    var onPuttRemove: () -> Void = {}
    var fir = -1
    var onFirCycle: () -> Void = {}
    var showFir = false
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let pal = scheme.pal
        VStack(spacing: 4) {
            HStack {
                VStack(alignment: .leading, spacing: 1) {
                    Text(name)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(pal.onSurface)
                    if strokes > 0 {
                        Text(scoreName(strokes - par))
                            .font(.system(size: 12))
                            .foregroundColor(pal.onSurfaceVariant)
                    }
                }
                Spacer()
                RoundIconButton(label: "−", filled: false, action: onRemove)
                Text("\(strokes)")
                    .font(.system(size: 26, weight: .heavy))
                    .foregroundColor(pal.onSurface)
                    .frame(width: 52)
                RoundIconButton(label: "+", filled: true, action: onAdd)
            }
            HStack(spacing: 0) {
                Text("PUTTS")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(pal.onSurfaceVariant)
                Button(action: onPuttRemove) {
                    Text("−")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(pal.primary)
                        .padding(.horizontal, 10).padding(.vertical, 2)
                }
                .buttonStyle(.plain)
                Text("\(putts)")
                    .font(.system(size: 16, weight: .heavy))
                    .foregroundColor(pal.onSurface)
                    .frame(width: 24)
                Button(action: onPuttAdd) {
                    Text("+")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(pal.primary)
                        .padding(.horizontal, 10).padding(.vertical, 2)
                }
                .buttonStyle(.plain)
                if showFir {
                    Spacer()
                    let (label, bg, fg): (String, Color, Color) = {
                        switch fir {
                        case 1: return ("FAIRWAY ✓", pal.primaryContainer, pal.onPrimaryContainer)
                        case 0: return ("FAIRWAY ✗", pal.errorContainer, pal.onErrorContainer)
                        default: return ("FAIRWAY —", pal.surfaceVariant, pal.onSurfaceVariant)
                        }
                    }()
                    Button(action: onFirCycle) {
                        Text(label)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(fg)
                            .padding(.horizontal, 12).padding(.vertical, 5)
                            .background(Capsule().fill(bg))
                    }
                    .buttonStyle(.plain)
                } else {
                    Spacer()
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 18).fill(pal.surface))
    }
}
