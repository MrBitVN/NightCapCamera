import Foundation
import AVFoundation
import CoreMedia
import SwiftUI

public protocol CameraFrameDelegate: AnyObject {
    func cameraManager(_ manager: AVCameraManager, didOutput sampleBuffer: CMSampleBuffer)
}

public final class AVCameraManager: NSObject, ObservableObject {
    public static let shared = AVCameraManager()

    // Published states for SwiftUI
    @Published public var isRunning: Bool = false
    @Published public var isPermissionDenied: Bool = false
    @Published public var isFrontCamera: Bool = false
    @Published public var isTorchOn: Bool = false

    // Exposure parameters
    @Published public var isManualExposure: Bool = false
    @Published public var currentExposureDurationNs: Int64 = 33_333_333 // 1/30s
    @Published public var minExposureDurationNs: Int64 = 1_000_000 // 1/1000s
    @Published public var maxExposureDurationNs: Int64 = 1_000_000_000 // 1s
    @Published public var currentISO: Float = 400
    @Published public var minISO: Float = 50
    @Published public var maxISO: Float = 3200

    // Focus parameters
    @Published public var isManualFocus: Bool = false
    @Published public var isInfinityFocus: Bool = false
    @Published public var lensPosition: Float = 0.0 // 0.0 = infinity, 1.0 = macro

    // EV compensation
    @Published public var evCompensation: Float = 0.0
    @Published public var minEV: Float = -2.0
    @Published public var maxEV: Float = 2.0

    // Zoom state
    @Published public var currentZoom: CGFloat = 1.0
    @Published public var minZoom: CGFloat = 1.0
    @Published public var maxZoom: CGFloat = 10.0

    // Video recording
    @Published public var isRecordingVideo: Bool = false
    @Published public var recordingSeconds: Int = 0

    public weak var frameDelegate: CameraFrameDelegate?

    private let captureSession = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "com.nightcap.camera.sessionQueue", qos: .userInteractive)
    private let videoDataOutputQueue = DispatchQueue(label: "com.nightcap.camera.videoOutputQueue", qos: .userInteractive)

    private var currentDevice: AVCaptureDevice?
    private var videoInput: AVCaptureDeviceInput?
    private var audioInput: AVCaptureDeviceInput?
    private let videoOutput = AVCaptureVideoDataOutput()
    private let audioOutput = AVCaptureAudioDataOutput()

    // Asset Writer for video recording
    private var assetWriter: AVAssetWriter?
    private var assetWriterVideoInput: AVAssetWriterInput?
    private var assetWriterAudioInput: AVAssetWriterInput?
    private var recordingStartTime: CMTime?
    private var currentVideoURL: URL?

    private var isSessionConfigured: Bool = false

    public override init() {
        super.init()
    }

    public func requestPermissionsAndStart() {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            DispatchQueue.main.async { self.isPermissionDenied = false }
            configureSession()
            startSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                guard let self = self else { return }
                if granted {
                    DispatchQueue.main.async { self.isPermissionDenied = false }
                    self.configureSession()
                    self.startSession()
                } else {
                    DispatchQueue.main.async { self.isPermissionDenied = true }
                }
            }
        default:
            DispatchQueue.main.async { self.isPermissionDenied = true }
        }
    }

    public func configureSession() {
        sessionQueue.async { [weak self] in
            guard let self = self, !self.isSessionConfigured else { return }
            self.captureSession.beginConfiguration()
            self.captureSession.sessionPreset = .hd1920x1080

            self.setupCameraDevice(position: self.isFrontCamera ? .front : .back)
            self.setupVideoOutput()
            self.setupAudio()

            self.captureSession.commitConfiguration()
            self.isSessionConfigured = true
        }
    }

    public func startSession() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            if !self.captureSession.isRunning {
                self.captureSession.startRunning()
                DispatchQueue.main.async {
                    self.isRunning = true
                }
            }
        }
    }

    public func stopSession() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            if self.captureSession.isRunning {
                self.captureSession.stopRunning()
                DispatchQueue.main.async {
                    self.isRunning = false
                }
            }
        }
    }

    private func setupCameraDevice(position: AVCaptureDevice.Position) {
        if let currentInput = videoInput {
            captureSession.removeInput(currentInput)
        }

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) else {
            return
        }
        currentDevice = device

        do {
            let input = try AVCaptureDeviceInput(device: device)
            if captureSession.canAddInput(input) {
                captureSession.addInput(input)
                videoInput = input
            }

            // Read sensor limits
            let format = device.activeFormat
            let minDuration = format.minExposureDuration
            let maxDuration = format.maxExposureDuration
            let minIso = format.minISO
            let maxIso = format.maxISO

            let minDurationNs = Int64(CMTimeGetSeconds(minDuration) * 1_000_000_000)
            let maxDurationNs = Int64(CMTimeGetSeconds(maxDuration) * 1_000_000_000)
            let maxZoomFactor = min(format.videoMaxZoomFactor, 10.0)

            DispatchQueue.main.async {
                self.minExposureDurationNs = minDurationNs
                self.maxExposureDurationNs = maxDurationNs
                self.minISO = minIso
                self.maxISO = maxIso
                self.minEV = device.minExposureTargetBias
                self.maxEV = device.maxExposureTargetBias
                self.minZoom = 1.0
                self.maxZoom = maxZoomFactor
                self.currentZoom = 1.0
            }
        } catch {
            print("Failed to create AVCaptureDeviceInput: \(error)")
        }
    }

    private func setupVideoOutput() {
        if captureSession.canAddOutput(videoOutput) {
            videoOutput.alwaysDiscardsLateVideoFrames = true
            videoOutput.videoSettings = [
                kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA)
            ]
            videoOutput.setSampleBufferDelegate(self, queue: videoDataOutputQueue)
            captureSession.addOutput(videoOutput)
        }
        updateVideoConnectionOrientation()
    }

    private func updateVideoConnectionOrientation() {
        if let connection = videoOutput.connection(with: .video) {
            if connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
            if connection.isVideoMirroringSupported {
                connection.isVideoMirrored = isFrontCamera
            }
        }
    }

    private func setupAudio() {
        guard AVCaptureDevice.authorizationStatus(for: .audio) == .authorized else { return }
        guard let audioDevice = AVCaptureDevice.default(for: .audio) else { return }
        do {
            let aInput = try AVCaptureDeviceInput(device: audioDevice)
            if captureSession.canAddInput(aInput) {
                captureSession.addInput(aInput)
                audioInput = aInput
            }
            if captureSession.canAddOutput(audioOutput) {
                audioOutput.setSampleBufferDelegate(self, queue: videoDataOutputQueue)
                captureSession.addOutput(audioOutput)
            }
        } catch {
            print("Failed to add audio input: \(error)")
        }
    }

    // MARK: - Manual Controls

    public func setExposure(durationNs: Int64, iso: Float) {
        guard let device = currentDevice else { return }
        sessionQueue.async {
            guard device.isExposureModeSupported(.custom) else { return }
            do {
                try device.lockForConfiguration()
                let seconds = Double(durationNs) / 1_000_000_000.0
                let rawTime = CMTime(seconds: seconds, preferredTimescale: 1_000_000_000)
                let minDur = device.activeFormat.minExposureDuration
                let maxDur = device.activeFormat.maxExposureDuration
                let clampedTime = CMTimeMaximum(minDur, CMTimeMinimum(rawTime, maxDur))
                let clampedIso = min(max(iso, device.activeFormat.minISO), device.activeFormat.maxISO)
                device.setExposureModeCustom(duration: clampedTime, iso: clampedIso, completionHandler: nil)
                device.unlockForConfiguration()

                let actualNs = Int64(CMTimeGetSeconds(clampedTime) * 1_000_000_000)
                DispatchQueue.main.async {
                    self.isManualExposure = true
                    self.currentExposureDurationNs = actualNs
                    self.currentISO = clampedIso
                }
            } catch {
                print("Failed to set exposure: \(error)")
            }
        }
    }

    public func setAutoExposure() {
        guard let device = currentDevice else { return }
        sessionQueue.async {
            do {
                try device.lockForConfiguration()
                if device.isExposureModeSupported(.continuousAutoExposure) {
                    device.exposureMode = .continuousAutoExposure
                }
                device.unlockForConfiguration()
                DispatchQueue.main.async {
                    self.isManualExposure = false
                }
            } catch {
                print("Failed to set auto exposure: \(error)")
            }
        }
    }

    public func setFocusDistance(_ position: Float) {
        guard let device = currentDevice else { return }
        sessionQueue.async {
            guard device.isFocusModeSupported(.locked),
                  device.isLockingFocusWithCustomLensPositionSupported else { return }
            do {
                try device.lockForConfiguration()
                let clamped = min(max(position, 0.0), 1.0)
                device.setFocusModeLocked(lensPosition: clamped, completionHandler: nil)
                device.unlockForConfiguration()
                DispatchQueue.main.async {
                    self.isManualFocus = true
                    self.isInfinityFocus = false
                    self.lensPosition = clamped
                }
            } catch {
                print("Failed to set focus distance: \(error)")
            }
        }
    }

    public func setInfinityFocus() {
        guard let device = currentDevice else { return }
        sessionQueue.async {
            guard device.isFocusModeSupported(.locked),
                  device.isLockingFocusWithCustomLensPositionSupported else { return }
            do {
                try device.lockForConfiguration()
                device.setFocusModeLocked(lensPosition: 0.0, completionHandler: nil)
                device.unlockForConfiguration()
                DispatchQueue.main.async {
                    self.isManualFocus = false
                    self.isInfinityFocus = true
                    self.lensPosition = 0.0
                }
            } catch {
                print("Failed to set infinity focus: \(error)")
            }
        }
    }

    public func setAutoFocus() {
        guard let device = currentDevice else { return }
        sessionQueue.async {
            do {
                try device.lockForConfiguration()
                if device.isFocusModeSupported(.continuousAutoFocus) {
                    device.focusMode = .continuousAutoFocus
                }
                device.unlockForConfiguration()
                DispatchQueue.main.async {
                    self.isManualFocus = false
                    self.isInfinityFocus = false
                }
            } catch {
                print("Failed to set auto focus: \(error)")
            }
        }
    }

    public func setEVCompensation(_ ev: Float) {
        guard let device = currentDevice else { return }
        sessionQueue.async {
            do {
                try device.lockForConfiguration()
                let clamped = min(max(ev, device.minExposureTargetBias), device.maxExposureTargetBias)
                device.setExposureTargetBias(clamped, completionHandler: nil)
                device.unlockForConfiguration()
                DispatchQueue.main.async {
                    self.evCompensation = clamped
                }
            } catch {
                print("Failed to set EV compensation: \(error)")
            }
        }
    }

    public func setZoomFactor(_ zoom: CGFloat) {
        guard let device = currentDevice else { return }
        sessionQueue.async {
            do {
                try device.lockForConfiguration()
                let clamped = min(max(zoom, self.minZoom), self.maxZoom)
                device.videoZoomFactor = clamped
                device.unlockForConfiguration()
                DispatchQueue.main.async {
                    self.currentZoom = clamped
                }
            } catch {
                print("Failed to set zoom: \(error)")
            }
        }
    }

    public func toggleTorch(_ enable: Bool) {
        guard let device = currentDevice, device.hasTorch else { return }
        sessionQueue.async {
            do {
                try device.lockForConfiguration()
                device.torchMode = enable ? .on : .off
                device.unlockForConfiguration()
                DispatchQueue.main.async {
                    self.isTorchOn = enable
                }
            } catch {
                print("Failed to toggle torch: \(error)")
            }
        }
    }

    public func switchCamera() {
        guard !isRecordingVideo else { return }
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            let nextIsFront = !self.isFrontCamera
            self.captureSession.beginConfiguration()
            self.setupCameraDevice(position: nextIsFront ? .front : .back)
            DispatchQueue.main.async {
                self.isFrontCamera = nextIsFront
            }
            if let connection = self.videoOutput.connection(with: .video) {
                if connection.isVideoOrientationSupported {
                    connection.videoOrientation = .portrait
                }
                if connection.isVideoMirroringSupported {
                    connection.isVideoMirrored = nextIsFront
                }
            }
            self.captureSession.commitConfiguration()
        }
    }

    public func tapToFocus(at point: CGPoint) {
        guard let device = currentDevice else { return }
        sessionQueue.async {
            do {
                try device.lockForConfiguration()
                if device.isFocusPointOfInterestSupported && device.isFocusModeSupported(.autoFocus) {
                    device.focusPointOfInterest = point
                    device.focusMode = .autoFocus
                }
                if device.isExposurePointOfInterestSupported && device.isExposureModeSupported(.continuousAutoExposure) {
                    device.exposurePointOfInterest = point
                    device.exposureMode = .continuousAutoExposure
                }
                device.unlockForConfiguration()
                DispatchQueue.main.async {
                    self.isManualFocus = false
                    self.isInfinityFocus = false
                }
            } catch {
                print("Failed to tap to focus: \(error)")
            }
        }
    }

    // MARK: - Video Recording

    public func startRecordingVideo(to url: URL) {
        videoDataOutputQueue.async { [weak self] in
            guard let self = self, !self.isRecordingVideo else { return }
            try? FileManager.default.removeItem(at: url)
            do {
                let writer = try AVAssetWriter(outputURL: url, fileType: .mp4)

                let videoSettings: [String: Any] = [
                    AVVideoCodecKey: AVVideoCodecType.h264,
                    AVVideoWidthKey: 1080,
                    AVVideoHeightKey: 1920
                ]
                let vInput = AVAssetWriterInput(mediaType: .video, outputSettings: videoSettings)
                vInput.expectsMediaDataInRealTime = true
                if writer.canAdd(vInput) {
                    writer.add(vInput)
                    self.assetWriterVideoInput = vInput
                }

                if self.audioInput != nil {
                    let audioSettings: [String: Any] = [
                        AVFormatIDKey: kAudioFormatMPEG4AAC,
                        AVNumberOfChannelsKey: 2,
                        AVSampleRateKey: 44100,
                        AVEncoderBitRateKey: 128000
                    ]
                    let aInput = AVAssetWriterInput(mediaType: .audio, outputSettings: audioSettings)
                    aInput.expectsMediaDataInRealTime = true
                    if writer.canAdd(aInput) {
                        writer.add(aInput)
                        self.assetWriterAudioInput = aInput
                    }
                }

                guard writer.startWriting() else {
                    print("AVAssetWriter failed to start writing: \(writer.error?.localizedDescription ?? "unknown")")
                    return
                }
                self.assetWriter = writer
                self.recordingStartTime = nil
                self.currentVideoURL = url

                DispatchQueue.main.async {
                    self.isRecordingVideo = true
                    self.recordingSeconds = 0
                }
            } catch {
                print("Failed to initialize AVAssetWriter: \(error)")
            }
        }
    }

    public func stopRecordingVideo(completion: @escaping (URL?) -> Void) {
        videoDataOutputQueue.async { [weak self] in
            guard let self = self, self.isRecordingVideo, let writer = self.assetWriter else {
                DispatchQueue.main.async { completion(nil) }
                return
            }

            guard writer.status == .writing else {
                self.assetWriter = nil
                self.assetWriterVideoInput = nil
                self.assetWriterAudioInput = nil
                self.recordingStartTime = nil
                DispatchQueue.main.async {
                    self.isRecordingVideo = false
                    completion(nil)
                }
                return
            }

            self.assetWriterVideoInput?.markAsFinished()
            self.assetWriterAudioInput?.markAsFinished()

            writer.finishWriting {
                let outputURL = (writer.status == .completed) ? self.currentVideoURL : nil
                self.assetWriter = nil
                self.assetWriterVideoInput = nil
                self.assetWriterAudioInput = nil
                self.recordingStartTime = nil

                DispatchQueue.main.async {
                    self.isRecordingVideo = false
                    completion(outputURL)
                }
            }
        }
    }
}

// MARK: - AVCaptureVideoDataOutputSampleBufferDelegate

extension AVCameraManager: AVCaptureVideoDataOutputSampleBufferDelegate, AVCaptureAudioDataOutputSampleBufferDelegate {
    public func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        if output == videoOutput {
            // Forward video sample buffer to Metal renderer
            frameDelegate?.cameraManager(self, didOutput: sampleBuffer)

            // Write to video if recording
            if isRecordingVideo, let writer = assetWriter, writer.status == .writing {
                let timestamp = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
                if recordingStartTime == nil {
                    recordingStartTime = timestamp
                    writer.startSession(atSourceTime: timestamp)
                }
                if let vInput = assetWriterVideoInput, vInput.isReadyForMoreMediaData {
                    vInput.append(sampleBuffer)
                }
            }
        } else if output == audioOutput {
            if isRecordingVideo, let writer = assetWriter, writer.status == .writing, recordingStartTime != nil {
                if let aInput = assetWriterAudioInput, aInput.isReadyForMoreMediaData {
                    aInput.append(sampleBuffer)
                }
            }
        }
    }
}
