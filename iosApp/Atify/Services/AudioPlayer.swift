import Foundation
import AVFoundation
import MediaPlayer
import Combine

public enum RepeatMode: Int, Codable {
    case off = 0
    case all = 1
    case one = 2
}

@MainActor
public final class AudioPlayer: ObservableObject {
    public static let shared = AudioPlayer()

    private var player: AVPlayer?
    private var timeObserverToken: Any?

    @Published public var currentTrack: Track?
    @Published public var queue: [Track] = []
    @Published public var isPlaying: Bool = false
    @Published public var isBuffering: Bool = false
    @Published public var currentTime: Double = 0
    @Published public var duration: Double = 0
    @Published public var repeatMode: RepeatMode = .off
    @Published public var isShuffle: Bool = false

    private init() {
        setupAudioSession()
        setupRemoteCommands()
    }

    // MARK: - Audio Session Setup (Background Audio)
    private func setupAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, policy: .longFormAudio)
            try session.setActive(true)
        } catch {
            print("Failed to configure AVAudioSession: \(error)")
        }
    }

    // MARK: - Lock Screen & Control Center Integration
    private func setupRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()

        commandCenter.playCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.resume() }
            return .success
        }

        commandCenter.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.pause() }
            return .success
        }

        commandCenter.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.togglePlayPause() }
            return .success
        }

        commandCenter.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.next() }
            return .success
        }

        commandCenter.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.previous() }
            return .success
        }

        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let posEvent = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor [weak self] in
                self?.seek(to: posEvent.positionTime)
            }
            return .success
        }
    }

    private func updateNowPlayingInfo() {
        guard let track = currentTrack else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }

        var info: [String: Any] = [
            MPMediaItemPropertyTitle: track.title,
            MPMediaItemPropertyArtist: track.artist,
            MPMediaItemPropertyAlbumTitle: track.album,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentTime,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0
        ]

        if let coverUrl = URL(string: track.coverUrl) {
            Task.detached {
                if let data = try? Data(contentsOf: coverUrl), let image = UIImage(data: data) {
                    let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
                    await MainActor.run {
                        info[MPMediaItemPropertyArtwork] = artwork
                        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
                    }
                }
            }
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    // MARK: - Playback Controls
    public func play(track: Track, newQueue: [Track]? = nil) {
        if let newQ = newQueue {
            self.queue = newQ
        } else if !queue.contains(where: { $0.id == track.id }) {
            self.queue = [track]
        }

        self.currentTrack = track
        self.isBuffering = true
        self.currentTime = 0
        self.duration = Double(track.durationMs) / 1000.0

        PreferencesService.shared.addRecent(track: track)

        Task {
            // Resolve stream URL via InnerTubeService
            guard let streamUrl = await InnerTubeService.shared.resolveStream(for: track) else {
                self.isBuffering = false
                print("Failed to resolve stream URL for \(track.title)")
                return
            }

            self.playStream(url: streamUrl)
        }
    }

    private func playStream(url: URL) {
        let playerItem = AVPlayerItem(url: url)

        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemDidPlayToEndTime, object: nil)
        NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: playerItem,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.handleTrackFinished()
            }
        }

        if let existing = player {
            existing.replaceCurrentItem(with: playerItem)
        } else {
            let p = AVPlayer(playerItem: playerItem)
            self.player = p
            setupTimeObserver()
        }

        player?.play()
        self.isPlaying = true
        self.isBuffering = false
        updateNowPlayingInfo()
    }

    private func setupTimeObserver() {
        guard let p = player else { return }
        let interval = CMTime(seconds: 0.25, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        timeObserverToken = p.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                self.currentTime = time.seconds
                if let dur = p.currentItem?.duration.seconds, !dur.isNaN, dur > 0 {
                    self.duration = dur
                }
            }
        }
    }

    public func resume() {
        player?.play()
        isPlaying = true
        updateNowPlayingInfo()
    }

    public func pause() {
        player?.pause()
        isPlaying = false
        updateNowPlayingInfo()
    }

    public func togglePlayPause() {
        if isPlaying {
            pause()
        } else {
            resume()
        }
    }

    public func seek(to seconds: Double) {
        let cmTime = CMTime(seconds: seconds, preferredTimescale: 600)
        player?.seek(to: cmTime, toleranceBefore: .zero, toleranceAfter: .zero)
        currentTime = seconds
        updateNowPlayingInfo()
    }

    public func next() {
        guard let cur = currentTrack, let idx = queue.firstIndex(where: { $0.id == cur.id }) else { return }

        if idx + 1 < queue.count {
            play(track: queue[idx + 1])
        } else if repeatMode == .all, !queue.isEmpty {
            play(track: queue[0])
        }
    }

    public func previous() {
        if currentTime > 3.0 {
            seek(to: 0)
            return
        }
        guard let cur = currentTrack, let idx = queue.firstIndex(where: { $0.id == cur.id }) else { return }

        if idx > 0 {
            play(track: queue[idx - 1])
        } else if repeatMode == .all, !queue.isEmpty {
            play(track: queue[queue.count - 1])
        }
    }

    private func handleTrackFinished() {
        switch repeatMode {
        case .one:
            seek(to: 0)
            resume()
        case .all, .off:
            next()
        }
    }

    public func toggleRepeat() {
        switch repeatMode {
        case .off: repeatMode = .all
        case .all: repeatMode = .one
        case .one: repeatMode = .off
        }
    }

    public func toggleShuffle() {
        isShuffle.toggle()
        if isShuffle, let cur = currentTrack {
            var rest = queue.filter { $0.id != cur.id }
            rest.shuffle()
            queue = [cur] + rest
        }
    }
}
