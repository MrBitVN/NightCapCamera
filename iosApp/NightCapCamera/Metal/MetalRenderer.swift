import Foundation
import Metal
import MetalKit
import CoreMedia
import CoreVideo
import CoreImage
import UIKit

public final class MetalRenderer: NSObject, MTKViewDelegate, CameraFrameDelegate, ObservableObject {
    public static let shared = MetalRenderer()

    public let device: MTLDevice
    private let commandQueue: MTLCommandQueue
    private var textureCache: CVMetalTextureCache?
    private let ciContext: CIContext

    // Texture Accumulator
    public let accumulator: MetalTextureAccumulator

    // Compute and Render pipelines
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
    private var needsClearAccumulator: Bool = false
    private let frameLock = NSLock()

    // Fullscreen quad vertices (triangle strip: x, y, u, v)
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
        self.ciContext = CIContext(mtlDevice: dev)
        self.accumulator = MetalTextureAccumulator(device: dev)

        super.init()

        CVMetalTextureCacheCreate(kCFAllocatorDefault, nil, dev, nil, &textureCache)
        setupPipelines()
        setupBuffers()
    }

    private func setupPipelines() {
        guard let library = device.makeDefaultLibrary() else {
            print("MetalRenderer Error: Failed to create default Metal library")
            return
        }

        // Sampler
        let samplerDesc = MTLSamplerDescriptor()
        samplerDesc.minFilter = .linear
        samplerDesc.magFilter = .linear
        samplerDesc.sAddressMode = .clampToEdge
        samplerDesc.tAddressMode = .clampToEdge
        samplerState = device.makeSamplerState(descriptor: samplerDesc)

        // Vertex Descriptor matching VertexIn in Shaders.metal
        let vertexDescriptor = MTLVertexDescriptor()
        // attribute(0): float4 position (fed by float2 x, y)
        vertexDescriptor.attributes[0].format = .float2
        vertexDescriptor.attributes[0].offset = 0
        vertexDescriptor.attributes[0].bufferIndex = 0
        // attribute(1): float2 texCoords (u, v)
        vertexDescriptor.attributes[1].format = .float2
        vertexDescriptor.attributes[1].offset = MemoryLayout<Float>.stride * 2
        vertexDescriptor.attributes[1].bufferIndex = 0
        // layout(0)
        vertexDescriptor.layouts[0].stride = MemoryLayout<Float>.stride * 4
        vertexDescriptor.layouts[0].stepFunction = .perVertex

        // Display pipeline
        let displayDesc = MTLRenderPipelineDescriptor()
        displayDesc.vertexFunction = library.makeFunction(name: "passthrough_vertex")
        displayDesc.fragmentFunction = library.makeFunction(name: "display_fragment")
        displayDesc.vertexDescriptor = vertexDescriptor
        displayDesc.colorAttachments[0].pixelFormat = .bgra8Unorm

        do {
            displayRenderPipeline = try device.makeRenderPipelineState(descriptor: displayDesc)
        } catch {
            print("MetalRenderer Error: Failed to create displayRenderPipeline: \(error)")
        }

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
        frameLock.lock()
        frameCount = 0
        isStackingActive = true
        needsClearAccumulator = true
        frameLock.unlock()
        IOSGyroStabilizer.shared.lockReferenceOrientation()
    }

    public func stopStacking(completion: @escaping (UIImage?) -> Void) {
        frameLock.lock()
        isStackingActive = false
        frameLock.unlock()
        IOSGyroStabilizer.shared.resetReference()

        let boost = self.isoBoost
        let redVision = self.isRedVision

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else {
                DispatchQueue.main.async { completion(nil) }
                return
            }
            // Wait for any in-flight GPU work to finish before reading the texture
            if let syncBuffer = self.commandQueue.makeCommandBuffer() {
                syncBuffer.commit()
                syncBuffer.waitUntilCompleted()
            }

            var image = self.accumulator.exportUIImage(ciContext: self.ciContext)
            if let img = image, (boost > 1.0 || redVision) {
                image = self.applyDisplayEffects(to: img, isoBoost: boost, isRedVision: redVision)
            }
            DispatchQueue.main.async {
                completion(image)
            }
        }
    }

    public func resetStacking() {
        frameLock.lock()
        isStackingActive = false
        frameCount = 0
        needsClearAccumulator = true
        frameLock.unlock()
        IOSGyroStabilizer.shared.resetReference()
    }

    public func captureSinglePhoto(completion: @escaping (UIImage?) -> Void) {
        frameLock.lock()
        let pixelBuffer = latestPixelBuffer
        frameLock.unlock()

        guard let pb = pixelBuffer else {
            DispatchQueue.main.async { completion(nil) }
            return
        }

        let boost = self.isoBoost
        let redVision = self.isRedVision

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else {
                DispatchQueue.main.async { completion(nil) }
                return
            }

            var ciImage = CIImage(cvPixelBuffer: pb)

            // Apply digital ISO boost if > 1.0
            if boost > 1.0 {
                let exposureEV = log2(boost)
                if let filter = CIFilter(name: "CIExposureAdjust") {
                    filter.setValue(ciImage, forKey: kCIInputImageKey)
                    filter.setValue(exposureEV, forKey: kCIInputEVKey)
                    if let output = filter.outputImage {
                        ciImage = output
                    }
                }
            }

            // Apply Astronomy Red Vision filter if enabled
            if redVision {
                if let mono = CIFilter(name: "CIColorMatrix") {
                    mono.setValue(ciImage, forKey: kCIInputImageKey)
                    mono.setValue(CIVector(x: 0.299, y: 0.587, z: 0.114, w: 0), forKey: "inputRVector")
                    mono.setValue(CIVector(x: 0, y: 0, z: 0, w: 0), forKey: "inputGVector")
                    mono.setValue(CIVector(x: 0, y: 0, z: 0, w: 0), forKey: "inputBVector")
                    mono.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
                    if let output = mono.outputImage {
                        ciImage = output
                    }
                }
            }

            guard let cgImage = self.ciContext.createCGImage(ciImage, from: ciImage.extent) else {
                DispatchQueue.main.async { completion(nil) }
                return
            }

            let uiImage = UIImage(cgImage: cgImage, scale: 1.0, orientation: .up)
            DispatchQueue.main.async {
                completion(uiImage)
            }
        }
    }

    private func applyDisplayEffects(to image: UIImage, isoBoost: Float, isRedVision: Bool) -> UIImage? {
        guard let cg = image.cgImage else { return image }
        var ciImage = CIImage(cgImage: cg)

        if isoBoost > 1.0 {
            let exposureEV = log2(isoBoost)
            if let filter = CIFilter(name: "CIExposureAdjust") {
                filter.setValue(ciImage, forKey: kCIInputImageKey)
                filter.setValue(exposureEV, forKey: kCIInputEVKey)
                if let output = filter.outputImage {
                    ciImage = output
                }
            }
        }

        if isRedVision {
            if let mono = CIFilter(name: "CIColorMatrix") {
                mono.setValue(ciImage, forKey: kCIInputImageKey)
                mono.setValue(CIVector(x: 0.299, y: 0.587, z: 0.114, w: 0), forKey: "inputRVector")
                mono.setValue(CIVector(x: 0, y: 0, z: 0, w: 0), forKey: "inputGVector")
                mono.setValue(CIVector(x: 0, y: 0, z: 0, w: 0), forKey: "inputBVector")
                mono.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
                if let output = mono.outputImage {
                    ciImage = output
                }
            }
        }

        guard let outCG = ciContext.createCGImage(ciImage, from: ciImage.extent) else { return image }
        return UIImage(cgImage: outCG, scale: 1.0, orientation: .up)
    }

    // MARK: - CameraFrameDelegate

    public func cameraManager(_ manager: AVCameraManager, didOutput sampleBuffer: CMSampleBuffer) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        frameLock.lock()
        latestPixelBuffer = pixelBuffer
        frameLock.unlock()
    }

    // MARK: - MTKViewDelegate

    public func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
        // Handled dynamically in draw
    }

    public func draw(in view: MTKView) {
        guard let drawable = view.currentDrawable,
              let renderPassDesc = view.currentRenderPassDescriptor,
              let commandBuffer = commandQueue.makeCommandBuffer() else {
            return
        }

        frameLock.lock()
        let pixelBuffer = latestPixelBuffer
        let shouldClear = needsClearAccumulator
        let stackingActive = isStackingActive
        needsClearAccumulator = false
        frameLock.unlock()

        guard let pb = pixelBuffer, let cache = textureCache else {
            // Clear screen to black when waiting for first camera frame
            if let clearEncoder = commandBuffer.makeRenderCommandEncoder(descriptor: renderPassDesc) {
                clearEncoder.endEncoding()
            }
            commandBuffer.present(drawable)
            commandBuffer.commit()
            return
        }

        let w = CVPixelBufferGetWidth(pb)
        let h = CVPixelBufferGetHeight(pb)

        // Safely update accumulator dimensions and clear on render thread
        accumulator.setDimensions(width: w, height: h)
        if shouldClear {
            accumulator.clearAccumulator()
        }

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
        if stackingActive {
            frameCount += 1
            processStacking(commandBuffer: commandBuffer, currentTexture: cameraTexture)
            let countSnapshot = frameCount
            DispatchQueue.main.async { [weak self] in
                self?.onFrameProcessed?(countSnapshot)
            }
        }

        // Render to Screen Display
        let sourceTexture = stackingActive ? (accumulator.currentSourceTexture ?? cameraTexture) : cameraTexture
        renderToDisplay(
            commandBuffer: commandBuffer,
            renderPassDesc: renderPassDesc,
            texture: sourceTexture,
            applyStabilization: stackingActive
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

        // Save current frame for Meteor mode frame differencing on next frame
        if currentMode == .meteors,
           let prevFrameTex = accumulator.prevFrameTexture,
           let blitEncoder = commandBuffer.makeBlitCommandEncoder() {
            blitEncoder.copy(
                from: currentTexture,
                sourceSlice: 0,
                sourceLevel: 0,
                sourceOrigin: MTLOrigin(x: 0, y: 0, z: 0),
                sourceSize: MTLSize(width: currentTexture.width, height: currentTexture.height, depth: 1),
                to: prevFrameTex,
                destinationSlice: 0,
                destinationLevel: 0,
                destinationOrigin: MTLOrigin(x: 0, y: 0, z: 0)
            )
            blitEncoder.endEncoding()
        }

        accumulator.swapBuffers()
    }

    private func renderToDisplay(
        commandBuffer: MTLCommandBuffer,
        renderPassDesc: MTLRenderPassDescriptor,
        texture: MTLTexture,
        applyStabilization: Bool
    ) {
        guard let pipeline = displayRenderPipeline,
              let vBuf = vertexBuffer,
              let renderEncoder = commandBuffer.makeRenderCommandEncoder(descriptor: renderPassDesc) else {
            return
        }

        renderEncoder.setRenderPipelineState(pipeline)
        renderEncoder.setVertexBuffer(vBuf, offset: 0, index: 0)

        // Only apply gyro stabilization during active stacking so viewfinder stays centered
        let stabMatrix = applyStabilization ? IOSGyroStabilizer.shared.stabilizationMatrix : matrix_identity_float4x4

        var uniforms = Uniforms(
            stabilizationMatrix: stabMatrix,
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
