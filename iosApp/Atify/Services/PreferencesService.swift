import Foundation

public final class PreferencesService: ObservableObject {
    public static let shared = PreferencesService()

    private let defaults = UserDefaults.standard
    private let keySpDc = "spotify_sp_dc"
    private let keyLikedSongs = "liked_songs_list"
    private let keyRecentTracks = "recent_tracks_list"
    private let keyPermanentVideoMap = "spotify_video_permanent_map"

    @Published public var spDc: String {
        didSet { defaults.set(spDc, forKey: keySpDc) }
    }

    @Published public var likedTracks: [Track] = [] {
        didSet { saveLikedTracks() }
    }

    @Published public var recentTracks: [Track] = [] {
        didSet { saveRecentTracks() }
    }

    private var inMemoryVideoMap: [String: String] = [:]

    private init() {
        self.spDc = defaults.string(forKey: keySpDc) ?? ""
        loadLikedTracks()
        loadRecentTracks()
        loadVideoMap()
    }

    // MARK: - Liked Tracks
    public func isLiked(trackId: String) -> Bool {
        return likedTracks.contains { $0.id == trackId || (!trackId.isEmpty && $0.spotifyId == trackId) }
    }

    public func toggleLike(track: Track) {
        if let index = likedTracks.firstIndex(where: { $0.id == track.id || (!track.spotifyId.isEmpty && $0.spotifyId == track.spotifyId) }) {
            likedTracks.remove(at: index)
        } else {
            likedTracks.insert(track, at: 0)
        }
    }

    private func saveLikedTracks() {
        if let data = try? JSONEncoder().encode(likedTracks) {
            defaults.set(data, forKey: keyLikedSongs)
        }
    }

    private func loadLikedTracks() {
        if let data = defaults.data(forKey: keyLikedSongs),
           let list = try? JSONDecoder().decode([Track].self, from: data) {
            self.likedTracks = list
        }
    }

    // MARK: - Recent Tracks
    public func addRecent(track: Track) {
        var updated = recentTracks.filter { $0.id != track.id }
        updated.insert(track, at: 0)
        if updated.count > 40 {
            updated = Array(updated.prefix(40))
        }
        self.recentTracks = updated
    }

    private func saveRecentTracks() {
        if let data = try? JSONEncoder().encode(recentTracks) {
            defaults.set(data, forKey: keyRecentTracks)
        }
    }

    private func loadRecentTracks() {
        if let data = defaults.data(forKey: keyRecentTracks),
           let list = try? JSONDecoder().decode([Track].self, from: data) {
            self.recentTracks = list
        }
    }

    // MARK: - Permanent Spotify -> Video ID Map
    public func getPermanentVideoId(for spotifyId: String) -> String? {
        guard !spotifyId.isEmpty else { return nil }
        if let memory = inMemoryVideoMap[spotifyId] { return memory }
        if let dict = defaults.dictionary(forKey: keyPermanentVideoMap) as? [String: String],
           let id = dict[spotifyId] {
            inMemoryVideoMap[spotifyId] = id
            return id
        }
        return nil
    }

    public func setPermanentVideoId(_ videoId: String, for spotifyId: String) {
        guard !spotifyId.isEmpty, !videoId.isEmpty else { return }
        inMemoryVideoMap[spotifyId] = videoId
        var dict = (defaults.dictionary(forKey: keyPermanentVideoMap) as? [String: String]) ?? [:]
        dict[spotifyId] = videoId
        defaults.set(dict, forKey: keyPermanentVideoMap)
    }

    private func loadVideoMap() {
        if let dict = defaults.dictionary(forKey: keyPermanentVideoMap) as? [String: String] {
            inMemoryVideoMap = dict
        }
    }
}
