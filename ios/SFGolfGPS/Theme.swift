import SwiftUI

enum ThemeMode: String { case system = "SYSTEM", light = "LIGHT", dark = "DARK" }

/// Paleta "Fairway" — verdes profundos, alto contraste para luz solar directa.
/// Mismos roles y valores que Theme.kt (Material 3).
struct Pal {
    let primary: Color
    let onPrimary: Color
    let primaryContainer: Color
    let onPrimaryContainer: Color
    let secondary: Color
    let onSecondary: Color
    let secondaryContainer: Color
    let onSecondaryContainer: Color
    let background: Color
    let onBackground: Color
    let surface: Color
    let onSurface: Color
    let surfaceVariant: Color
    let onSurfaceVariant: Color
    let outline: Color
    let error: Color
    let onError: Color
    let errorContainer: Color
    let onErrorContainer: Color

    static let dark = Pal(
        primary: Color(argb: 0xFF7ADFA8),          // verde menta brillante
        onPrimary: Color(argb: 0xFF06281A),
        primaryContainer: Color(argb: 0xFF14503A),
        onPrimaryContainer: Color(argb: 0xFFC9F5DC),
        secondary: Color(argb: 0xFFE0A96D),        // arena / bunker
        onSecondary: Color(argb: 0xFF2B1A0A),
        secondaryContainer: Color(argb: 0xFF4A3320),
        onSecondaryContainer: Color(argb: 0xFFF5DFC2),
        background: Color(argb: 0xFF061E14),
        onBackground: Color(argb: 0xFFE8F5EC),
        surface: Color(argb: 0xFF0C2B1D),
        onSurface: Color(argb: 0xFFE8F5EC),
        surfaceVariant: Color(argb: 0xFF16382A),
        onSurfaceVariant: Color(argb: 0xFFB7D4C3),
        outline: Color(argb: 0xFF4E7A64),
        error: Color(argb: 0xFFFFB4AB),
        onError: Color(argb: 0xFF690005),
        errorContainer: Color(argb: 0xFF93000A),
        onErrorContainer: Color(argb: 0xFFFFDAD6)
    )

    static let light = Pal(
        primary: Color(argb: 0xFF1B5E20),
        onPrimary: .white,
        primaryContainer: Color(argb: 0xFFC8E6C9),
        onPrimaryContainer: Color(argb: 0xFF0A2E0D),
        secondary: Color(argb: 0xFF8D6E3B),
        onSecondary: .white,
        secondaryContainer: Color(argb: 0xFFF0E2C8),
        onSecondaryContainer: Color(argb: 0xFF3B2E14),
        background: Color(argb: 0xFFF6F8F4),
        onBackground: Color(argb: 0xFF15201A),
        surface: .white,
        onSurface: Color(argb: 0xFF15201A),
        surfaceVariant: Color(argb: 0xFFE1EBE2),
        onSurfaceVariant: Color(argb: 0xFF3E5347),
        outline: Color(argb: 0xFF6F8578),
        error: Color(argb: 0xFFBA1A1A),
        onError: .white,
        errorContainer: Color(argb: 0xFFFFDAD6),
        onErrorContainer: Color(argb: 0xFF410002)
    )
}

extension ColorScheme {
    var pal: Pal { self == .dark ? .dark : .light }
}

extension ThemeMode {
    /// Para .preferredColorScheme del root: nil = seguir al sistema.
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

func scoreName(_ diff: Int) -> String {
    switch diff {
    case ...(-3): return "Albatross 🦅"
    case -2: return "Eagle 🦅"
    case -1: return "Birdie 🐦"
    case 0: return "Par"
    case 1: return "Bogey"
    case 2: return "Doble bogey"
    default: return "+\(diff)"
    }
}
