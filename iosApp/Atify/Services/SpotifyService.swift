import Foundation

public final class SpotifyService: ObservableObject {
    public static let shared = SpotifyService()

    private let session = URLSession(configuration: .default)
    private var accessToken: String?
    private var tokenExpiry: Date?

    private init() {}

    // MARK: - Token Management
    public func ensureToken() async -> String? {
        if let token = accessToken, let expiry = tokenExpiry, expiry > Date().addingTimeInterval(60) {
            return token
        }

        // Fetch fresh web token (uses sp_dc if available in preferences)
        guard let url = URL(string: "https://open.spotify.com/get_access_token?reason=transport&productType=web_player") else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.addValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")

        let spDc = PreferencesService.shared.spDc
        if !spDc.isEmpty {
            request.addValue("sp_dc=\(spDc)", forHTTPHeaderField: "Cookie")
        }

        do {
            let (data, _) = try await session.data(for: request)
            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let token = json["accessToken"] as? String,
               let expiryMs = json["accessTokenExpirationTimestampMs"] as? Double {
                self.accessToken = token
                self.tokenExpiry = Date(timeIntervalSince1970: expiryMs / 1000.0)
                return token
            }
        } catch {
            print("Failed to fetch Spotify web access token: \(error)")
        }
        return nil
    }

    // MARK: - Catalog Search
    public func searchTracks(query: String, limit: Int = 30) async -> [Track] {
        guard let token = await ensureToken() else { return [] }
        guard let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://api.spotify.com/v1/search?q=\(encoded)&type=track&limit=\(limit)&market=from_token") else {
            return []
        }

        var request = URLRequest(url: url)
        request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        do {
            let (data, _) = try await session.data(for: request)
            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let tracksObj = json["tracks"] as? [String: Any],
               let items = tracksObj["items"] as? [[String: Any]] {
                return items.compactMap { parseTrack(from: $0) }
            }
        } catch {
            print("Spotify search failed: \(error)")
        }
        return []
    }

    // MARK: - Track Radio / Mood-based Recommendations
    public func getTrackRadio(seedTrack: Track, limit: Int = 25) async -> [Track] {
        guard let token = await ensureToken(), !seedTrack.spotifyId.isEmpty else { return [] }

        // Primary: Spotify Recommendations endpoint
        guard let url = URL(string: "https://api.spotify.com/v1/recommendations?seed_tracks=\(seedTrack.spotifyId)&limit=\(limit)") else {
            return []
        }

        var request = URLRequest(url: url)
        request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        do {
            let (data, _) = try await session.data(for: request)
            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let items = json["tracks"] as? [[String: Any]] {
                return items.compactMap { parseTrack(from: $0) }.filter { $0.id != seedTrack.id }
            }
        } catch {
            print("Spotify recommendations failed: \(error)")
        }
        return []
    }

    // MARK: - Parsing
    private func parseTrack(from dict: [String: Any]) -> Track? {
        guard let id = dict["id"] as? String,
              let name = dict["name"] as? String,
              let artists = dict["artists"] as? [[String: Any]],
              let album = dict["album"] as? [String: Any] else {
            return nil
        }

        let artistNames = artists.compactMap { $0["name"] as? String }.joined(separator: ", ")
        let albumName = album["name"] as? String ?? ""
        let images = album["images"] as? [[String: Any]] ?? []
        let coverUrl = images.first?["url"] as? String ?? ""
        let durationMs = dict["duration_ms"] as? Int ?? 0
        let explicit = dict["explicit"] as? Bool ?? false

        return Track(
            id: id,
            title: name,
            artist: artistNames.isEmpty ? "Unknown Artist" : artistNames,
            album: albumName,
            coverUrl: coverUrl,
            durationMs: durationMs,
            spotifyId: id,
            isExplicit: explicit
        )
    }
}
