import Foundation

public final class InnerTubeService {
    public static let shared = InnerTubeService()

    private let session = URLSession(configuration: .default)
    private var streamCache: [String: (url: URL, expires: Date)] = [:]

    private init() {}

    public func resolveStream(for track: Track) async -> URL? {
        // 1. Check in-memory stream URL cache
        let cacheKey = track.spotifyId.isEmpty ? "\(track.artist) \(track.title)" : track.spotifyId
        if let cached = streamCache[cacheKey], cached.expires > Date() {
            return cached.url
        }

        // 2. Resolve YouTube Video ID (check permanent cache first)
        var videoId = PreferencesService.shared.getPermanentVideoId(for: track.spotifyId)
        if videoId == nil {
            let query = "\(track.artist) - \(track.title)"
            videoId = await searchVideoId(query: query)
            if let vid = videoId, !track.spotifyId.isEmpty {
                PreferencesService.shared.setPermanentVideoId(vid, for: track.spotifyId)
            }
        }

        guard let vid = videoId else { return nil }

        // 3. Resolve direct audio stream from player endpoint
        guard let streamUrl = await fetchStreamUrl(videoId: vid) else { return nil }

        // Cache for 5 hours (standard YouTube stream URLs expire after 6h)
        streamCache[cacheKey] = (url: streamUrl, expires: Date().addingTimeInterval(5 * 3600))
        return streamUrl
    }

    private func searchVideoId(query: String) async -> String? {
        guard let url = URL(string: "https://music.youtube.com/youtubei/v1/search?prettyPrint=false") else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.addValue("https://music.youtube.com", forHTTPHeaderField: "Origin")
        request.addValue("https://music.youtube.com", forHTTPHeaderField: "Referer")
        request.addValue("67", forHTTPHeaderField: "X-YouTube-Client-Name")
        request.addValue("1.20240101.01.00", forHTTPHeaderField: "X-YouTube-Client-Version")

        let body: [String: Any] = [
            "context": [
                "client": [
                    "clientName": "WEB_REMIX",
                    "clientVersion": "1.20240101.01.00",
                    "hl": "en",
                    "gl": "US"
                ]
            ],
            "query": query,
            "params": "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D" // Filter to Songs
        ]

        guard let httpBody = try? JSONSerialization.data(withJSONObject: body) else { return nil }
        request.httpBody = httpBody

        do {
            let (data, _) = try await session.data(for: request)
            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
                return extractFirstVideoId(from: json)
            }
        } catch {
            print("InnerTube search failed: \(error.localizedDescription)")
        }
        return nil
    }

    private func fetchStreamUrl(videoId: String) async -> URL? {
        guard let url = URL(string: "https://music.youtube.com/youtubei/v1/player?prettyPrint=false") else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.addValue("com.google.ios.youtubemusic/6.42 (iPhone; iOS 17.5.1; Scale/3.00)", forHTTPHeaderField: "User-Agent")

        let body: [String: Any] = [
            "context": [
                "client": [
                    "clientName": "IOS",
                    "clientVersion": "19.29.1",
                    "deviceMake": "Apple",
                    "deviceModel": "iPhone16,2",
                    "hl": "en",
                    "gl": "US"
                ]
            ],
            "videoId": videoId,
            "contentCheckOk": true,
            "racyCheckOk": true
        ]

        guard let httpBody = try? JSONSerialization.data(withJSONObject: body) else { return nil }
        request.httpBody = httpBody

        do {
            let (data, _) = try await session.data(for: request)
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let streamingData = json["streamingData"] as? [String: Any] else {
                return nil
            }

            // Look for audio formats (prefer adaptiveFormats with audio/mp4 / m4a or audio/webm)
            let adaptiveFormats = (streamingData["adaptiveFormats"] as? [[String: Any]]) ?? []
            let directFormats = (streamingData["formats"] as? [[String: Any]]) ?? []
            let allFormats = adaptiveFormats + directFormats

            let audioFormats = allFormats.filter { format in
                let mime = (format["mimeType"] as? String) ?? ""
                return mime.contains("audio/mp4") || mime.contains("audio/webm") || mime.contains("audio/aac")
            }

            // Pick highest bitrate audio format
            if let best = audioFormats.max(by: { ($0["bitrate"] as? Int ?? 0) < ($1["bitrate"] as? Int ?? 0) }),
               let urlString = best["url"] as? String,
               let streamUrl = URL(string: urlString) {
                return streamUrl
            }
        } catch {
            print("InnerTube player stream resolution error: \(error.localizedDescription)")
        }
        return nil
    }

    private func extractFirstVideoId(from json: [String: Any]) -> String? {
        guard let contents = json["contents"] as? [String: Any],
              let tabbed = contents["tabbedSearchResultsRenderer"] as? [String: Any],
              let tabs = tabbed["tabs"] as? [[String: Any]],
              let firstTab = tabs.first,
              let tabRenderer = firstTab["tabRenderer"] as? [String: Any],
              let content = tabRenderer["content"] as? [String: Any],
              let sectionList = content["sectionListRenderer"] as? [String: Any],
              let sectionContents = sectionList["contents"] as? [[String: Any]] else {
            return nil
        }

        for section in sectionContents {
            if let shelf = section["musicShelfRenderer"] as? [String: Any],
               let items = shelf["contents"] as? [[String: Any]] {
                for item in items {
                    if let renderer = item["musicResponsiveListItemRenderer"] as? [String: Any],
                       let flexColumns = renderer["flexColumns"] as? [[String: Any]],
                       let firstCol = flexColumns.first,
                       let colRenderer = firstCol["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any],
                       let text = colRenderer["text"] as? [String: Any],
                       let runs = text["runs"] as? [[String: Any]],
                       let navEndpoint = runs.first?["navigationEndpoint"] as? [String: Any],
                       let watchEndpoint = navEndpoint["watchEndpoint"] as? [String: Any],
                       let videoId = watchEndpoint["videoId"] as? String {
                        return videoId
                    }
                }
            }
        }
        return nil
    }
}
