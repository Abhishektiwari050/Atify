import SwiftUI

public struct MiniPlayerView: View {
    @ObservedObject var player: AudioPlayer
    @ObservedObject private var prefs = PreferencesService.shared
    public let onExpand: () -> Void

    public init(player: AudioPlayer, onExpand: @escaping () -> Void) {
        self.player = player
        self.onExpand = onExpand
    }

    public var body: some View {
        if let track = player.currentTrack {
            VStack(spacing: 0) {
                // Main Mini Player Bar
                HStack(spacing: 12) {
                    // Album Artwork
                    AsyncImage(url: URL(string: track.coverUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(AtifyTheme.card)
                            .overlay(Image(systemName: "music.note").foregroundColor(AtifyTheme.textSubtle))
                    }
                    .frame(width: 42, height: 42)
                    .clipShape(RoundedRectangle(cornerRadius: 6))

                    // Title & Artist
                    VStack(alignment: .leading, spacing: 2) {
                        Text(track.title)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                            .lineLimit(1)

                        Text(track.artist)
                            .font(.system(size: 12))
                            .foregroundColor(AtifyTheme.textSecondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    // Like Button
                    HeartButtonView(
                        isLiked: prefs.isLiked(trackId: track.id),
                        size: 20
                    ) {
                        prefs.toggleLike(track: track)
                    }

                    // Play/Pause Button
                    Button(action: {
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.impactOccurred()
                        player.togglePlayPause()
                    }) {
                        if player.isBuffering {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .frame(width: 32, height: 32)
                        } else {
                            Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: 20))
                                .foregroundColor(.white)
                                .frame(width: 32, height: 32)
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(AtifyTheme.surface.opacity(0.95))
                        .shadow(color: Color.black.opacity(0.4), radius: 8, x: 0, y: 4)
                )

                // Bottom Progress Bar
                GeometryReader { geo in
                    let progress = player.duration > 0 ? (player.currentTime / player.duration) : 0
                    ZStack(alignment: .leading) {
                        Rectangle()
                            .fill(Color.white.opacity(0.15))
                            .frame(height: 2)

                        Rectangle()
                            .fill(AtifyTheme.spotifyGreen)
                            .frame(width: geo.size.width * CGFloat(progress), height: 2)
                    }
                }
                .frame(height: 2)
                .padding(.horizontal, 4)
            }
            .contentShape(Rectangle())
            .onTapGesture {
                onExpand()
            }
            .gesture(
                DragGesture(minimumDistance: 20)
                    .onEnded { value in
                        if value.translation.width < -50 {
                            // Swipe left -> Next
                            let impact = UIImpactFeedbackGenerator(style: .light)
                            impact.impactOccurred()
                            player.next()
                        } else if value.translation.width > 50 {
                            // Swipe right -> Previous
                            let impact = UIImpactFeedbackGenerator(style: .light)
                            impact.impactOccurred()
                            player.previous()
                        }
                    }
            )
            .padding(.horizontal, 8)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }
}
