import SwiftUI

@main
struct NightCapCameraApp: App {
    init() {
        // Keep screen awake during long astrophotography exposures
        UIApplication.shared.isIdleTimerDisabled = true
    }

    var body: some Scene {
        WindowGroup {
            CameraView()
                .preferredColorScheme(.dark)
                .statusBar(hidden: true)
        }
    }
}
