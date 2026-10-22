import SwiftUI

public struct LikedSongsView: View {
    @ObservedObject var player: AudioPlayer
    @ObservedObject private var prefs = PreferencesService.shared
    @Environment(\.dismiss) private var dismiss

    public init(player: AudioPlayer) {
        self.player = player
    }

    public var body: some View {
        ZStack {
            AtifyTheme.background.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Header Banner with Gradient
                    ZStack(alignment: .bottomLeading) {
                        LinearGradient(
                            gradient: Gradient(colors: [
                                Color(red: 0.35, green: 0.15, blue: 0.65),
                                AtifyTheme.background
                            ]),
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        .frame(height: 240)

                        HStack(alignment: .bottom, spacing: 16) {
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
                                    .font(.system(size: 48))
                                    .foregroundColor(.white)
                            }
                            .frame(width: 120, height: 120)
                            .cornerRadius(8)
                            .shadow(radius: 8)

                            VStack(alignment: .leading, spacing: 6) {
                                Text("Playlist")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(AtifyTheme.textSecondary)

                                Text("Liked Songs")
                                    .font(.system(size: 26, weight: .bold))
                                    .foregroundColor(.white)

                                Text("\(prefs.likedTracks.count) songs")
                                    .font(.system(size: 13))
                                    .foregroundColor(AtifyTheme.textSecondary)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 16)
                    }

                    // Action Bar: Play & Shuffle Buttons
                    HStack {
                        Button(action: {
                            guard !prefs.likedTracks.isEmpty else { return }
                            let impact = UIImpactFeedbackGenerator(style: .medium)
                            impact.impactOccurred()
                            player.play(track: prefs.likedTracks[0], newQueue: prefs.likedTracks)
                        }) {
                            Image(systemName: "play.circle.fill")
                                .font(.system(size: 50))
                                .foregroundColor(AtifyTheme.spotifyGreen)
                        }

                        Button(action: {
                            guard !prefs.likedTracks.isEmpty else { return }
                            let impact = UIImpactFeedbackGenerator(style: .medium)
                            impact.impactOccurred()
                            var shuffled = prefs.likedTracks
                            shuffled.shuffle()
                            player.play(track: shuffled[0], newQueue: shuffled)
                        }) {
                            Image(systemName: "shuffle")
                                .font(.system(size: 22))
                                .foregroundColor(AtifyTheme.textSecondary)
                                .frame(width: 44, height: 44)
                        }

                        Spacer()
                    }
                    .padding(.horizontal, 16)

                    // Track List
                    if prefs.likedTracks.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "heart.slash")
                                .font(.system(size: 40))
                                .foregroundColor(AtifyTheme.textSubtle)
                                .padding(.top, 40)

                            Text("No liked songs yet")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(AtifyTheme.textSecondary)

                            Text("Songs you like will appear here.")
                                .font(.system(size: 13))
                                .foregroundColor(AtifyTheme.textSubtle)
                        }
                        .frame(maxWidth: .infinity)
                    } else {
                        LazyVStack(spacing: 2) {
                            ForEach(prefs.likedTracks) { track in
                                TrackRowView(
                                    track: track,
                                    isPlayingNow: player.currentTrack?.id == track.id
                                ) {
                                    player.play(track: track, newQueue: prefs.likedTracks)
                                }
                            }
                        }
                    }

                    Spacer(minLength: 80)
                }
            }
        }
        .navigationTitle("Liked Songs")
        .navigationBarTitleDisplayMode(.inline)
    }
}
