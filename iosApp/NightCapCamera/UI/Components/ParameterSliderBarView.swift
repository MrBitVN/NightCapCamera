import SwiftUI

public enum ActiveParameter {
    case none
    case shutter
    case iso
    case focus
    case ev
}

public struct ParameterSliderBarView: View {
    let activeParam: ActiveParameter
    let primaryColor: Color

    // Shutter
    let exposureTimeNs: Int64
    let minExposureNs: Int64
    let maxExposureNs: Int64
    let isManualExposure: Bool
    let onExposureChanged: (Int64) -> Void
    let onAutoExposure: () -> Void

    // ISO
    let currentISO: Float
    let minISO: Float
    let maxISO: Float
    let onISOChanged: (Float) -> Void

    // Focus
    let focusDistance: Float
    let isManualFocus: Bool
    let isInfinityFocus: Bool
    let onFocusDistanceChanged: (Float) -> Void
    let onAutoFocus: () -> Void
    let onInfinityFocus: () -> Void

    // EV
    let evCompensation: Float
    let minEV: Float
    let maxEV: Float
    let onEVChanged: (Float) -> Void

    let onDismiss: () -> Void

    public var body: some View {
        if activeParam != .none {
            VStack(spacing: 8) {
                switch activeParam {
                case .shutter:
                    let seconds = Double(exposureTimeNs) / 1_000_000_000.0
                    let displayValue = !isManualExposure ? "AUTO" : (seconds >= 1.0 ? String(format: "%.2fs", seconds) : "1/\(Int(round(1.0 / seconds)))s")

                    SliderHeader(
                        systemImage: "gauge.with.dots.needle.bottom.50percent",
                        title: "SHUTTER SPEED",
                        valueText: displayValue,
                        isAuto: !isManualExposure,
                        primaryColor: primaryColor,
                        onAutoClick: onAutoExposure,
                        onCloseClick: onDismiss
                    )

                    // Logarithmic scale for Shutter speed
                    let minLog = log(max(Double(minExposureNs), 100_000.0))
                    let maxLog = log(max(Double(maxExposureNs), Double(minExposureNs) + 1.0))
                    let currentLog = log(Double(exposureTimeNs))
                    let fraction = (currentLog - minLog) / (maxLog - minLog)

                    VolumeSliderRow(
                        value: Binding(
                            get: { fraction },
                            set: { frac in
                                let targetLog = minLog + frac * (maxLog - minLog)
                                let targetNs = Int64(exp(targetLog))
                                onExposureChanged(min(max(targetNs, minExposureNs), maxExposureNs))
                            }
                        ),
                        leftLabel: "1/8000s",
                        rightLabel: "\(maxExposureNs / 1_000_000_000)s",
                        primaryColor: primaryColor,
                        onStepDown: {
                            onExposureChanged(max(Int64(Double(exposureTimeNs) / 1.5), minExposureNs))
                        },
                        onStepUp: {
                            onExposureChanged(min(Int64(Double(exposureTimeNs) * 1.5), maxExposureNs))
                        }
                    )

                case .iso:
                    let displayValue = !isManualExposure ? "AUTO" : "ISO \(Int(currentISO))"

                    SliderHeader(
                        systemImage: "sun.max.fill",
                        title: "ISO SENSITIVITY",
                        valueText: displayValue,
                        isAuto: !isManualExposure,
                        primaryColor: primaryColor,
                        onAutoClick: onAutoExposure,
                        onCloseClick: onDismiss
                    )

                    let minLog = log(Double(max(minISO, 50)))
                    let maxLog = log(Double(max(maxISO, 100)))
                    let currentLog = log(Double(currentISO))
                    let fraction = (currentLog - minLog) / (maxLog - minLog)

                    VolumeSliderRow(
                        value: Binding(
                            get: { fraction },
                            set: { frac in
                                let targetLog = minLog + frac * (maxLog - minLog)
                                let targetIso = Float(round(exp(targetLog) / 50.0) * 50.0)
                                onISOChanged(min(max(targetIso, minISO), maxISO))
                            }
                        ),
                        leftLabel: "\(Int(minISO))",
                        rightLabel: "\(Int(maxISO))",
                        primaryColor: primaryColor,
                        onStepDown: {
                            onISOChanged(max(currentISO - 100, minISO))
                        },
                        onStepUp: {
                            onISOChanged(min(currentISO + 100, maxISO))
                        }
                    )

                case .focus:
                    let displayValue = (!isManualFocus && !isInfinityFocus) ? "AUTO FOCUS (AF)" :
                                       (isInfinityFocus ? "∞ INFINITY (STARS)" : "MANUAL: \(String(format: "%.2f", focusDistance))")

                    SliderHeader(
                        systemImage: "camera.metering.spot",
                        title: "FOCUS DISTANCE",
                        valueText: displayValue,
                        isAuto: !isManualFocus && !isInfinityFocus,
                        primaryColor: primaryColor,
                        onAutoClick: onAutoFocus,
                        onCloseClick: onDismiss,
                        extraAction: {
                            Button(action: onInfinityFocus) {
                                Text("∞ INFINITY")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(isInfinityFocus ? .black : .white)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(isInfinityFocus ? primaryColor : Color.white.opacity(0.12))
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                            }
                        }
                    )

                    VolumeSliderRow(
                        value: Binding(
                            get: { Double(focusDistance) },
                            set: { onFocusDistanceChanged(Float($0)) }
                        ),
                        leftLabel: "∞ Infinity",
                        rightLabel: "Macro",
                        primaryColor: primaryColor,
                        onStepDown: {
                            onFocusDistanceChanged(max(focusDistance - 0.05, 0.0))
                        },
                        onStepUp: {
                            onFocusDistanceChanged(min(focusDistance + 0.05, 1.0))
                        }
                    )

                case .ev:
                    let displayValue = evCompensation == 0 ? "0.0 EV (STANDARD)" : String(format: "%+.1f EV", evCompensation)

                    SliderHeader(
                        systemImage: "plusminus.circle.fill",
                        title: "EXPOSURE COMPENSATION (EV)",
                        valueText: displayValue,
                        isAuto: evCompensation == 0,
                        primaryColor: primaryColor,
                        onAutoClick: { onEVChanged(0.0) },
                        onCloseClick: onDismiss
                    )

                    let totalRange = Double(maxEV - minEV)
                    let fraction = totalRange > 0 ? Double(evCompensation - minEV) / totalRange : 0.5

                    VolumeSliderRow(
                        value: Binding(
                            get: { fraction },
                            set: { frac in
                                let targetEV = Float(Double(minEV) + frac * totalRange)
                                onEVChanged(min(max(targetEV, minEV), maxEV))
                            }
                        ),
                        leftLabel: String(format: "%.1f", minEV),
                        rightLabel: String(format: "+%.1f", maxEV),
                        primaryColor: primaryColor,
                        onStepDown: {
                            onEVChanged(max(evCompensation - 0.5, minEV))
                        },
                        onStepUp: {
                            onEVChanged(min(evCompensation + 0.5, maxEV))
                        }
                    )

                case .none:
                    EmptyView()
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color(white: 0.08).opacity(0.95))
            .clipShape(RoundedRectangle(cornerRadius: 22))
            .overlay(RoundedRectangle(cornerRadius: 22).stroke(primaryColor.opacity(0.45), lineWidth: 1))
            .padding(.horizontal, 14)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }
}

private struct SliderHeader<Extra: View>: View {
    let systemImage: String
    let title: String
    let valueText: String
    let isAuto: Bool
    let primaryColor: Color
    let onAutoClick: () -> Void
    let onCloseClick: () -> Void
    let extraAction: Extra

    init(
        systemImage: String,
        title: String,
        valueText: String,
        isAuto: Bool,
        primaryColor: Color,
        onAutoClick: @escaping () -> Void,
        onCloseClick: @escaping () -> Void,
        @ViewBuilder extraAction: () -> Extra = { EmptyView() }
    ) {
        self.systemImage = systemImage
        self.title = title
        self.valueText = valueText
        self.isAuto = isAuto
        self.primaryColor = primaryColor
        self.onAutoClick = onAutoClick
        self.onCloseClick = onCloseClick
        self.extraAction = extraAction()
    }

    var body: some View {
        VStack(spacing: 4) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: systemImage)
                        .foregroundColor(primaryColor)
                        .font(.system(size: 14))

                    Text(title)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.gray)
                }

                Spacer()

                extraAction

                Button(action: onAutoClick) {
                    Text("AUTO")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(isAuto ? .black : .white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(isAuto ? primaryColor : Color.white.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                Button(action: onCloseClick) {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.gray)
                        .frame(width: 24, height: 24)
                }
            }

            Text(valueText)
                .font(.system(size: 18, weight: .black, design: .monospaced))
                .foregroundColor(primaryColor)
                .padding(.vertical, 2)
        }
    }
}

private struct VolumeSliderRow: View {
    @Binding var value: Double
    let leftLabel: String
    let rightLabel: String
    let primaryColor: Color
    let onStepDown: () -> Void
    let onStepUp: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onStepDown) {
                Image(systemName: "minus")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 34, height: 34)
                    .background(Color.white.opacity(0.08))
                    .clipShape(Circle())
            }

            Text(leftLabel)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(.gray)

            Slider(value: $value, in: 0.0...1.0)
                .accentColor(primaryColor)

            Text(rightLabel)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(.gray)

            Button(action: onStepUp) {
                Image(systemName: "plus")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 34, height: 34)
                    .background(Color.white.opacity(0.08))
                    .clipShape(Circle())
            }
        }
    }
}
