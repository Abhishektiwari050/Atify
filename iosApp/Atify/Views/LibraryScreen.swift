import SwiftUI

public struct LibraryScreen: View {
    @ObservedObject var player: AudioPlayer
    @ObservedObject private var prefs = PreferencesService.shared

    public init(player: AudioPlayer) {
        self.player = player
    }

    public var body: some View {
        NavigationView {
            ZStack {
                AtifyTheme.background.ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        // Header
                        HStack {
                            Image(systemName: "person.crop.circle.fill")
                                .font(.system(size: 32))
                                .foregroundColor(AtifyTheme.textSecondary)

                            Text("Your Library")
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(.white)

                            Spacer()
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 8)

                        // Liked Songs Card Navigation Link
                        NavigationLink(destination: LikedSongsView(player: player)) {
                            HStack(spacing: 14) {
                                ZStack {
                                    LinearGradient(
                                        gradient: Gradient(colors: [
                                            Color(red: 0.45, green: 0.25, blue: 0.85),
                                            Color(red: 0.25, green: 0.55, blue: 0.85)
                                        ]),
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                    Image(systemName: "heart.fill")
                                        .font(.system(size: 26))
                                        .foregroundColor(.white)
                                }
                                .frame(width: 64, height: 64)
                                .cornerRadius(6)

                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Liked Songs")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.white)

                                    Text("Playlist • \(prefs.likedTracks.count) songs")
                                        .font(.system(size: 13))
                                        .foregroundColor(AtifyTheme.textSecondary)
                                }

                                Spacer()

                                Image(systemName: "chevron.right")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(AtifyTheme.textSubtle)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(AtifyTheme.card)
                            .cornerRadius(8)
                            .padding(.horizontal, 16)
                        }
                        .buttonStyle(BouncyRowButtonStyle())

                        // Listening History Section
                        if !prefs.recentTracks.isEmpty {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Recently Played")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 16)
                                    .padding(.top, 8)

                                LazyVStack(spacing: 2) {
                                    ForEach(prefs.recentTracks) { track in
                                        TrackRowView(
                                            track: track,
                                            isPlayingNow: player.currentTrack?.id == track.id
                                        ) {
                                            player.play(track: track, newQueue: prefs.recentTracks)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(minLength: 80)
                    }
                }
            }
            .navigationBarHidden(true)
        }
    }
}
