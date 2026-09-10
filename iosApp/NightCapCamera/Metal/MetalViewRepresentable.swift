import SwiftUI
import MetalKit

public struct MetalViewRepresentable: UIViewRepresentable {
    @ObservedObject var renderer: MetalRenderer = MetalRenderer.shared

    public init() {}

    public func makeUIView(context: Context) -> MTKView {
        let mtkView = MTKView()
        mtkView.device = renderer.device
        mtkView.delegate = renderer
        mtkView.colorPixelFormat = .bgra8Unorm
        mtkView.framebufferOnly = false
        mtkView.preferredFramesPerSecond = 60
        mtkView.enableSetNeedsDisplay = false
        mtkView.isPaused = false
        mtkView.backgroundColor = .black
        return mtkView
    }

    public func updateUIView(_ uiView: MTKView, context: Context) {
        // Updated by MTKViewDelegate
    }
}
