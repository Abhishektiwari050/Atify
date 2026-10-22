import Foundation

public struct Track: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public let title: String
    public let artist: String
    public let album: String
    public let coverUrl: String
    public let durationMs: Int
    public let spotifyId: String
    public var streamUrl: String?
    public var isExplicit: Bool

    public init(
        id: String,
        title: String,
        artist: String,
        album: String = "",
        coverUrl: String = "",
        durationMs: Int = 0,
        spotifyId: String = "",
        streamUrl: String? = nil,
        isExplicit: Bool = false
    ) {
        self.id = id
        self.title = title
        self.artist = artist
        self.album = album
        self.coverUrl = coverUrl
        self.durationMs = durationMs
        self.spotifyId = spotifyId
        self.streamUrl = streamUrl
        self.isExplicit = isExplicit
    }

    public var formattedDuration: String {
        let totalSeconds = durationMs / 1000
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
}

public struct Album: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public let title: String
    public let artist: String
    public let coverUrl: String
    public let year: String
    public var tracks: [Track]

    public init(id: String, title: String, artist: String, coverUrl: String, year: String = "", tracks: [Track] = []) {
        self.id = id
        self.title = title
        self.artist = artist
        self.coverUrl = coverUrl
        self.year = year
        self.tracks = tracks
    }
}

public struct Artist: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public let name: String
    public let imageUrl: String

    public init(id: String, name: String, imageUrl: String = "") {
        self.id = id
        self.name = name
        self.imageUrl = imageUrl
    }
}

public struct Playlist: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let coverUrl: String
    public var tracks: [Track]

    public init(id: String, title: String, subtitle: String = "", coverUrl: String = "", tracks: [Track] = []) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.coverUrl = coverUrl
        self.tracks = tracks
    }
}
