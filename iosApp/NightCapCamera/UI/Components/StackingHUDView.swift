import SwiftUI

public struct StackingHUDView: View {
    let mode: StackingMode
    let isStackingActive: Bool
    let elapsedSeconds: Int
    let frameCount: Int
    let isRecordingVideo: Bool
    let recordingSeconds: Int
    let isGyroEnabled: Bool
    let isRedVision: Bool
    let isTorchOn: Bool
    let isFrontCamera: Bool
    let primaryColor: Color

    let onToggleRedVision: () -> Void
    let onToggleGyro: () -> Void
    let onToggleTorch: () -> Void
    let onSwitchCamera: () -> Void

    @State private var isPulsing: Bool = false

    public var body: some View {
        VStack {
            // Top Bar
            HStack {
                // Mode Badge
                HStack(spacing: 8) {
                    if isStackingActive || isRecordingVideo {
                        Circle()
                            .fill(Color.red)
                            .frame(width: 10, height: 10)
                            .opacity(isPulsing ? 0.3 : 1.0)
                            .animation(.easeInOut(duration: 0.6).repeatForever(autoreverses: true), value: isPulsing)
                    }

                    Text(isRecordingVideo ? "REC" : mode.displayName.uppercased())
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(isRecordingVideo ? .red : primaryColor)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(Color.black.opacity(0.65))
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(isRecordingVideo ? Color.red : primaryColor.opacity(0.5), lineWidth: 1)
                )

                Spacer()

                // Quick Utilities Row
                HStack(spacing: 8) {
                    // Flip Camera
                    IconButton(
                        systemName: "camera.rotate",
                        isActive: isFrontCamera,
                        primaryColor: primaryColor,
                        action: onSwitchCamera
                    )

                    // Torch
                    IconButton(
                        systemName: isTorchOn ? "flashlight.on.fill" : "flashlight.off.fill",
                        isActive: isTorchOn,
                        primaryColor: primaryColor,
                        action: onToggleTorch
                    )

                    // Gyro Stabilization
                    IconButton(
                        systemName: "gyroscope",
                        isActive: isGyroEnabled,
                        primaryColor: primaryColor,
                        action: onToggleGyro
                    )

                    // Astronomy Red Vision Mode
                    IconButton(
                        systemName: "eye.fill",
                        isActive: isRedVision,
                        activeColor: Color.red,
                        primaryColor: primaryColor,
                        action: onToggleRedVision
                    )
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 10)

            Spacer()

            // Video Recording Timer Display
            if isRecordingVideo {
                HStack(spacing: 10) {
                    Circle()
                        .fill(Color.red)
                        .frame(width: 12, height: 12)
                        .opacity(isPulsing ? 0.3 : 1.0)

                    let minutes = recordingSeconds / 60
                    let seconds = recordingSeconds % 60
                    Text(String(format: "REC %02d:%02d", minutes, seconds))
                        .font(.system(size: 24, weight: .black, design: .monospaced))
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 22)
                .padding(.vertical, 10)
                .background(Color.black.opacity(0.85))
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.red, lineWidth: 1.5))
                .transition(.scale.combined(with: .opacity))
            }

            // Stacking Exposure Timer & Frames Display
            if isStackingActive {
                VStack(spacing: 4) {
                    let minutes = elapsedSeconds / 60
                    let seconds = elapsedSeconds % 60
                    Text(String(format: "%02d:%02d", minutes, seconds))
                        .font(.system(size: 36, weight: .bold, design: .monospaced))
                        .foregroundColor(.white)

                    Text("Stacked \(frameCount) frames")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(primaryColor)
                }
                .padding(.horizontal, 28)
                .padding(.vertical, 14)
                .background(Color.black.opacity(0.85))
                .clipShape(RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(primaryColor, lineWidth: 1.5))
                .transition(.scale.combined(with: .opacity))
            }

            Spacer()
        }
        .onAppear {
            isPulsing = true
        }
    }
}

private struct IconButton: View {
    let systemName: String
    let isActive: Bool
    var activeColor: Color? = nil
    let primaryColor: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 16))
                .foregroundColor(isActive ? (activeColor ?? primaryColor) : Color.white)
                .frame(width: 38, height: 38)
                .background(isActive ? (activeColor ?? primaryColor).opacity(0.25) : Color.black.opacity(0.6))
                .clipShape(Circle())
                .overlay(
                    Circle().stroke(isActive ? (activeColor ?? primaryColor) : Color.gray, lineWidth: 1)
                )
        }
    }
}
