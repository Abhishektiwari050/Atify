import SwiftUI

public struct PlayerControlsView: View {
    @ObservedObject var player: AudioPlayer

    public init(player: AudioPlayer) {
        self.player = player
    }

    public var body: some View {
        VStack(spacing: 16) {
            // Scrubber Slider
            VStack(spacing: 6) {
                Slider(
                    value: Binding(
                        get: { player.currentTime },
                        set: { player.seek(to: $0) }
                    ),
                    in: 0...max(player.duration, 1.0)
                )
                .accentColor(.white)

                HStack {
                    Text(formatTime(player.currentTime))
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(AtifyTheme.textSecondary)

                    Spacer()

                    let remaining = max(0, player.duration - player.currentTime)
                    Text("-\(formatTime(remaining))")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(AtifyTheme.textSecondary)
                }
            }
            .padding(.horizontal, 24)

            // Playback Buttons Row
            HStack(spacing: 0) {
                // Shuffle Button
                Button(action: {
                    let impact = UIImpactFeedbackGenerator(style: .light)
                    impact.impactOccurred()
                    player.toggleShuffle()
                }) {
                    Image(systemName: "shuffle")
                        .font(.system(size: 20))
                        .foregroundColor(player.isShuffle ? AtifyTheme.spotifyGreen : .white)
                        .frame(width: 48, height: 48)
                }

                Spacer()

                // Previous Button
                Button(action: {
                    let impact = UIImpactFeedbackGenerator(style: .light)
                    impact.impactOccurred()
                    player.previous()
                }) {
                    Image(systemName: "backward.end.fill")
                        .font(.system(size: 26))
                        .foregroundColor(.white)
                        .frame(width: 48, height: 48)
                }

                Spacer()

                // Main Play / Pause Button
                Button(action: {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    player.togglePlayPause()
                }) {
                    ZStack {
                        Circle()
                            .fill(Color.white)
                            .frame(width: 64, height: 64)

                        if player.isBuffering {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .black))
                        } else {
                            Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: 26))
                                .foregroundColor(.black)
                                .offset(x: player.isPlaying ? 0 : 2)
                        }
                    }
                }
                .buttonStyle(BouncyButtonStyle())

                Spacer()

                // Next Button
                Button(action: {
                    let impact = UIImpactFeedbackGenerator(style: .light)
                    impact.impactOccurred()
                    player.next()
                }) {
                    Image(systemName: "forward.end.fill")
                        .font(.system(size: 26))
                        .foregroundColor(.white)
                        .frame(width: 48, height: 48)
                }

                Spacer()

                // Repeat Button
                Button(action: {
                    let impact = UIImpactFeedbackGenerator(style: .light)
                    impact.impactOccurred()
                    player.toggleRepeat()
                }) {
                    ZStack {
                        Image(systemName: player.repeatMode == .one ? "repeat.1" : "repeat")
                            .font(.system(size: 20))
                            .foregroundColor(player.repeatMode != .off ? AtifyTheme.spotifyGreen : .white)
                    }
                    .frame(width: 48, height: 48)
                }
            }
            .padding(.horizontal, 20)
        }
    }

    private func formatTime(_ seconds: Double) -> String {
        guard !seconds.isNaN && !seconds.isInfinite else { return "0:00" }
        let total = Int(seconds)
        let min = total / 60
        let sec = total % 60
        return String(format: "%d:%02d", min, sec)
    }
}

public struct BouncyButtonStyle: ButtonStyle {
    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.92 : 1.0)
            .animation(.spring(response: 0.25, dampingFraction: 0.55), value: configuration.isPressed)
    }
}
