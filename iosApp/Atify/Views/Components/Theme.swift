import SwiftUI

public struct AtifyTheme {
    public static let background = Color(hex: 0x121212)
    public static let surface = Color(hex: 0x1E1E1E)
    public static let elevatedSurface = Color(hex: 0x282828)
    public static let spotifyGreen = Color(hex: 0x1ED760)
    public static let textPrimary = Color.white
    public static let textSecondary = Color(hex: 0xB3B3B3)
    public static let textSubtle = Color(hex: 0x727272)
    public static let divider = Color(hex: 0x2A2A2A)
}

extension Color {
    public init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }
}
