package io.github.sekademi.spotufi.data.entity

/** Combined Spotify search results: tracks, albums, artists, playlists, podcasts + episodes. */
data class SearchResults(
    val songs: List<SongsModel> = emptyList(),
    val albums: List<AlbumsModel> = emptyList(),
    val artists: List<ArtistsModel> = emptyList(),
    val playlists: List<LibraryEntry> = emptyList(),
    val shows: List<PodcastModel> = emptyList(),
    val episodes: List<SongsModel> = emptyList(),
)
