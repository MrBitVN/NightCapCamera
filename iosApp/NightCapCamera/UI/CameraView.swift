import SwiftUI
import AVFoundation

public struct CameraView: View {
    @StateObject private var cameraManager = AVCameraManager.shared
    @StateObject private var renderer = MetalRenderer.shared
    private let gyro = IOSGyroStabilizer.shared

    @State private var selectedMode: StackingMode = .photo
    @State private var isStackingActive: Bool = false
    @State private var elapsedSeconds: Int = 0
    @State private var frameCount: Int = 0
    @State private var isGyroEnabled: Bool = true
    @State private var isRedVision: Bool = false

    @State private var activeParam: ActiveParameter = .none
    @State private var isoBoost: Float = 1.0

    // Toast banner
    @State private var toastMessage: String?
    @State private var toastTask: Task<Void, Never>?

    // Timer tasks
    @State private var stackingTimerTask: Task<Void, Never>?
    @State private var recordingTimerTask: Task<Void, Never>?

    public init() {}

    public var body: some View {
        let primaryColor = NightCapTheme.primaryColor(isRedVision: isRedVision)

        ZStack {
            Color.black.ignoresSafeArea()

            // Layer 1: Metal Viewfinder
            MetalViewRepresentable()
                .ignoresSafeArea()

            // Layer 2: Gesture Overlay (Pinch to Zoom & Tap to Focus)
            GestureOverlayView(
                currentZoom: $cameraManager.currentZoom,
                minZoom: cameraManager.minZoom,
                maxZoom: cameraManager.maxZoom,
                primaryColor: primaryColor,
                onTapToFocus: { point in
                    cameraManager.tapToFocus(at: point)
                },
                onZoomChanged: { zoom in
                    cameraManager.setZoomFactor(zoom)
                }
            ) {
                // Layer 3: Top HUD (Status bar, Mode badge, Utility buttons) & Center Timers
                StackingHUDView(
                    mode: selectedMode,
                    isStackingActive: isStackingActive,
                    elapsedSeconds: elapsedSeconds,
                    frameCount: frameCount,
                    isRecordingVideo: cameraManager.isRecordingVideo,
                    recordingSeconds: cameraManager.recordingSeconds,
                    isGyroEnabled: isGyroEnabled,
                    isRedVision: isRedVision,
                    isTorchOn: cameraManager.isTorchOn,
                    isFrontCamera: cameraManager.isFrontCamera,
                    primaryColor: primaryColor,
                    onToggleRedVision: {
                        isRedVision.toggle()
                        renderer.isRedVision = isRedVision
                    },
                    onToggleGyro: {
                        isGyroEnabled.toggle()
                        gyro.isEnabled = isGyroEnabled
                        showToast(isGyroEnabled ? "Handheld stabilization enabled" : "Stabilization disabled (Tripod mode)")
                    },
                    onToggleTorch: {
                        let nextTorch = !cameraManager.isTorchOn
                        cameraManager.toggleTorch(nextTorch)
                        showToast(nextTorch ? "Torch enabled" : "Torch disabled")
                    },
                    onSwitchCamera: {
                        if cameraManager.isRecordingVideo {
                            showToast("Please stop recording before switching camera")
                        } else if isStackingActive {
                            showToast("Please stop exposure before switching camera")
                        } else {
                            cameraManager.switchCamera()
                            showToast(!cameraManager.isFrontCamera ? "Switched to Front Camera" : "Switched to Back Camera")
                        }
                    }
                )
            }

            // Layer 4: Bottom Controls Column
            VStack(spacing: 0) {
                Spacer()

                // Parameter Slider Bar
                ParameterSliderBarView(
                    activeParam: activeParam,
                    primaryColor: primaryColor,
                    exposureTimeNs: cameraManager.currentExposureDurationNs,
                    minExposureNs: cameraManager.minExposureDurationNs,
                    maxExposureNs: cameraManager.maxExposureDurationNs,
                    isManualExposure: cameraManager.isManualExposure,
                    onExposureChanged: { ns in
                        cameraManager.setExposure(durationNs: ns, iso: cameraManager.currentISO)
                    },
                    onAutoExposure: {
                        cameraManager.setAutoExposure()
                    },
                    currentISO: cameraManager.currentISO,
                    minISO: cameraManager.minISO,
                    maxISO: cameraManager.maxISO,
                    onISOChanged: { iso in
                        cameraManager.setExposure(durationNs: cameraManager.currentExposureDurationNs, iso: iso)
                    },
                    focusDistance: cameraManager.lensPosition,
                    isManualFocus: cameraManager.isManualFocus,
                    isInfinityFocus: cameraManager.isInfinityFocus,
                    onFocusDistanceChanged: { dist in
                        cameraManager.setFocusDistance(dist)
                    },
                    onAutoFocus: {
                        cameraManager.setAutoFocus()
                    },
                    onInfinityFocus: {
                        cameraManager.setInfinityFocus()
                    },
                    evCompensation: cameraManager.evCompensation,
                    minEV: cameraManager.minEV,
                    maxEV: cameraManager.maxEV,
                    onEVChanged: { ev in
                        cameraManager.setEVCompensation(ev)
                    },
                    onDismiss: {
                        activeParam = .none
                    }
                )

                // Manual Control Bar
                ManualControlBarView(
                    selectedMode: selectedMode,
                    activeParam: activeParam,
                    isStackingActive: isStackingActive,
                    isRecordingVideo: cameraManager.isRecordingVideo,
                    isManualExposure: cameraManager.isManualExposure,
                    exposureTimeNs: cameraManager.currentExposureDurationNs,
                    currentISO: cameraManager.currentISO,
                    isoBoost: isoBoost,
                    isManualFocus: cameraManager.isManualFocus,
                    isInfinityFocus: cameraManager.isInfinityFocus,
                    focusDistance: cameraManager.lensPosition,
                    evCompensation: cameraManager.evCompensation,
                    primaryColor: primaryColor,
                    onSelectParameter: { param in
                        activeParam = param
                    },
                    onModeSelected: { mode in
                        selectedMode = mode
                        renderer.currentMode = mode
                        if mode != .photo && mode != .video {
                            cameraManager.setExposure(durationNs: 250_000_000, iso: 1600) // 1/4s, ISO 1600 for astrophotography
                        } else {
                            cameraManager.setAutoExposure()
                        }
                    },
                    onShutterClick: handleShutterClick,
                    onCycleIsoBoost: {
                        isoBoost = (isoBoost == 1.0) ? 2.0 : (isoBoost == 2.0 ? 4.0 : (isoBoost == 4.0 ? 8.0 : 1.0))
                        renderer.isoBoost = isoBoost
                        showToast("ISO Boost: \(Int(isoBoost))x")
                    },
                    onResetStacking: {
                        if isStackingActive {
                            isStackingActive = false
                            renderer.resetStacking()
                            frameCount = 0
                            elapsedSeconds = 0
                            showToast("Exposure cancelled")
                        }
                    }
                )
            }

            // Layer 5: Permission Denied Overlay (if user denied camera access)
            if cameraManager.isPermissionDenied {
                VStack(spacing: 16) {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 44))
                        .foregroundColor(primaryColor)
                    Text("Camera Access Required")
                        .font(.headline)
                        .foregroundColor(.white)
                    Text("Please allow camera access in Settings to use NightCap Camera.")
                        .font(.subheadline)
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                    Button("Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(primaryColor)
                    .foregroundColor(.black)
                    .cornerRadius(20)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black.opacity(0.85))
            }

            // Layer 6: Toast Message Banner
            if let toast = toastMessage {
                VStack {
                    Text(toast)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 8)
                        .background(Color.black.opacity(0.85))
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(primaryColor.opacity(0.4), lineWidth: 1))
                        .padding(.top, 60)
                        .transition(.move(edge: .top).combined(with: .opacity))

                    Spacer()
                }
            }
        }
        .onAppear {
            cameraManager.frameDelegate = renderer
            cameraManager.requestPermissionsAndStart()
            gyro.start()

            renderer.onFrameProcessed = { count in
                self.frameCount = count
            }
        }
        .onDisappear {
            cameraManager.stopSession()
            gyro.stop()
        }
    }

    private func handleShutterClick() {
        switch selectedMode {
        case .video:
            if !cameraManager.isRecordingVideo {
                let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("REC_\(Int(Date().timeIntervalSince1970)).mp4")
                cameraManager.startRecordingVideo(to: tempURL)
                showToast("Recording started...")

                recordingTimerTask?.cancel()
                recordingTimerTask = Task {
                    while cameraManager.isRecordingVideo {
                        try? await Task.sleep(nanoseconds: 1_000_000_000)
                        cameraManager.recordingSeconds += 1
                    }
                }
            } else {
                recordingTimerTask?.cancel()
                showToast("Processing and saving video...")
                cameraManager.stopRecordingVideo { outputURL in
                    guard let url = outputURL else {
                        self.showToast("Failed to save video")
                        return
                    }
                    IOSImageSaver.saveVideoToGallery(videoURL: url) { success in
                        self.showToast(success ? "Saved video (\(self.cameraManager.recordingSeconds) s) to Gallery!" : "Failed to save video")
                    }
                }
            }

        case .photo:
            showToast("Capturing photo...")
            renderer.captureSinglePhoto { image in
                guard let img = image else {
                    self.showToast("Failed to save photo")
                    return
                }
                IOSImageSaver.saveImageToGallery(img, modeName: "PHOTO") { success in
                    self.showToast(success ? "Photo saved to Gallery!" : "Failed to save photo")
                }
            }

        default:
            // Multi-frame Stacking Modes (Stars, Star Trails, Light Trails, Meteors)
            if !isStackingActive {
                frameCount = 0
                elapsedSeconds = 0
                isStackingActive = true
                renderer.startStacking()
                showToast("Exposure started [\(selectedMode.displayName)]")

                stackingTimerTask?.cancel()
                stackingTimerTask = Task {
                    while isStackingActive {
                        try? await Task.sleep(nanoseconds: 1_000_000_000)
                        elapsedSeconds += 1
                    }
                }
            } else {
                isStackingActive = false
                stackingTimerTask?.cancel()
                renderer.stopStacking { image in
                    guard let img = image else {
                        self.showToast("Failed to save photo")
                        return
                    }
                    IOSImageSaver.saveImageToGallery(img, modeName: selectedMode.rawValue) { success in
                        self.showToast(success ? "Saved astrophoto (\(self.frameCount) frames) to Gallery!" : "Failed to save photo")
                    }
                }
            }
        }
    }

    private func showToast(_ message: String) {
        toastTask?.cancel()
        withAnimation {
            toastMessage = message
        }
        toastTask = Task {
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            withAnimation {
                toastMessage = nil
            }
        }
    }
}
