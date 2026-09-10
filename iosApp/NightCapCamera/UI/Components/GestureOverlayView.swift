import SwiftUI

public struct GestureOverlayView<Content: View>: View {
    @Binding var currentZoom: CGFloat
    let minZoom: CGFloat
    let maxZoom: CGFloat
    let primaryColor: Color
    let onTapToFocus: (CGPoint) -> Void
    let onZoomChanged: (CGFloat) -> Void
    let content: () -> Content

    @State private var focusPoint: CGPoint?
    @State private var focusScale: CGFloat = 1.4
    @State private var showZoomBadge: Bool = false
    @State private var hideBadgeTask: Task<Void, Never>?
    @GestureState private var pinchScale: CGFloat = 1.0
    @State private var baseZoom: CGFloat = 1.0

    public init(
        currentZoom: Binding<CGFloat>,
        minZoom: CGFloat = 1.0,
        maxZoom: CGFloat = 10.0,
        primaryColor: Color,
        onTapToFocus: @escaping (CGPoint) -> Void,
        onZoomChanged: @escaping (CGFloat) -> Void,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self._currentZoom = currentZoom
        self.minZoom = minZoom
        self.maxZoom = maxZoom
        self.primaryColor = primaryColor
        self.onTapToFocus = onTapToFocus
        self.onZoomChanged = onZoomChanged
        self.content = content
    }

    public var body: some View {
        GeometryReader { geometry in
            ZStack {
                content()

                // Tap to Focus Ring
                if let point = focusPoint {
                    Circle()
                        .stroke(primaryColor, lineWidth: 2)
                        .frame(width: 64, height: 64)
                        .scaleEffect(focusScale)
                        .position(point)
                        .transition(.opacity)
                }

                // Floating Zoom HUD Pill
                if showZoomBadge || currentZoom > 1.05 {
                    VStack {
                        Spacer()

                        HStack(spacing: 6) {
                            // 1x preset
                            ZoomChip(
                                title: "1x",
                                isSelected: abs(currentZoom - 1.0) < 0.1,
                                primaryColor: primaryColor
                            ) {
                                selectZoom(1.0)
                            }

                            // Fractional zoom badge when not close to presets
                            let isNearPreset = abs(currentZoom - 1.0) < 0.1 ||
                                               abs(currentZoom - 2.0) < 0.1 ||
                                               (maxZoom >= 5.0 && abs(currentZoom - 5.0) < 0.1)

                            if !isNearPreset {
                                Text(String(format: "%.1fx", currentZoom))
                                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                                    .foregroundColor(primaryColor)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(primaryColor.opacity(0.35))
                                    .clipShape(Capsule())
                                    .overlay(Capsule().stroke(primaryColor, lineWidth: 1))
                            }

                            // 2x preset
                            if maxZoom >= 2.0 {
                                ZoomChip(
                                    title: "2x",
                                    isSelected: abs(currentZoom - 2.0) < 0.1,
                                    primaryColor: primaryColor
                                ) {
                                    selectZoom(2.0)
                                }
                            }

                            // 5x preset
                            if maxZoom >= 5.0 {
                                ZoomChip(
                                    title: "5x",
                                    isSelected: abs(currentZoom - 5.0) < 0.1,
                                    primaryColor: primaryColor
                                ) {
                                    selectZoom(5.0)
                                }
                            }
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Color.black.opacity(0.75))
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(primaryColor.opacity(0.5), lineWidth: 1))
                        .padding(.bottom, 120)
                        .transition(.scale(scale: 0.85).combined(with: .opacity))
                    }
                }
            }
            .contentShape(Rectangle())
            // Single-finger Tap to Focus
            .onTapGesture { location in
                let normX = location.x / geometry.size.width
                let normY = location.y / geometry.size.height

                focusPoint = location
                focusScale = 1.4
                withAnimation(.easeOut(duration: 0.25)) {
                    focusScale = 1.0
                }
                onTapToFocus(CGPoint(x: normX, y: normY))

                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    withAnimation {
                        focusPoint = nil
                    }
                }
            }
            // 2-finger Pinch to Zoom
            .simultaneousGesture(
                MagnificationGesture()
                    .onChanged { scale in
                        let target = min(max(baseZoom * scale, minZoom), maxZoom)
                        currentZoom = target
                        onZoomChanged(target)
                        showZoomBadge = true
                        hideBadgeTask?.cancel()
                    }
                    .onEnded { _ in
                        baseZoom = currentZoom
                        scheduleBadgeHide()
                    }
            )
        }
    }

    private func selectZoom(_ zoom: CGFloat) {
        currentZoom = zoom
        baseZoom = zoom
        onZoomChanged(zoom)
        scheduleBadgeHide()
    }

    private func scheduleBadgeHide() {
        hideBadgeTask?.cancel()
        hideBadgeTask = Task {
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            withAnimation {
                showZoomBadge = false
            }
        }
    }
}

private struct ZoomChip: View {
    let title: String
    let isSelected: Bool
    let primaryColor: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(isSelected ? .black : .white)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(isSelected ? primaryColor : Color.white.opacity(0.12))
                .clipShape(Capsule())
        }
    }
}
