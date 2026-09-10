import SwiftUI

public enum NightCapTheme {
    public static let standardPrimary = Color(red: 1.0, green: 0.8, blue: 0.0) // NightCap Gold
    public static let standardAccent = Color(red: 0.0, green: 0.9, blue: 1.0)  // Cyan
    public static let standardBackground = Color.black
    public static let standardSurface = Color(white: 0.08)

    // Astronomy Red Vision Mode
    public static let astroRedPrimary = Color(red: 1.0, green: 0.09, blue: 0.27)
    public static let astroRedAccent = Color(red: 0.83, green: 0.0, blue: 0.0)
    public static let astroRedText = Color(red: 1.0, green: 0.32, blue: 0.32)

    public static func primaryColor(isRedVision: Bool) -> Color {
        isRedVision ? astroRedPrimary : standardPrimary
    }

    public static func accentColor(isRedVision: Bool) -> Color {
        isRedVision ? astroRedAccent : standardAccent
    }
}
