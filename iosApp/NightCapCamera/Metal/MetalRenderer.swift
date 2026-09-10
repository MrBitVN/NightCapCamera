import Foundation
import Metal
import MetalKit
import CoreMedia
import CoreVideo
import UIKit

public final class MetalRenderer: NSObject, MTKViewDelegate, CameraFrameDelegate, ObservableObject {
    public static let shared = MetalRenderer()

    public let device: MTLDevice
    private let commandQueue: MTLCommandQueue
    private var textureCache: CVMetalTextureCache?

    // Texture Accumulator
    public let accumulator: MetalTextureAccumulator

    // Compute and Render pipelines
    private var inputRenderPipeline: MTLRenderPipelineState?
    private var displayRenderPipeline: MTLRenderPipelineState?
    private var averageComputePipeline: MTLComputePipelineState?
    private var maxComputePipeline: MTLComputePipelineState?
    private var meteorComputePipeline: MTLComputePipelineState?

    private var samplerState: MTLSamplerState?
    private var vertexBuffer: MTLBuffer?

    // State & Properties
    public var currentMode: StackingMode = .photo
    public var isoBoost: Float = 1.0
    public var isRedVision: Bool = false
    public var isStackingActive: Bool = false
    public private(set) var frameCount: Int = 0

    // Callbacks
    public var onFrameProcessed: ((Int) -> Void)?

    private var latestPixelBuffer: CVPixelBuffer?
    private let frameLock = NSLock()

    // Fullscreen quad vertices (2 triangles)
    private let quadVertices: [Float] = [
        // x,    y,    u,    v
        -1.0, -1.0,  0.0,  1.0,
         1.0, -1.0,  1.0,  1.0,
        -1.0,  1.0,  0.0,  0.0,
         1.0,  1.0,  1.0,  0.0
    ]

    public override init() {
        guard let dev = MTLCreateSystemDefaultDevice(),
              let queue = dev.makeCommandQueue() else {
            fatalError("Metal is not supported on this device")
        }
        self.device = dev
        self.commandQueue = queue
        self.accumulator = MetalTextureAccumulator(device: dev)

        super.init()

        CVMetalTextureCacheCreate(kCFAllocatorDefault, nil, dev, nil, &textureCache)
        setupPipelines()
        setupBuffers()
    }

    private func setupPipelines() {
        guard let library = device.makeDefaultLibrary() else {
            print("Failed to create default Metal library")
            return
        }

        // Sampler
        let samplerDesc = MTLSamplerDescriptor()
        samplerDesc.minFilter = .linear
        samplerDesc.magFilter = .linear
        samplerDesc.sAddressMode = .clampToEdge
        samplerDesc.tAddressMode = .clampToEdge
        samplerState = device.makeSamplerState(descriptor: samplerDesc)

        // Display pipeline
        let displayDesc = MTLRenderPipelineDescriptor()
        displayDesc.vertexFunction = library.makeFunction(name: "passthrough_vertex")
        displayDesc.fragmentFunction = library.makeFunction(name: "display_fragment")
        displayDesc.colorAttachments[0].pixelFormat = .bgra8Unorm
        displayRenderPipeline = try? device.makeRenderPipelineState(descriptor: displayDesc)

        // Compute pipelines
        if let avgFunc = library.makeFunction(name: "stack_average_kernel") {
            averageComputePipeline = try? device.makeComputePipelineState(function: avgFunc)
        }
        if let maxFunc = library.makeFunction(name: "stack_max_kernel") {
            maxComputePipeline = try? device.makeComputePipelineState(function: maxFunc)
        }
        if let meteorFunc = library.makeFunction(name: "stack_meteor_kernel") {
            meteorComputePipeline = try? device.makeComputePipelineState(function: meteorFunc)
        }
    }

    private func setupBuffers() {
        vertexBuffer = device.makeBuffer(
            bytes: quadVertices,
            length: quadVertices.count * MemoryLayout<Float>.size,
            options: .storageModeShared
        )
    }

    public func startStacking() {
        frameCount = 0
        isStackingActive = true
        accumulator.clearAccumulator()
        IOSGyroStabilizer.shared.lockReferenceOrientation()
    }

    public func stopStacking(completion: @escaping (UIImage?) -> Void) {
        isStackingActive = false
        IOSGyroStabilizer.shared.resetReference()
        let image = accumulator.exportUIImage()
        completion(image)
    }

    public func resetStacking() {
        isStackingActive = false
        frameCount = 0
        accumulator.clearAccumulator()
        IOSGyroStabilizer.shared.resetReference()
    }

    public func captureSinglePhoto(completion: @escaping (UIImage?) -> Void) {
        let image = accumulator.exportUIImage()
        completion(image)
    }

    // MARK: - CameraFrameDelegate

    public func cameraManager(_ manager: AVCameraManager, didOutput sampleBuffer: CMSampleBuffer) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        frameLock.lock()
        latestPixelBuffer = pixelBuffer
        frameLock.unlock()

        let w = CVPixelBufferGetWidth(pixelBuffer)
        let h = CVPixelBufferGetHeight(pixelBuffer)
        accumulator.setDimensions(width: w, height: h)
    }

    // MARK: - MTKViewDelegate

    public func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
        // Handle resize if needed
    }

    public func draw(in view: MTKView) {
        guard let drawable = view.currentDrawable,
              let renderPassDesc = view.currentRenderPassDescriptor,
              let commandBuffer = commandQueue.makeCommandBuffer() else {
            return
        }

        frameLock.lock()
        let pixelBuffer = latestPixelBuffer
        frameLock.unlock()

        guard let pb = pixelBuffer, let cache = textureCache else {
            commandBuffer.present(drawable)
            commandBuffer.commit()
            return
        }

        let w = CVPixelBufferGetWidth(pb)
        let h = CVPixelBufferGetHeight(pb)

        // Create Metal texture from CVPixelBuffer directly without CPU copying
        var cvMetalTexture: CVMetalTexture?
        CVMetalTextureCacheCreateTextureFromImage(
            kCFAllocatorDefault,
            cache,
            pb,
            nil,
            .bgra8Unorm,
            w,
            h,
            0,
            &cvMetalTexture
        )

        guard let cvTex = cvMetalTexture, let cameraTexture = CVMetalTextureGetTexture(cvTex) else {
            commandBuffer.present(drawable)
            commandBuffer.commit()
            return
        }

        // Process Stacking Compute Pass
        if isStackingActive {
            frameCount += 1
            processStacking(commandBuffer: commandBuffer, currentTexture: cameraTexture)
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                self.onFrameProcessed?(self.frameCount)
            }
        }

        // Render to Screen Display
        let sourceTexture = isStackingActive ? (accumulator.currentSourceTexture ?? cameraTexture) : cameraTexture
        renderToDisplay(
            commandBuffer: commandBuffer,
            renderPassDesc: renderPassDesc,
            drawable: drawable,
            texture: sourceTexture
        )

        commandBuffer.present(drawable)
        commandBuffer.commit()
    }

    private func processStacking(commandBuffer: MTLCommandBuffer, currentTexture: MTLTexture) {
        guard let prevSource = accumulator.currentSourceTexture,
              let nextDest = accumulator.currentDestinationTexture,
              let computeEncoder = commandBuffer.makeComputeCommandEncoder() else {
            return
        }

        let pipeline: MTLComputePipelineState?
        switch currentMode {
        case .stars:
            pipeline = averageComputePipeline
        case .starTrails, .lightTrails:
            pipeline = maxComputePipeline
        case .meteors:
            pipeline = meteorComputePipeline
        default:
            pipeline = nil
        }

        guard let cp = pipeline else {
            computeEncoder.endEncoding()
            return
        }

        computeEncoder.setComputePipelineState(cp)
        computeEncoder.setTexture(currentTexture, index: 0)

        if currentMode == .meteors {
            computeEncoder.setTexture(accumulator.prevFrameTexture ?? currentTexture, index: 1)
            computeEncoder.setTexture(prevSource, index: 2)
            computeEncoder.setTexture(nextDest, index: 3)
        } else {
            computeEncoder.setTexture(prevSource, index: 1)
            computeEncoder.setTexture(nextDest, index: 2)
        }

        var count = Int32(frameCount)
        computeEncoder.setBytes(&count, length: MemoryLayout<Int32>.size, index: 0)

        let threadgroupSize = MTLSize(width: 16, height: 16, depth: 1)
        let threadgroups = MTLSize(
            width: (currentTexture.width + 15) / 16,
            height: (currentTexture.height + 15) / 16,
            depth: 1
        )

        computeEncoder.dispatchThreadgroups(threadgroups, threadsPerThreadgroup: threadgroupSize)
        computeEncoder.endEncoding()

        accumulator.swapBuffers()
    }

    private func renderToDisplay(
        commandBuffer: MTLCommandBuffer,
        renderPassDesc: MTLRenderPassDescriptor,
        drawable: CAMetalDrawable,
        texture: MTLTexture
    ) {
        guard let pipeline = displayRenderPipeline,
              let vBuf = vertexBuffer,
              let renderEncoder = commandBuffer.makeRenderCommandEncoder(descriptor: renderPassDesc) else {
            return
        }

        renderEncoder.setRenderPipelineState(pipeline)
        renderEncoder.setVertexBuffer(vBuf, offset: 0, index: 0)

        // Uniforms
        var uniforms = Uniforms(
            stabilizationMatrix: IOSGyroStabilizer.shared.stabilizationMatrix,
            aspectMatrix: matrix_identity_float4x4,
            isoBoost: isoBoost,
            isRedVision: isRedVision ? 1 : 0,
            frameCount: Int32(frameCount)
        )
        renderEncoder.setVertexBytes(&uniforms, length: MemoryLayout<Uniforms>.size, index: 1)
        renderEncoder.setFragmentBytes(&uniforms, length: MemoryLayout<Uniforms>.size, index: 1)

        renderEncoder.setFragmentTexture(texture, index: 0)
        renderEncoder.setFragmentSamplerState(samplerState, index: 0)

        renderEncoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
        renderEncoder.endEncoding()
    }
}

private struct Uniforms {
    var stabilizationMatrix: simd_float4x4
    var aspectMatrix: simd_float4x4
    var isoBoost: Float
    var isRedVision: Int32
    var frameCount: Int32
}
