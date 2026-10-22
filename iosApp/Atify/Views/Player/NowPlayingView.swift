import SwiftUI

public struct NowPlayingView: View {
    @ObservedObject var player: AudioPlayer
    @ObservedObject private var prefs = PreferencesService.shared
    @Environment(\.dismiss) private var dismiss

    @State private var dragOffset: CGFloat = 0
    @State private var showQueueSheet = false

    public init(player: AudioPlayer) {
        self.player = player
    }

    public var body: some View {
        ZStack {
            // Gradient Background
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.16, green: 0.18, blue: 0.22),
                    AtifyTheme.background
                ]),
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // Top Navigation Bar
                HStack {
                    Button(action: {
                        dismiss()
                    }) {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                    }

                    Spacer()

                    VStack(spacing: 2) {
                        Text("PLAYING FROM")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(AtifyTheme.textSecondary)
                            .tracking(1.0)
                        Text(player.currentTrack?.album.isEmpty == false ? (player.currentTrack?.album ?? "Queue") : "Radio")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                    }

                    Spacer()

                    Button(action: {
                        // Options menu
                    }) {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)

                Spacer(minLength: 16)

                // Album Artwork with Spotify Spring Scale on Play/Pause
                if let track = player.currentTrack {
                    AsyncImage(url: URL(string: track.coverUrl)) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    } placeholder: {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(AtifyTheme.surface)
                            .overlay(
                                Image(systemName: "music.note")
                                    .font(.system(size: 64))
                                    .foregroundColor(AtifyTheme.textSubtle)
                            )
                    }
                    .frame(maxWidth: 320, maxHeight: 320)
                    .aspectRatio(1, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .shadow(color: Color.black.opacity(0.5), radius: 24, x: 0, y: 12)
                    .scaleEffect(player.isPlaying ? 1.0 : 0.88)
                    .animation(.spring(response: 0.45, dampingFraction: 0.65), value: player.isPlaying)
                    .padding(.horizontal, 28)
                }

                Spacer(minLength: 24)

                // Track Title, Artist & Bouncing Like Button
                if let track = player.currentTrack {
                    HStack(alignment: .center) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(track.title)
                                .font(.system(size: 22, weight: .bold))
                                .foregroundColor(.white)
                                .lineLimit(1)

                            Text(track.artist)
                                .font(.system(size: 16, weight: .medium))
                                .foregroundColor(AtifyTheme.textSecondary)
                                .lineLimit(1)
                        }

                        Spacer()

                        HeartButtonView(
                            isLiked: prefs.isLiked(trackId: track.id),
                            size: 26
                        ) {
                            prefs.toggleLike(track: track)
                        }
                    }
                    .padding(.horizontal, 28)
                }

                Spacer(minLength: 16)

                // Playback Controls (Scrubber, Prev, Play/Pause, Next, Shuffle, Repeat)
                PlayerControlsView(player: player)

                Spacer(minLength: 16)

                // Bottom Action Bar: AirPlay / Audio Routes & Queue
                HStack {
                    AirPlayButtonView()
                        .frame(width: 32, height: 32)

                    Spacer()

                    Button(action: {
                        showQueueSheet = true
                    }) {
                        Image(systemName: "list.bullet")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                    }
                }
                .padding(.horizontal, 28)
                .padding(.bottom, 24)
            }
        }
        .offset(y: max(0, dragOffset))
        .gesture(
            DragGesture()
                .onChanged { gesture in
                    if gesture.translation.height > 0 {
                        dragOffset = gesture.translation.height
                    }
                }
                .onEnded { gesture in
                    if gesture.translation.height > 120 {
                        dismiss()
                    } else {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                            dragOffset = 0
                        }
                    }
                }
        )
        .sheet(isPresented: $showQueueSheet) {
            QueueSheetView(player: player)
        }
    }
}

public struct QueueSheetView: View {
    @ObservedObject var player: AudioPlayer
    @Environment(\.dismiss) private var dismiss

    public var body: some View {
        NavigationView {
            ZStack {
                AtifyTheme.background.ignoresSafeArea()

                List {
                    if let current = player.currentTrack {
                        Section(header: Text("Now Playing").foregroundColor(AtifyTheme.spotifyGreen).font(.system(size: 13, weight: .bold))) {
                            TrackRowView(track: current, isPlayingNow: true) {}
                                .listRowBackground(AtifyTheme.surface)
                        }
                    }

                    Section(header: Text("Next In Queue").foregroundColor(AtifyTheme.textSecondary).font(.system(size: 13, weight: .bold))) {
                        ForEach(player.queue.filter { $0.id != player.currentTrack?.id }) { track in
                            TrackRowView(track: track, isPlayingNow: false) {
                                player.play(track: track)
                            }
                            .listRowBackground(AtifyTheme.surface)
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
            .navigationTitle("Play Queue")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .foregroundColor(AtifyTheme.spotifyGreen)
                }
            }
        }
    }
}
