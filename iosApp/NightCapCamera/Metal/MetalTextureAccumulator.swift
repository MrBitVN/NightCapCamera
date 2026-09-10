import Foundation
import Metal
import CoreGraphics
import UIKit

public final class MetalTextureAccumulator {
    private let device: MTLDevice

    public private(set) var width: Int = 1920
    public private(set) var height: Int = 1080

    // Double buffering ping-pong textures in 16-bit float
    public private(set) var accumulatorTextureA: MTLTexture?
    public private(set) var accumulatorTextureB: MTLTexture?

    // Previous frame texture for Meteor mode frame differencing
    public private(set) var prevFrameTexture: MTLTexture?

    private var useTextureAAsSource: Bool = true

    public init(device: MTLDevice) {
        self.device = device
        recreateTextures(width: 1920, height: 1080)
    }

    public func setDimensions(width: Int, height: Int) {
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
        prevFrameTexture = device.makeTexture(descriptor: desc)
        useTextureAAsSource = true

        clearAccumulator()
    }

    public func clearAccumulator() {
        guard let texA = accumulatorTextureA, let texB = accumulatorTextureB else { return }
        let byteCount = width * height * 8 // 4 channels * 2 bytes (Float16)
        let zeros = [UInt8](repeating: 0, count: byteCount)
        texA.replace(region: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0, withBytes: zeros, bytesPerRow: width * 8)
        texB.replace(region: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0, withBytes: zeros, bytesPerRow: width * 8)
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

    public func exportUIImage() -> UIImage? {
        guard let source = currentSourceTexture else { return nil }

        let bytesPerRow = width * 4
        var rawData = [UInt8](repeating: 0, count: width * height * 4)

        // Read texture pixels
        // Convert from RGBA16Float to standard 8-bit sRGB
        let floatBytesPerRow = width * 8
        var floatData = [Float16](repeating: 0, count: width * height * 4)
        source.getBytes(&floatData, bytesPerRow: floatBytesPerRow, from: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0)

        for i in 0..<(width * height * 4) {
            let val = Float(floatData[i])
            let clamped = min(max(val, 0.0), 1.0)
            rawData[i] = UInt8(clamped * 255.0)
        }

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue)

        guard let provider = CGDataProvider(data: Data(rawData) as CFData),
              let cgImage = CGImage(
                width: width,
                height: height,
                bitsPerComponent: 8,
                bitsPerPixel: 32,
                bytesPerRow: bytesPerRow,
                space: colorSpace,
                bitmapInfo: bitmapInfo,
                provider: provider,
                decode: nil,
                shouldInterpolate: true,
                intent: .defaultIntent
              ) else {
            return nil
        }

        return UIImage(cgImage: cgImage, scale: 1.0, orientation: .up)
    }
}
