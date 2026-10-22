import SwiftUI

public struct HomeScreen: View {
    @ObservedObject var player: AudioPlayer
    @ObservedObject private var prefs = PreferencesService.shared
    @State private var showSettings = false
    @State private var trendingTracks: [Track] = []
    @State private var isLoading = false

    public init(player: AudioPlayer) {
        self.player = player
    }

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5..<12: return "Good morning"
        case 12..<18: return "Good afternoon"
        default: return "Good evening"
        }
    }

    public var body: some View {
        NavigationView {
            ZStack {
                AtifyTheme.background.ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        // Header Greeting & Settings
                        HStack {
                            Text(greeting)
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(.white)

                            Spacer()

                            Button(action: {
                                showSettings = true
                            }) {
                                Image(systemName: "gearshape")
                                    .font(.system(size: 20))
                                    .foregroundColor(.white)
                                    .frame(width: 44, height: 44)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 8)

                        // Quick Picks (Recently played or fallback favorites)
                        let quickPicks = Array(prefs.recentTracks.prefix(6))
                        if !quickPicks.isEmpty {
                            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                                ForEach(quickPicks) { track in
                                    QuickPickCard(track: track, isPlaying: player.currentTrack?.id == track.id) {
                                        player.play(track: track, newQueue: quickPicks)
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                        }

                        // Recently Played Horizontal Carousel
                        if !prefs.recentTracks.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Recently played")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 16)

                                ScrollView(.horizontal, showsIndicators: false) {
                                    LazyHStack(spacing: 14) {
                                        ForEach(prefs.recentTracks) { track in
                                            TrackCardView(track: track) {
                                                player.play(track: track, newQueue: prefs.recentTracks)
                                            }
                                        }
                                    }
                                    .padding(.horizontal, 16)
                                }
                            }
                        }

                        // Trending / Recommended For You
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Recommended for you")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 16)

                            if isLoading {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: AtifyTheme.spotifyGreen))
                                    .frame(maxWidth: .infinity, minHeight: 120)
                            } else {
                                LazyVStack(spacing: 2) {
                                    ForEach(trendingTracks) { track in
                                        TrackRowView(
                                            track: track,
                                            isPlayingNow: player.currentTrack?.id == track.id
                                        ) {
                                            player.play(track: track, newQueue: trendingTracks)
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
            .sheet(isPresented: $showSettings) {
                SettingsView()
            }
            .task {
                await loadTrending()
            }
        }
    }

    private func loadTrending() async {
        guard trendingTracks.isEmpty else { return }
        isLoading = true
        // Default top tracks search query
        let results = await SpotifyService.shared.searchTracks(query: "Top Global Hits", limit: 20)
        trendingTracks = results
        isLoading = false
    }
}

public struct QuickPickCard: View {
    public let track: Track
    public let isPlaying: Bool
    public let onTap: () -> Void

    public var body: some View {
        Button(action: onTap) {
            HStack(spacing: 8) {
                AsyncImage(url: URL(string: track.coverUrl)) { img in
                    img.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Rectangle().fill(AtifyTheme.surface)
                }
                .frame(width: 54, height: 54)
                .clipped()

                Text(track.title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(isPlaying ? AtifyTheme.spotifyGreen : .white)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                Spacer(minLength: 4)
            }
            .frame(height: 54)
            .background(AtifyTheme.card)
            .cornerRadius(4)
        }
        .buttonStyle(BouncyRowButtonStyle())
    }
}

public struct TrackCardView: View {
    public let track: Track
    public let onTap: () -> Void

    public var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                AsyncImage(url: URL(string: track.coverUrl)) { img in
                    img.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(AtifyTheme.surface)
                }
                .frame(width: 140, height: 140)
                .clipShape(RoundedRectangle(cornerRadius: 6))

                Text(track.title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .frame(width: 140, alignment: .leading)

                Text(track.artist)
                    .font(.system(size: 12))
                    .foregroundColor(AtifyTheme.textSecondary)
                    .lineLimit(1)
                    .frame(width: 140, alignment: .leading)
            }
        }
        .buttonStyle(BouncyRowButtonStyle())
    }
}
