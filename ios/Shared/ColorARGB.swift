import SwiftUI

enum Units: String { case yards = "YARDS", meters = "METERS" }

extension Color {
    /// Color desde 0xAARRGGBB (mismo formato que Compose en Android).
    init(argb: UInt32) {
        let a = Double((argb >> 24) & 0xFF) / 255
        let r = Double((argb >> 16) & 0xFF) / 255
        let g = Double((argb >> 8) & 0xFF) / 255
        let b = Double(argb & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}
