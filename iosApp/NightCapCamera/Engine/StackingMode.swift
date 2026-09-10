import Foundation

public enum StackingMode: String, CaseIterable, Identifiable {
    case photo = "PHOTO"
    case video = "VIDEO"
    case stars = "STARS"
    case starTrails = "STAR_TRAILS"
    case lightTrails = "LIGHT_TRAILS"
    case meteors = "METEORS"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .photo:
            return "Photo"
        case .video:
            return "Video"
        case .stars:
            return "Stars"
        case .starTrails:
            return "Star Trails"
        case .lightTrails:
            return "Light Trails"
        case .meteors:
            return "Meteors"
        }
    }

    public var description: String {
        switch self {
        case .photo:
            return "Standard manual photography with ISO Boost and fine parameter tuning"
        case .video:
            return "High quality video recording with audio and exposure controls"
        case .stars:
            return "Deep noise reduction and average stacking to reveal stars and Milky Way"
        case .starTrails:
            return "Maximum luminance blending for circular star trails around the celestial pole"
        case .lightTrails:
            return "Endless exposure for traffic light trails, fireworks, and light painting"
        case .meteors:
            return "Automatic detection and capture of shooting stars and ISS transit trails"
        }
    }
}
