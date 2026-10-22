import SwiftUI

public struct TrackRowView: View {
    public let track: Track
    public let isPlayingNow: Bool
    public let onTap: () -> Void

    @ObservedObject private var prefs = PreferencesService.shared

    public init(track: Track, isPlayingNow: Bool = false, onTap: @escaping () -> Void) {
        self.track = track
        self.isPlayingNow = isPlayingNow
        self.onTap = onTap
    }

    public var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                // Album Artwork
                AsyncImage(url: URL(string: track.coverUrl)) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(AtifyTheme.surface)
                        .overlay(Image(systemName: "music.note").foregroundColor(AtifyTheme.textSubtle))
                }
                .frame(width: 48, height: 48)
                .clipShape(RoundedRectangle(cornerRadius: 6))

                // Track Title & Artist
                VStack(alignment: .leading, spacing: 3) {
                    Text(track.title)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(isPlayingNow ? AtifyTheme.spotifyGreen : AtifyTheme.textPrimary)
                        .lineLimit(1)

                    HStack(spacing: 4) {
                        if track.isExplicit {
                            Text("E")
                                .font(.system(size: 9, weight: .bold))
                                .padding(.horizontal, 3)
                                .padding(.vertical, 1)
                                .background(Color.white.opacity(0.3))
                                .cornerRadius(2)
                        }
                        Text(track.artist)
                            .font(.system(size: 13))
                            .foregroundColor(AtifyTheme.textSecondary)
                            .lineLimit(1)
                    }
                }

                Spacer()

                // Like Button
                let isLiked = prefs.isLiked(trackId: track.id)
                HeartButtonView(isLiked: isLiked, size: 20) {
                    prefs.toggleLike(track: track)
                }
            }
            .padding(.vertical, 6)
            .padding(.horizontal, 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(BouncyRowButtonStyle())
    }
}

public struct BouncyRowButtonStyle: ButtonStyle {
    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .animation(.spring(response: 0.25, dampingFraction: 0.65), value: configuration.isPressed)
    }
}
