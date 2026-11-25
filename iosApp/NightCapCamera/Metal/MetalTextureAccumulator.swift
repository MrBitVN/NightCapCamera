import Foundation
import Metal
import CoreImage
import CoreGraphics
import UIKit

public final class MetalTextureAccumulator {
    private let device: MTLDevice

    public private(set) var width: Int = 1080
    public private(set) var height: Int = 1920

    // Double buffering ping-pong textures in 16-bit float
    public private(set) var accumulatorTextureA: MTLTexture?
    public private(set) var accumulatorTextureB: MTLTexture?

    // Previous frame texture for Meteor mode frame differencing
    public private(set) var prevFrameTexture: MTLTexture?

    private var useTextureAAsSource: Bool = true

    public init(device: MTLDevice) {
        self.device = device
        recreateTextures(width: 1080, height: 1920)
    }

    public func setDimensions(width: Int, height: Int) {
        guard width > 0, height > 0 else { return }
        guard self.width != width || self.height != height else { return }
        self.width = width
        self.height = height
        recreateTextures(width: width, height: height)
    }

    public func recreateTextures(width: Int, height: Int) {
        let desc = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: .rgba16Float,
            width: width,
            height: height,
            mipmapped: false
        )
        desc.usage = [.shaderRead, .shaderWrite, .renderTarget]
        desc.storageMode = .shared

        accumulatorTextureA = device.makeTexture(descriptor: desc)
        accumulatorTextureB = device.makeTexture(descriptor: desc)

        let prevDesc = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: .bgra8Unorm,
            width: width,
            height: height,
            mipmapped: false
        )
        prevDesc.usage = [.shaderRead, .shaderWrite]
        prevDesc.storageMode = .shared
        prevFrameTexture = device.makeTexture(descriptor: prevDesc)
        useTextureAAsSource = true

        clearAccumulator()
    }

    public func clearAccumulator() {
        guard width > 0, height > 0 else { return }
        let bytesPerRow = width * 8 // 4 channels * 2 bytes (Float16)
        let byteCount = bytesPerRow * height
        let zeros = [UInt8](repeating: 0, count: byteCount)
        let region = MTLRegionMake2D(0, 0, width, height)

        accumulatorTextureA?.replace(region: region, mipmapLevel: 0, withBytes: zeros, bytesPerRow: bytesPerRow)
        accumulatorTextureB?.replace(region: region, mipmapLevel: 0, withBytes: zeros, bytesPerRow: bytesPerRow)

        let prevBytesPerRow = width * 4
        let prevZeros = [UInt8](repeating: 0, count: prevBytesPerRow * height)
        prevFrameTexture?.replace(region: region, mipmapLevel: 0, withBytes: prevZeros, bytesPerRow: prevBytesPerRow)
        useTextureAAsSource = true
    }

    public var currentSourceTexture: MTLTexture? {
        useTextureAAsSource ? accumulatorTextureA : accumulatorTextureB
    }

    public var currentDestinationTexture: MTLTexture? {
        useTextureAAsSource ? accumulatorTextureB : accumulatorTextureA
    }

    public func swapBuffers() {
        useTextureAAsSource.toggle()
    }

    public func exportUIImage(ciContext: CIContext) -> UIImage? {
        guard let source = currentSourceTexture else { return nil }
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let ciImage = CIImage(mtlTexture: source, options: [.colorSpace: colorSpace]) else {
            return nil
        }
        // Metal texture origin is top-left while CIImage origin is bottom-left
        let flipped = ciImage
            .transformed(by: CGAffineTransform(scaleX: 1, y: -1).translatedBy(x: 0, y: -ciImage.extent.height))
        guard let cgImage = ciContext.createCGImage(flipped, from: flipped.extent) else {
            return nil
        }
        return UIImage(cgImage: cgImage, scale: 1.0, orientation: .up)
    }
}
