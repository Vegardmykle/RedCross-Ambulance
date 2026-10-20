import SwiftUI
import UIKit

// Farger fra designet (DESIGN.md). Hver farge har en mørk variant slik at
// flater og tekst ikke blir hvitt på hvitt i dark mode.
extension Color {
    static let rkPrimary = dynamic(light: (179, 0, 15), dark: (229, 57, 53))
    static let rkPrimaryContainer = dynamic(light: (217, 35, 35), dark: (147, 0, 10))
    static let rkOnPrimary = Color.white
    static let rkSurface = dynamic(light: (249, 249, 249), dark: (0, 0, 0))
    static let rkSurfaceContainer = dynamic(light: (238, 238, 238), dark: (44, 44, 46))
    /// Kortflater oppå rkSurface
    static let rkCard = dynamic(light: (255, 255, 255), dark: (28, 28, 30))
    static let rkError = dynamic(light: (186, 26, 26), dark: (255, 180, 171))
    static let rkErrorContainer = dynamic(light: (255, 218, 214), dark: (147, 0, 10))

    private static func dynamic(light: (Int, Int, Int), dark: (Int, Int, Int)) -> Color {
        Color(UIColor { traits in
            let (r, g, b) = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat(r) / 255, green: CGFloat(g) / 255, blue: CGFloat(b) / 255, alpha: 1)
        })
    }
}
