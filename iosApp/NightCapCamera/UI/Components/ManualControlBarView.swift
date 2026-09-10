import SwiftUI

public struct ManualControlBarView: View {
    let selectedMode: StackingMode
    let activeParam: ActiveParameter
    let isStackingActive: Bool
    let isRecordingVideo: Bool

    let isManualExposure: Bool
    let exposureTimeNs: Int64
    let currentISO: Float
    let isoBoost: Float
    let isManualFocus: Bool
    let isInfinityFocus: Bool
    let focusDistance: Float
    let evCompensation: Float

    let primaryColor: Color

    let onSelectParameter: (ActiveParameter) -> Void
    let onModeSelected: (StackingMode) -> Void
    let onShutterClick: () -> Void
    let onCycleIsoBoost: () -> Void
    let onResetStacking: () -> Void

    public var body: some View {
        VStack(spacing: 10) {
            // Parameter Selector Chips: SHUTTER, ISO, FOCUS, EV, BOOST
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    // Shutter Chip
                    let seconds = Double(exposureTimeNs) / 1_000_000_000.0
                    let shutterLabel = !isManualExposure ? "AUTO" : (seconds >= 1.0 ? String(format: "%.1fs", seconds) : "1/\(Int(round(1.0 / seconds)))s")

                    ParamChip(
                        label: "SHUTTER",
                        value: shutterLabel,
                        isActive: activeParam == .shutter,
                        isModified: isManualExposure,
                        primaryColor: primaryColor
                    ) {
                        onSelectParameter(activeParam == .shutter ? .none : .shutter)
                    }

                    // ISO Chip
                    let isoLabel = !isManualExposure ? "AUTO" : "\(Int(currentISO))"
                    ParamChip(
                        label: "ISO",
                        value: isoLabel,
                        isActive: activeParam == .iso,
                        isModified: isManualExposure,
                        primaryColor: primaryColor
                    ) {
                        onSelectParameter(activeParam == .iso ? .none : .iso)
                    }

                    // Focus Chip
                    let focusLabel = isInfinityFocus ? "∞ INF" : (isManualFocus ? "MANUAL" : "AUTO")
                    ParamChip(
                        label: "FOCUS",
                        value: focusLabel,
                        isActive: activeParam == .focus,
                        isModified: isManualFocus || isInfinityFocus,
                        primaryColor: primaryColor
                    ) {
                        onSelectParameter(activeParam == .focus ? .none : .focus)
                    }

                    // EV Chip
                    let evLabel = evCompensation == 0 ? "0.0" : String(format: "%+.1f", evCompensation)
                    ParamChip(
                        label: "EV",
                        value: evLabel,
                        isActive: activeParam == .ev,
                        isModified: evCompensation != 0,
                        primaryColor: primaryColor
                    ) {
                        onSelectParameter(activeParam == .ev ? .none : .ev)
                    }

                    // Quick ISO BOOST Chip
                    Button(action: onCycleIsoBoost) {
                        HStack(spacing: 4) {
                            Image(systemName: "bolt.fill")
                                .font(.system(size: 12))
                                .foregroundColor(isoBoost > 1.0 ? primaryColor : .white)

                            Text("\(Int(isoBoost))x BOOST")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(isoBoost > 1.0 ? primaryColor : .white)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(isoBoost > 1.0 ? primaryColor.opacity(0.25) : Color.white.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(isoBoost > 1.0 ? primaryColor : Color.white.opacity(0.15), lineWidth: 1)
                        )
                    }
                }
                .padding(.horizontal, 14)
            }

            // Mode Selector Bar
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(StackingMode.allCases) { mode in
                        let isSelected = mode == selectedMode
                        Button(action: {
                            if !isStackingActive && !isRecordingVideo {
                                onModeSelected(mode)
                            }
                        }) {
                            Text(mode.rawValue.replacingOccurrences(of: "_", with: " "))
                                .font(.system(size: 11, weight: isSelected ? .bold : .regular))
                                .foregroundColor(isSelected ? .black : .gray)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 6)
                                .background(isSelected ? primaryColor : Color.white.opacity(0.08))
                                .clipShape(RoundedRectangle(cornerRadius: 16))
                        }
                        .disabled(isStackingActive || isRecordingVideo)
                    }
                }
                .padding(.horizontal, 14)
            }

            // Shutter Button & Tools Row
            HStack {
                // ISO Boost cycle button
                Button(action: onCycleIsoBoost) {
                    VStack(spacing: 4) {
                        Image(systemName: "bolt.fill")
                            .font(.system(size: 20))
                            .foregroundColor(isoBoost > 1.0 ? primaryColor : .white)
                            .frame(width: 46, height: 46)
                            .background(isoBoost > 1.0 ? primaryColor.opacity(0.25) : Color.white.opacity(0.08))
                            .clipShape(Circle())
                            .overlay(Circle().stroke(isoBoost > 1.0 ? primaryColor : Color.gray, lineWidth: 1))

                        Text("\(Int(isoBoost))x BOOST")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(isoBoost > 1.0 ? primaryColor : .gray)
                    }
                }

                Spacer()

                // Main Shutter Button
                let isActionActive = isStackingActive || isRecordingVideo
                let isVideoMode = selectedMode == .video

                Button(action: onShutterClick) {
                    ZStack {
                        Circle()
                            .stroke(isActionActive || isVideoMode ? Color.red : primaryColor, lineWidth: 4)
                            .frame(width: 76, height: 76)

                        if isActionActive {
                            RoundedRectangle(cornerRadius: 6)
                                .fill(Color.red)
                                .frame(width: 26, height: 26)
                        } else if isVideoMode {
                            Circle()
                                .fill(Color.red)
                                .frame(width: 60, height: 60)
                        } else {
                            Circle()
                                .fill(primaryColor)
                                .frame(width: 60, height: 60)
                        }
                    }
                }

                Spacer()

                // Reset Stacking Button
                Button(action: onResetStacking) {
                    VStack(spacing: 4) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(isStackingActive ? .white : .gray)
                            .frame(width: 46, height: 46)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                            .overlay(Circle().stroke(isStackingActive ? Color.white : Color.gray.opacity(0.5), lineWidth: 1))

                        Text("RESET")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(isStackingActive ? .white : .gray)
                    }
                }
                .disabled(!isStackingActive)
            }
            .padding(.horizontal, 32)
            .padding(.top, 4)
        }
        .padding(.vertical, 12)
        .background(Color.black.opacity(0.92))
    }
}

private struct ParamChip: View {
    let label: String
    let value: String
    let isActive: Bool
    let isModified: Bool
    let primaryColor: Color
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            VStack(spacing: 2) {
                Text(label)
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(isActive ? primaryColor : .gray)

                Text(value)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(isActive ? primaryColor : .white)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(
                isActive ? primaryColor.opacity(0.35) :
                (isModified ? Color.white.opacity(0.12) : Color.white.opacity(0.06))
            )
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(isActive ? primaryColor : (isModified ? primaryColor.opacity(0.6) : Color.white.opacity(0.12)), lineWidth: 1)
            )
        }
    }
}
