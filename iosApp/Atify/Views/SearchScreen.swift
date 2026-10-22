import SwiftUI

public struct SearchScreen: View {
    @ObservedObject var player: AudioPlayer
    @State private var query: String = ""
    @State private var results: [Track] = []
    @State private var isSearching = false
    @State private var searchTask: Task<Void, Never>?

    public init(player: AudioPlayer) {
        self.player = player
    }

    public var body: some View {
        NavigationView {
            ZStack {
                AtifyTheme.background.ignoresSafeArea()

                VStack(spacing: 12) {
                    // Search Bar
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(AtifyTheme.textSecondary)

                        TextField("What do you want to listen to?", text: $query)
                            .foregroundColor(.white)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                            .onChange(of: query) { newQuery in
                                performSearch(query: newQuery)
                            }

                        if !query.isEmpty {
                            Button(action: {
                                query = ""
                                results = []
                            }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(AtifyTheme.textSecondary)
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(AtifyTheme.card)
                    .cornerRadius(8)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)

                    // Results or Browse Genres
                    if isSearching {
                        Spacer()
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: AtifyTheme.spotifyGreen))
                            .scaleEffect(1.2)
                        Spacer()
                    } else if !results.isEmpty {
                        ScrollView {
                            LazyVStack(spacing: 2) {
                                ForEach(results) { track in
                                    TrackRowView(
                                        track: track,
                                        isPlayingNow: player.currentTrack?.id == track.id
                                    ) {
                                        playWithMoodRadio(track: track)
                                    }
                                }
                            }
                            .padding(.bottom, 80)
                        }
                    } else if query.isEmpty {
                        // Explore Genres & Discover
                        VStack(spacing: 16) {
                            Spacer()

                            Image(systemName: "sparkles")
                                .font(.system(size: 48))
                                .foregroundColor(AtifyTheme.spotifyGreen)

                            Text("Play what you love")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.white)

                            Text("Search for artists, songs, podcasts, and more.")
                                .font(.system(size: 14))
                                .foregroundColor(AtifyTheme.textSecondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 32)

                            Spacer()
                        }
                    } else {
                        VStack(spacing: 12) {
                            Spacer()
                            Text("No results found for \"\(query)\"")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)

                            Text("Please check your spelling or try different keywords.")
                                .font(.system(size: 13))
                                .foregroundColor(AtifyTheme.textSecondary)
                            Spacer()
                        }
                    }
                }
            }
            .navigationTitle("Search")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func performSearch(query: String) {
        searchTask?.cancel()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            results = []
            isSearching = false
            return
        }

        searchTask = Task {
            try? await Task.sleep(nanoseconds: 350_000_000) // 350ms debounce
            guard !Task.isCancelled else { return }

            await MainActor.run { isSearching = true }
            // Expanded search limit: 50 tracks
            let searchResults = await SpotifyService.shared.searchTracks(query: trimmed, limit: 50)
            guard !Task.isCancelled else { return }

            await MainActor.run {
                self.results = searchResults
                self.isSearching = false
            }
        }
    }

    private func playWithMoodRadio(track: Track) {
        // 1. Play clicked song immediately
        player.play(track: track, newQueue: [track])

        // 2. Fetch mood-based radio recommendations asynchronously
        Task {
            let radio = await SpotifyService.shared.getTrackRadio(seedTrack: track, limit: 30)
            await MainActor.run {
                if player.currentTrack?.id == track.id {
                    player.queue = [track] + radio
                }
            }
        }
    }
}
