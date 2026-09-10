import Foundation
import CoreMotion
import simd

public final class IOSGyroStabilizer {
    public static let shared = IOSGyroStabilizer()

    private let motionManager = CMMotionManager()
    private let queue = OperationQueue()

    private let focalLengthScale: Float = 1.8

    public var isEnabled: BooleanLiteralType = true

    // Stabilization matrix for Metal (column-major simd_float4x4)
    public private(set) var stabilizationMatrix: simd_float4x4 = matrix_identity_float4x4

    private var referenceAttitude: CMAttitude?
    private var isCalibrated: Bool = false

    public init() {
        queue.qualityOfService = .userInteractive
        queue.maxConcurrentOperationCount = 1
    }

    public func start() {
        guard motionManager.isDeviceMotionAvailable else { return }
        isCalibrated = false
        stabilizationMatrix = matrix_identity_float4x4

        motionManager.deviceMotionUpdateInterval = 1.0 / 60.0
        motionManager.startDeviceMotionUpdates(using: .xArbitraryZVertical, to: queue) { [weak self] motion, error in
            guard let self = self, let motion = motion, self.isEnabled else { return }
            self.processMotion(motion)
        }
    }

    public func stop() {
        motionManager.stopDeviceMotionUpdates()
        isCalibrated = false
        stabilizationMatrix = matrix_identity_float4x4
    }

    public func lockReferenceOrientation() {
        if let current = motionManager.deviceMotion?.attitude {
            referenceAttitude = current.copy() as? CMAttitude
            isCalibrated = true
            stabilizationMatrix = matrix_identity_float4x4
        }
    }

    public func resetReference() {
        referenceAttitude = nil
        isCalibrated = false
        stabilizationMatrix = matrix_identity_float4x4
    }

    private func processMotion(_ motion: CMDeviceMotion) {
        let currentAttitude = motion.attitude

        if !isCalibrated || referenceAttitude == nil {
            referenceAttitude = currentAttitude.copy() as? CMAttitude
            isCalibrated = true
            stabilizationMatrix = matrix_identity_float4x4
            return
        }

        guard let ref = referenceAttitude else { return }

        // Compute relative rotation
        let deltaRoll = Float(currentAttitude.roll - ref.roll)
        let deltaPitch = Float(currentAttitude.pitch - ref.pitch)
        let deltaYaw = Float(currentAttitude.yaw - ref.yaw)

        // Handheld vibration offset
        let shiftX = -deltaRoll * focalLengthScale
        let shiftY = deltaPitch * focalLengthScale
        let angle = -deltaYaw

        // Build 4x4 transform matrix: Translate(0.5, 0.5) * Rotate(Z, angle) * Translate(shiftX, shiftY) * Translate(-0.5, -0.5)
        let cosA = cos(angle)
        let sinA = sin(angle)

        // Rotation around Z at center (0.5, 0.5)
        var m = matrix_identity_float4x4
        m.columns.0 = simd_float4(cosA, sinA, 0, 0)
        m.columns.1 = simd_float4(-sinA, cosA, 0, 0)
        m.columns.2 = simd_float4(0, 0, 1, 0)
        m.columns.3 = simd_float4(
            0.5 * (1 - cosA) + 0.5 * sinA + shiftX,
            0.5 * (1 - cosA) - 0.5 * sinA + shiftY,
            0,
            1
        )

        self.stabilizationMatrix = m
    }
}
