package io.github.sekademi.spotufi.data.api

import android.content.Context
import android.util.Log
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.models.SpotifyAlbum
import com.metrolist.spotify.models.SpotifyArtist
import com.metrolist.spotify.models.SpotifyTrack
import io.github.sekademi.spotufi.data.entity.AlbumsModel
import io.github.sekademi.spotufi.data.entity.PodcastModel
import io.github.sekademi.spotufi.data.entity.ArtistOverviewModel
import io.github.sekademi.spotufi.data.entity.ArtistTrackUi
import com.metrolist.spotify.models.SpotifyHomeFeedItem
import io.github.sekademi.spotufi.data.entity.ArtistsModel
import io.github.sekademi.spotufi.data.entity.HomeFeedModel
import io.github.sekademi.spotufi.data.entity.HomeItem
import io.github.sekademi.spotufi.data.entity.HomeSection
import io.github.sekademi.spotufi.data.entity.SearchResults
import io.github.sekademi.spotufi.data.entity.SongsModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem
import javax.inject.Inject

/**
 * Spotify-backed replacement for the original Firebase Firestore data source.
 * Preserves the original `Flow<Response<List<...>>>` contract so the existing
 * ViewModels / UI keep working unchanged.
 *
 * Metadata (albums, artists, songs) comes from Spotify's web API; actual audio
 * is resolved from YouTube at playback time (see SongPlayer), so [SongsModel.url]
 * carries a stable playback key with the Spotify id plus the title/artist search
 * text, rather than a direct stream URL.
 */
class Api @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun stableId(key: String): Int = key.hashCode() and 0x7fffffff

    /**
     * Process-level cache for the Home feeds. The Home ViewModel is recreated on
     * every navigation back to Home and would otherwise re-hit the network each
     * time (slow). Once loaded we emit the cached result instantly, then refresh
     * in the background so the list stays fresh without blocking the UI.
     */
    companion object HomeCache {
        /** Sentinel id for the special pinned "Liked Songs" library entry. */
        const val LIKED_SONGS_ID = "liked-songs"
        /** Sentinel id for the special pinned "Downloaded" library entry. */
        const val DOWNLOADS_ID = "downloaded-offline"

        @Volatile var albums: List<AlbumsModel>? = null
        @Volatile var artists: List<ArtistsModel>? = null
        @Volatile var home: HomeFeedModel? = null
        @Volatile var library: List<io.github.sekademi.spotufi.data.entity.LibraryEntry>? = null

        /** Drop all cached feeds (e.g. on logout / account switch). */
        fun clear() {
            albums = null; artists = null; home = null; library = null
        }
    }

    private fun SpotifyTrack.toSongModel(): SongsModel {
        val singer = artists.joinToString(", ") { it.name }
        val cover = album?.images?.firstOrNull()?.url ?: ""
        return SongsModel(
            id = stableId("track:$id"),
            title = name.take(128),
            album = album?.name ?: "",
            singer = singer,
            coverUri = cover,
            url = io.github.sekademi.spotufi.di.SongPlayer.buildSpotifyPlayQuery(id, name, singer),
            spotifyTrackId = id,
            explicit = explicit,
            durationMs = durationMs,
            artistIds = artists.joinToString(",") { it.id.orEmpty() },
        )
    }

    private fun SpotifyAlbum.toAlbumModel(): AlbumsModel = AlbumsModel(
        id = stableId("album:$id"),
        artists = artists.joinToString(", ") { it.name },
        coverUri = images.firstOrNull()?.url ?: "",
        name = name,
        time = releaseDate ?: "",
        type = albumType.orEmpty(),
    )

    private fun SpotifyArtist.toArtistModel(): ArtistsModel = ArtistsModel(
        name = name,
        coverUri = images.firstOrNull()?.url ?: "",
        id = id,
    )

    suspend fun getAlbums(): Flow<Response<List<AlbumsModel>>> = flow {
        HomeCache.albums?.let { emit(Response.Success(it)) } ?: emit(Response.Loading())
        if (!SpotifyTokenProvider.ensureToken(context)) {
            if (HomeCache.albums == null) emit(Response.Error("Spotify not authenticated — set sp_dc cookie"))
            return@flow
        }
        Spotify.newReleases(limit = 20).fold(
            onSuccess = { resp ->
                val list = resp.albums?.items.orEmpty().map { it.toAlbumModel() }
                HomeCache.albums = list
                emit(Response.Success(list))
            },
            onFailure = {
                Log.e("Api", "getAlbums failed", it)
                if (HomeCache.albums == null) emit(Response.Error(it.message ?: "error"))
            },
        )
    }

    /** Artists the user follows on Spotify (library "Artists" filter). */
    suspend fun getFollowedArtists(): List<ArtistsModel> {
        if (!SpotifyTokenProvider.ensureToken(context)) return emptyList()
        return fetchAllPages { offset -> Spotify.myArtists(limit = 50, offset = offset) }
            .map { it.toArtistModel() }
            .also { if (it.isEmpty()) Log.d("Api", "getFollowedArtists: none") }
    }

    suspend fun getArtists(): Flow<Response<List<ArtistsModel>>> = flow {
        HomeCache.artists?.let { emit(Response.Success(it)) } ?: emit(Response.Loading())
        if (!SpotifyTokenProvider.ensureToken(context)) {
            if (HomeCache.artists == null) emit(Response.Error("Spotify not authenticated — set sp_dc cookie"))
            return@flow
        }
        Spotify.topArtists(limit = 20).fold(
            onSuccess = { paging ->
                val list = paging.items.map { it.toArtistModel() }
                HomeCache.artists = list
                emit(Response.Success(list))
            },
            onFailure = {
                Log.e("Api", "getArtists failed", it)
                if (HomeCache.artists == null) emit(Response.Error(it.message ?: "error"))
            },
        )
    }

    suspend fun getSongs(): Flow<Response<List<SongsModel>>> = flow {
        emit(Response.Loading())
        if (!SpotifyTokenProvider.ensureToken(context)) {
            emit(Response.Error("Spotify not authenticated — set sp_dc cookie")); return@flow
        }
        Spotify.topTracks(limit = 50).fold(
            onSuccess = { paging -> emit(Response.Success(paging.items.map { it.toSongModel() })) },
            onFailure = { Log.e("Api", "getSongs failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /**
     * Personalized Spotify home feed (the `home` GQL operation) — the real
     * landing page: "Your top mixes", "Jump back in", "Your favorite artists",
     * etc. Process-cached like the other home feeds for instant re-entry.
     */
    suspend fun getHomeFeed(): Flow<Response<HomeFeedModel>> = flow {
        HomeCache.home?.let { emit(Response.Success(it)) } ?: emit(Response.Loading())
        if (!SpotifyTokenProvider.ensureToken(context)) {
            if (HomeCache.home == null) emit(Response.Error("Spotify not authenticated — set sp_dc cookie"))
            return@flow
        }
        Spotify.home(sectionItemsLimit = 20).fold(
            onSuccess = { feed ->
                val sections = feed.sections.mapNotNull { section ->
                    // The GQL feed can repeat an item inside one section (and
                    // repeat whole sections, e.g. two "New releases" rows) —
                    // that showed up as duplicated album cards on Home.
                    val items = section.items
                        .distinctBy { it.uri }
                        .mapNotNull { it.toHomeItem() }
                        .distinctBy { it::class.simpleName + "|" + it.name.lowercase() }
                    if (items.isEmpty()) null
                    else HomeSection(title = section.title ?: "", items = items)
                }.distinctBy { it.title.lowercase().ifBlank { it.hashCode().toString() } }
                val model = HomeFeedModel(greeting = feed.greeting ?: "", sections = sections)
                HomeCache.home = model
                emit(Response.Success(model))
            },
            onFailure = {
                Log.e("Api", "getHomeFeed failed", it)
                if (HomeCache.home == null) emit(Response.Error(it.message ?: "error"))
            },
        )
    }

    private fun SpotifyHomeFeedItem.toHomeItem(): HomeItem? = when (this) {
        is SpotifyHomeFeedItem.Album -> HomeItem.Album(
            name = name,
            imageUrl = imageUrl ?: "",
            subtitle = artists.joinToString(", ") { it.name }.ifBlank { "Album" },
            artists = artists.joinToString(", ") { it.name },
        )
        is SpotifyHomeFeedItem.Artist -> HomeItem.Artist(
            name = name,
            imageUrl = imageUrl ?: "",
            id = id,
        )
        is SpotifyHomeFeedItem.Playlist -> HomeItem.Playlist(
            name = name,
            imageUrl = imageUrl ?: "",
            subtitle = (madeForUsername ?: ownerName)?.let { "Playlist • $it" } ?: "Playlist",
            id = id,
        )
    }

    /**
     * Live track search via Spotify's GraphQL search (not the rate-limited
     * personalized endpoints). Blank query yields an empty list.
     */
    suspend fun searchTracks(query: String): Flow<Response<List<SongsModel>>> = flow {
        emit(Response.Loading())
        if (query.isBlank()) {
            emit(Response.Success(emptyList())); return@flow
        }
        if (!SpotifyTokenProvider.ensureToken(context)) {
            emit(Response.Error("Spotify not authenticated — set sp_dc cookie")); return@flow
        }
        Spotify.search(query, types = listOf("track"), limit = 30).fold(
            onSuccess = { res -> emit(Response.Success(res.tracks?.items.orEmpty().map { it.toSongModel() })) },
            onFailure = { Log.e("Api", "searchTracks failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /**
     * Combined search: tracks + albums + artists + podcasts across Spotify
     * and InnerTube (YouTube Music) in parallel.
     * Guarantees songs not on Spotify (remixes, leaks, unreleased tracks,
     * regional music) are discoverable and playable.
     * Also falls back to YouTube podcasts and episodes if Spotify's podcast
     * search is empty or unauthenticated.
     */
    suspend fun searchEverything(query: String): Flow<Response<SearchResults>> = flow {
        emit(Response.Loading())
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            emit(Response.Success(SearchResults())); return@flow
        }
        val hasSpotify = SpotifyTokenProvider.ensureToken(context)

        coroutineScope {
            val spDeferred = async(Dispatchers.IO) {
                if (hasSpotify) {
                    Spotify.search(trimmed, types = listOf("track", "album", "artist"), limit = 20).getOrNull()
                } else null
            }
            val spPodcastsDeferred = async(Dispatchers.IO) {
                if (hasSpotify) {
                    Spotify.searchPodcasts(trimmed, limit = 12).getOrNull()
                } else null
            }
            val ytSongsDeferred = async(Dispatchers.IO) {
                runCatching {
                    YouTube.search(trimmed, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                }.getOrNull()
            }
            val ytVideosDeferred = async(Dispatchers.IO) {
                runCatching {
                    YouTube.search(trimmed, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                }.getOrNull()
            }

            val spRes = spDeferred.await()
            val spPodcasts = spPodcastsDeferred.await()
            val ytSongRes = ytSongsDeferred.await()
            val ytVideoRes = ytVideosDeferred.await()

            // 1. Process Spotify songs, albums, artists
            val spSongs = spRes?.tracks?.items.orEmpty().map { it.toSongModel() }
            val albums = spRes?.albums?.items.orEmpty().map { it.toAlbumModel() }
            val artists = spRes?.artists?.items.orEmpty().map { it.toArtistModel() }

            // 2. Process YouTube songs
            val ytSongs = ytSongRes?.items.orEmpty().mapNotNull { item ->
                when (item) {
                    is SongItem -> {
                        val artistName = item.artists.joinToString(", ") { it.name }.ifBlank { "YouTube" }
                        val cleanTitle = io.github.sekademi.spotufi.di.StreamResolver.cleanSpotifySearchTitle(item.title)
                        SongsModel(
                            id = stableId("yt:${item.id}"),
                            title = item.title,
                            album = item.album?.name ?: "Single",
                            singer = artistName,
                            coverUri = item.thumbnail,
                            url = "youtube:${item.id}|$cleanTitle $artistName",
                            spotifyTrackId = "",
                            explicit = item.explicit,
                            durationMs = (item.duration ?: 0) * 1000,
                        )
                    }
                    else -> null
                }
            }

            // Deduplicate: start with Spotify songs, then append unique YouTube songs
            val spSongKeys = spSongs.map {
                "${it.title.lowercase().trim()}|${it.singer.lowercase().trim()}"
            }.toSet()
            val uniqueYtSongs = ytSongs.filterNot { yt ->
                val key = "${yt.title.lowercase().trim()}|${yt.singer.lowercase().trim()}"
                key in spSongKeys
            }
            val combinedSongs = spSongs + uniqueYtSongs

            // 3. Process Podcasts: Spotify + YouTube fallback
            val spShows = spPodcasts?.shows?.items.orEmpty().map { it.toPodcastModel() }
            val spEpisodes = spPodcasts?.episodes?.items.orEmpty().map { it.toEpisodeSongModel(null) }

            val ytEpisodes = (ytVideoRes?.items.orEmpty() + ytSongRes?.items.orEmpty()).mapNotNull { item ->
                when (item) {
                    is EpisodeItem -> {
                        val showTitle = item.podcast?.name ?: item.author?.name ?: "Podcast"
                        val cleanTitle = io.github.sekademi.spotufi.di.StreamResolver.cleanSpotifySearchTitle(item.title)
                        SongsModel(
                            id = stableId("yt_ep:${item.id}"),
                            title = item.title,
                            album = showTitle,
                            singer = showTitle,
                            coverUri = item.thumbnail,
                            url = "youtube:${item.id}|$cleanTitle $showTitle",
                            spotifyTrackId = "",
                            explicit = item.explicit,
                            durationMs = (item.duration ?: 0) * 1000,
                        )
                    }
                    is SongItem -> {
                        if (item.isEpisode) {
                            val showTitle = item.album?.name ?: item.artists.joinToString(", ") { it.name }.ifBlank { "Podcast" }
                            val cleanTitle = io.github.sekademi.spotufi.di.StreamResolver.cleanSpotifySearchTitle(item.title)
                            SongsModel(
                                id = stableId("yt_ep:${item.id}"),
                                title = item.title,
                                album = showTitle,
                                singer = showTitle,
                                coverUri = item.thumbnail,
                                url = "youtube:${item.id}|$cleanTitle $showTitle",
                                spotifyTrackId = "",
                                explicit = item.explicit,
                                durationMs = (item.duration ?: 0) * 1000,
                            )
                        } else null
                    }
                    else -> null
                }
            }.distinctBy { it.id }

            val ytShows = ytVideoRes?.items.orEmpty().mapNotNull { item ->
                when (item) {
                    is PodcastItem -> {
                        PodcastModel(
                            id = item.id,
                            name = item.title,
                            publisher = item.author?.name ?: "Podcast",
                            coverUri = item.thumbnail ?: "",
                        )
                    }
                    else -> null
                }
            }

            // Fallback video episodes when no explicit podcast items returned
            val fallbackVideoEpisodes = if (spEpisodes.isEmpty() && ytEpisodes.isEmpty()) {
                ytVideoRes?.items.orEmpty().filterIsInstance<SongItem>().take(10).map { item ->
                    val creator = item.artists.joinToString(", ") { it.name }.ifBlank { "Creator" }
                    val cleanTitle = io.github.sekademi.spotufi.di.StreamResolver.cleanSpotifySearchTitle(item.title)
                    SongsModel(
                        id = stableId("yt_vid:${item.id}"),
                        title = item.title,
                        album = "Video",
                        singer = creator,
                        coverUri = item.thumbnail,
                        url = "youtube:${item.id}|$cleanTitle $creator",
                        spotifyTrackId = "",
                        explicit = item.explicit,
                        durationMs = (item.duration ?: 0) * 1000,
                    )
                }
            } else emptyList()

            val combinedShows = (spShows + ytShows).distinctBy { it.id }
            val combinedEpisodes = (spEpisodes + ytEpisodes + fallbackVideoEpisodes).distinctBy { it.id }

            if (combinedSongs.isEmpty() && albums.isEmpty() && artists.isEmpty() && combinedShows.isEmpty() && combinedEpisodes.isEmpty()) {
                if (!hasSpotify) {
                    emit(Response.Error("No results found across Spotify & YouTube"))
                } else {
                    emit(Response.Success(SearchResults()))
                }
            } else {
                emit(Response.Success(SearchResults(
                    songs = combinedSongs,
                    albums = albums,
                    artists = artists,
                    shows = combinedShows,
                    episodes = combinedEpisodes,
                )))
            }
        }
    }

    /** A podcast show's episodes, as playable [SongsModel] (url = "episode:<id>"). */
    suspend fun getShowEpisodes(showId: String): Flow<Response<List<SongsModel>>> = flow {
        emit(Response.Loading())
        if (!SpotifyTokenProvider.ensureToken(context)) {
            emit(Response.Error("Spotify not authenticated — set sp_dc cookie")); return@flow
        }
        val show = Spotify.show(showId).getOrNull()
        Spotify.showEpisodes(showId, limit = 50).fold(
            onSuccess = { paging ->
                emit(Response.Success(paging.items.map { it.toEpisodeSongModel(show?.name) }))
            },
            onFailure = { Log.e("Api", "getShowEpisodes failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /** Show header (name/publisher/cover) for the detail screen. */
    suspend fun getShow(showId: String): PodcastModel? {
        if (!SpotifyTokenProvider.ensureToken(context)) return null
        return Spotify.show(showId).getOrNull()?.toPodcastModel()
    }

    private fun com.metrolist.spotify.models.SpotifyShow.toPodcastModel() = PodcastModel(
        id = id,
        name = name,
        publisher = publisher,
        coverUri = images.firstOrNull()?.url ?: "",
    )

    private fun com.metrolist.spotify.models.SpotifyEpisode.toEpisodeSongModel(showName: String?): SongsModel {
        val subtitle = show?.name ?: showName ?: "Podcast"
        val cleanTitle = io.github.sekademi.spotufi.di.StreamResolver.cleanSpotifySearchTitle(name)
        val playQuery = "episode:$id|$cleanTitle $subtitle"
        return SongsModel(
            id = stableId("episode:$id"),
            title = name,
            album = subtitle,
            singer = subtitle,
            coverUri = images.firstOrNull()?.url ?: (show?.images?.firstOrNull()?.url ?: ""),
            url = playQuery,
            spotifyTrackId = "",
            explicit = false,
            durationMs = durationMs,
        )
    }

    /**
     * Spotify's recommendation engine: given a few seed track ids (the tracks the
     * user is currently/recently playing), returns a list of recommended tracks to
     * extend the queue with — i.e. the "autoplay radio" that keeps music going once
     * a playlist/album ends. Up to 5 seeds are allowed by Spotify; we cap there.
     * Returns an empty list on failure so callers can simply fall back to looping.
     */
    suspend fun getRecommendations(seedTrackIds: List<String>): List<SongsModel> {
        val seeds = seedTrackIds.filter { it.isNotBlank() }.distinct()
        if (seeds.isEmpty()) return emptyList()
        if (!SpotifyTokenProvider.ensureToken(context)) return emptyList()
        val seedId = seeds.last()
        // Primary: the exact radio the Spotify web player queues after this track —
        // the inspiredby-mix station playlist. This IS Spotify's real queue, so the
        // continuation matches what open.spotify.com would play next.
        Spotify.trackRadio(seedId).getOrNull()
            ?.filter { it.id != seedId }
            ?.takeIf { it.isNotEmpty() }
            ?.let { radio -> return radio.map { it.toSongModel() } }
        Log.w("Api", "track radio empty — trying native recommender")
        // Fallback 1: Spotify's own recommender (the SEO "recommended tracks" GQL op).
        // Still Spotify's real backend, so it beats any local heuristic when available.
        Spotify.recommendedTracks(seedId).getOrNull()?.takeIf { it.isNotEmpty() }?.let { native ->
            return native.map { it.toSongModel() }
        }
        Log.w("Api", "native recommender empty — trying artist radio")
        val seedTrack = Spotify.track(seedId).getOrNull()
            ?: return emptyList<SongsModel>().also { Log.w("Api", "getRecommendations: seed track unresolved") }

        // Fallback 2: artist radio built purely from GQL endpoints (seed artist top
        // tracks + related artists' top tracks). Unlike the heuristic engine this does
        // NOT depend on me/top/tracks|artists, which are frequently HTTP 429 rate-limited
        // with the web token — so it keeps working when the profile-based engine can't.
        artistRadio(seedTrack).takeIf { it.isNotEmpty() }?.let { radio ->
            return radio.map { it.toSongModel() }
        }

        Log.w("Api", "artist radio empty — falling back to heuristic engine")
        // Fallback 3: profile-based heuristic engine (best-personalized but needs top data).
        return io.github.sekademi.spotufi.data.recommendation.SpotifyRecommendationEngine
            .getRecommendations(seedTrack, limit = 25)
            .map { it.toSongModel() }
            .ifEmpty { emptyList<SongsModel>().also { Log.w("Api", "getRecommendations returned no tracks") } }
    }

    /**
     * A reliable "song radio" from a seed track using only GQL endpoints (not the
     * rate-limited REST top-data ones): the seed artist's top tracks plus the top
     * tracks of related artists, deduped and shuffled. Mirrors how tapping a single
     * song on Spotify continues into similar music.
     */
    private suspend fun artistRadio(seedTrack: SpotifyTrack): List<SpotifyTrack> {
        val seedArtistId = seedTrack.artists.firstOrNull()?.id ?: return emptyList()
        val out = mutableListOf<SpotifyTrack>()
        val seen = mutableSetOf(seedTrack.id)
        fun add(tracks: List<SpotifyTrack>?, cap: Int) {
            tracks.orEmpty().asSequence()
                .filter { it.id.isNotEmpty() && seen.add(it.id) }
                .take(cap)
                .forEach { out.add(it) }
        }
        add(Spotify.artistTopTracks(seedArtistId).getOrNull()?.tracks, cap = 10)
        Spotify.artistRelatedArtists(seedArtistId).getOrNull().orEmpty().take(6).forEach { related ->
            related.id.takeIf { it.isNotEmpty() }?.let { rid ->
                add(Spotify.artistTopTracks(rid).getOrNull()?.tracks, cap = 5)
            }
        }
        return out.shuffled().take(30)
    }

    /**
     * Generates a personalized "Quick Picks • Made For You" track list for the Home Screen.
     * Incorporates user's listening history, liked tracks, taste profile, and Spotify
     * algorithmic recommendations. Never returns an empty list on a working network connection.
     */
    suspend fun getPersonalizedQuickPicks(): List<SongsModel> {
        val hasSpotify = SpotifyTokenProvider.ensureToken(context)

        // 1. Seed from user's local listening history (most recent played tracks)
        val history = runCatching {
            io.github.sekademi.spotufi.data.preferences.getListeningHistory(context)
        }.getOrDefault(emptyList())

        val historySeeds = history
            .mapNotNull { entry ->
                io.github.sekademi.spotufi.di.StreamResolver.spotifyTrackIdForPlayback(entry.url)
            }
            .distinct()
            .take(5)

        if (hasSpotify && historySeeds.isNotEmpty()) {
            val recs = runCatching { getRecommendations(historySeeds) }.getOrDefault(emptyList())
            if (recs.isNotEmpty()) {
                val recentModels = history.take(4).map { entry ->
                    val sid = io.github.sekademi.spotufi.di.StreamResolver.spotifyTrackIdForPlayback(entry.url).orEmpty()
                    SongsModel(
                        id = entry.songId,
                        title = entry.title,
                        album = entry.album,
                        singer = entry.singer,
                        coverUri = entry.image,
                        url = entry.url.ifBlank {
                            if (sid.isNotBlank()) {
                                io.github.sekademi.spotufi.di.SongPlayer.buildSpotifyPlayQuery(sid, entry.title, entry.singer)
                            } else entry.title
                        },
                        spotifyTrackId = sid,
                    )
                }
                return (recentModels + recs).distinctBy { it.id }.take(20)
            }
        }

        // 2. Seed from Spotify Liked Songs / Saved Tracks
        if (hasSpotify) {
            val liked = runCatching { Spotify.likedSongs(limit = 20).getOrNull()?.items }.getOrNull()
            if (!liked.isNullOrEmpty()) {
                val likedModels = liked.map { it.track.toSongModel() }
                val likedSeeds = likedModels.map { it.spotifyTrackId }.filter { it.isNotBlank() }.take(5)
                val recs = if (likedSeeds.isNotEmpty()) {
                    runCatching { getRecommendations(likedSeeds) }.getOrDefault(emptyList())
                } else emptyList()
                val blended = (likedModels.shuffled().take(6) + recs).distinctBy { it.id }.take(20)
                if (blended.isNotEmpty()) return blended
            }
        }

        // 3. Seed from local taste profile affinities
        val tasteProfile = runCatching {
            io.github.sekademi.spotufi.data.recommendation.TasteProfileEngine.getProfile(context)
        }.getOrNull()
        val topArtist = tasteProfile?.artistAffinities?.maxByOrNull { it.value }?.key
        if (hasSpotify && !topArtist.isNullOrBlank()) {
            val artistSearch = runCatching {
                Spotify.search(topArtist, types = listOf("track"), limit = 15).getOrNull()
            }.getOrNull()
            val tracks = artistSearch?.tracks?.items.orEmpty().map { it.toSongModel() }
            if (tracks.isNotEmpty()) return tracks
        }

        // 4. Fallback to Spotify's top global hits / new releases
        if (hasSpotify) {
            val topTracks = runCatching {
                Spotify.search("Top 50", types = listOf("track"), limit = 20).getOrNull()
            }.getOrNull()
            val tracks = topTracks?.tracks?.items.orEmpty().map { it.toSongModel() }
            if (tracks.isNotEmpty()) return tracks
        }

        // 5. Ultimate network fallback: InnerTube trending / popular music
        val ytTrending = runCatching {
            YouTube.search("Popular Music Hits", YouTube.SearchFilter.FILTER_SONG).getOrNull()
        }.getOrNull()
        val ytTracks = ytTrending?.items.orEmpty().mapNotNull { item ->
            when (item) {
                is SongItem -> {
                    val artistName = item.artists.joinToString(", ") { it.name }.ifBlank { "Top Artist" }
                    val cleanTitle = io.github.sekademi.spotufi.di.StreamResolver.cleanSpotifySearchTitle(item.title)
                    SongsModel(
                        id = stableId("yt_trend:${item.id}"),
                        title = item.title,
                        album = item.album?.name ?: "Trending",
                        singer = artistName,
                        coverUri = item.thumbnail,
                        url = "youtube:${item.id}|$cleanTitle $artistName",
                        spotifyTrackId = "",
                        explicit = item.explicit,
                        durationMs = (item.duration ?: 0) * 1000,
                    )
                }
                else -> null
            }
        }
        if (ytTracks.isNotEmpty()) return ytTracks

        return emptyList()
    }

    /**
     * Loads the curated playlists for a Browse category/genre. Real Spotify opens
     * a genre catalogue (a page of playlists) rather than running a keyword song
     * search — we approximate that by searching playlists for the genre name and
     * presenting them as a grid the user can open. Returns LibraryEntry rows so
     * the Category screen can reuse the existing playlist row/tile rendering.
     */
    suspend fun getCategoryPlaylists(genre: String): Flow<Response<List<io.github.sekademi.spotufi.data.entity.LibraryEntry>>> = flow {
        emit(Response.Loading())
        if (genre.isBlank()) {
            emit(Response.Success(emptyList())); return@flow
        }
        if (!SpotifyTokenProvider.ensureToken(context)) {
            emit(Response.Error("Spotify not authenticated — set sp_dc cookie")); return@flow
        }
        Spotify.search(genre, types = listOf("playlist"), limit = 24).fold(
            onSuccess = { res ->
                emit(Response.Success(res.playlists?.items.orEmpty().map { p ->
                    io.github.sekademi.spotufi.data.entity.LibraryEntry(
                        spotifyId = p.id,
                        name = p.name,
                        subtitle = "Playlist" + (p.owner?.displayName?.let { " • $it" } ?: ""),
                        coverUri = p.images.firstOrNull()?.url ?: "",
                        isPlaylist = true,
                    )
                }))
            },
            onFailure = { Log.e("Api", "getCategoryPlaylists failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /**
     * Loads an artist's top tracks. The UI navigates by artist *name*, so we
     * resolve the artist via GraphQL search, then fetch its top tracks via the
     * GraphQL queryArtistOverview endpoint (neither is rate-limited).
     */
    suspend fun getArtistSongs(artistName: String): Flow<Response<List<SongsModel>>> = flow {
        emit(Response.Loading())
        if (artistName.isBlank()) {
            emit(Response.Success(emptyList())); return@flow
        }
        if (!SpotifyTokenProvider.ensureToken(context)) {
            emit(Response.Error("Spotify not authenticated — set sp_dc cookie")); return@flow
        }
        val artist = Spotify.search(artistName, types = listOf("artist"), limit = 1).getOrNull()
            ?.artists?.items?.firstOrNull()
        val artistId = artist?.id
        if (artistId.isNullOrBlank()) {
            emit(Response.Success(emptyList())); return@flow
        }
        val artistCover = artist.images.firstOrNull()?.url ?: ""
        Spotify.artistTopTracks(artistId).fold(
            onSuccess = { resp ->
                emit(Response.Success(resp.tracks.map { track ->
                    val song = track.toSongModel()
                    // GQL top-tracks often omit album art — fall back to the artist image.
                    if (song.coverUri.isBlank()) song.copy(coverUri = artistCover) else song
                }))
            },
            onFailure = { Log.e("Api", "getArtistSongs failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /**
     * Loads the full Spotify-style artist page (header, monthly listeners, bio,
     * popular tracks with play counts, discography, related artists) in one GQL
     * round-trip. When the caller knows the exact Spotify artist id it is used
     * directly; otherwise the name is resolved via search (fuzzy — a query like
     * "RAM" may resolve to "Rammstein", so ids are strongly preferred).
     */
    suspend fun getArtistOverview(artistName: String, knownArtistId: String = ""): Flow<Response<ArtistOverviewModel>> = flow {
        emit(Response.Loading())
        if (artistName.isBlank() && knownArtistId.isBlank()) {
            emit(Response.Success(ArtistOverviewModel(name = artistName))); return@flow
        }
        if (!SpotifyTokenProvider.ensureToken(context)) {
            emit(Response.Error("Spotify not authenticated — set sp_dc cookie")); return@flow
        }
        val artist = if (knownArtistId.isBlank()) {
            // Prefer an EXACT name match among the top hits — the first fuzzy
            // hit for a short name like "RAM" can be a different artist entirely.
            val hits = Spotify.search(artistName, types = listOf("artist"), limit = 5).getOrNull()
                ?.artists?.items.orEmpty()
            hits.firstOrNull { it.name.equals(artistName, ignoreCase = true) } ?: hits.firstOrNull()
        } else null
        val artistId = knownArtistId.ifBlank { artist?.id.orEmpty() }
        if (artistId.isBlank()) {
            emit(Response.Error("Artist not found")); return@flow
        }
        val searchCover = artist?.images?.firstOrNull()?.url ?: ""
        Spotify.artistOverview(artistId).fold(
            onSuccess = { o ->
                val avatar = o.avatarImages.firstOrNull()?.url?.ifBlank { null } ?: searchCover
                val header = o.headerImages.firstOrNull()?.url?.ifBlank { null } ?: avatar
                emit(Response.Success(ArtistOverviewModel(
                    id = o.id,
                    name = o.name.ifBlank { artistName },
                    verified = o.verified,
                    monthlyListeners = o.monthlyListeners,
                    biography = o.biography,
                    headerImage = header,
                    avatarImage = avatar,
                    topTracks = o.topTracks.map { t ->
                        val song = t.track.toSongModel()
                        ArtistTrackUi(
                            song = if (song.coverUri.isBlank()) song.copy(coverUri = avatar) else song,
                            playcount = t.playcount,
                        )
                    },
                    popularReleases = o.popularReleases.map { it.toAlbumModel() },
                    appearsOn = o.appearsOn.map { it.toAlbumModel() },
                    relatedArtists = o.relatedArtists.map { it.toArtistModel() },
                )))
            },
            onFailure = { Log.e("Api", "getArtistOverview failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /**
     * Loads the actual track list for an album. The UI navigates by album *name*
     * (the real Spotify id is lost during mapping), so we resolve the album via
     * search, then fetch its tracks. Uses GraphQL endpoints (not rate-limited).
     */
    suspend fun getAlbumSongs(albumName: String, artist: String = ""): Flow<Response<List<SongsModel>>> = flow {
        emit(Response.Loading())
        if (albumName.isBlank()) {
            emit(Response.Success(emptyList())); return@flow
        }
        if (!SpotifyTokenProvider.ensureToken(context)) {
            emit(Response.Error("Spotify not authenticated — set sp_dc cookie")); return@flow
        }
        // Two albums can share a name (different artists). Search the name and,
        // when we know the artist, pick the candidate whose artists match instead
        // of blindly taking the first (most-popular) result.
        val candidates = Spotify.search(
            if (artist.isBlank()) albumName else "$albumName $artist",
            types = listOf("album"),
            limit = 10,
        ).getOrNull()?.albums?.items.orEmpty()
        val albumId = pickAlbum(candidates, albumName, artist)?.id
        if (albumId.isNullOrBlank()) {
            emit(Response.Success(emptyList())); return@flow
        }
        Spotify.album(albumId).fold(
            onSuccess = { full -> emit(Response.Success(full.tracks?.items.orEmpty().map { it.toSongModel() })) },
            onFailure = { Log.e("Api", "getAlbumSongs failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /**
     * Loads the real track list for a playlist by its Spotify id (daily mixes,
     * Discover Weekly, personalized playlists, etc). Uses the fetchPlaylist GQL
     * endpoint directly — keyed by id, so it returns the *actual* playlist
     * content rather than a best-effort name search.
     */
    suspend fun getPlaylistSongs(playlistId: String): Flow<Response<List<SongsModel>>> = flow {
        emit(Response.Loading())
        if (playlistId.isBlank()) {
            emit(Response.Success(emptyList())); return@flow
        }
        if (!SpotifyTokenProvider.ensureToken(context)) {
            emit(Response.Error("Spotify not authenticated — set sp_dc cookie")); return@flow
        }
        Spotify.playlistTracks(playlistId, limit = 100).fold(
            onSuccess = { first ->
                val songs = first.items.mapNotNull { it.track?.toSongModel() }.toMutableList()
                // Show the first page right away, then keep paging until the
                // playlist's full track count is loaded.
                emit(Response.Success(songs.toList()))
                var offset = first.items.size
                while (offset < first.total && first.items.isNotEmpty()) {
                    val page = Spotify.playlistTracks(playlistId, limit = 100, offset = offset).getOrNull() ?: break
                    if (page.items.isEmpty()) break
                    songs += page.items.mapNotNull { it.track?.toSongModel() }
                    offset += page.items.size
                    emit(Response.Success(songs.toList()))
                }
            },
            onFailure = { Log.e("Api", "getPlaylistSongs failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /**
     * Follows a paged endpoint until every item is fetched, so libraries with
     * more than one page (50+ playlists/albums) load completely.
     */
    private suspend fun <T> fetchAllPages(
        fetch: suspend (offset: Int) -> kotlin.Result<com.metrolist.spotify.models.SpotifyPaging<T>>,
    ): List<T> {
        val first = fetch(0).getOrNull() ?: return emptyList()
        val items = first.items.toMutableList()
        var offset = first.items.size
        while (offset < first.total && first.items.isNotEmpty()) {
            val page = fetch(offset).getOrNull() ?: break
            if (page.items.isEmpty()) break
            items += page.items
            offset += page.items.size
        }
        return items
    }

    /**
     * "Your Library" — the user's actual saved Spotify albums plus their
     * playlists (followed + created), merged into one list. Uses the libraryV3
     * GQL endpoint (not rate-limited). Process-cached for instant re-entry.
     */
    suspend fun getLibrary(): Flow<Response<List<io.github.sekademi.spotufi.data.entity.LibraryEntry>>> = flow {
        HomeCache.library?.let { emit(Response.Success(it)) } ?: emit(Response.Loading())
        if (!SpotifyTokenProvider.ensureToken(context)) {
            if (HomeCache.library == null) {
                val downloaded = io.github.sekademi.spotufi.data.entity.LibraryEntry(
                    spotifyId = DOWNLOADS_ID,
                    name = "Downloaded",
                    subtitle = "Available offline",
                    coverUri = "",
                    isPlaylist = true,
                )
                emit(Response.Success(listOf(downloaded)))
            } else {
                emit(Response.Success(HomeCache.library!!))
            }
            return@flow
        }
        val albums = fetchAllPages { offset -> Spotify.myAlbums(limit = 50, offset = offset) }.map { a ->
            io.github.sekademi.spotufi.data.entity.LibraryEntry(
                spotifyId = a.id,
                name = a.name,
                subtitle = "Album • " + a.artists.joinToString(", ") { it.name },
                coverUri = a.images.firstOrNull()?.url ?: "",
                isPlaylist = false,
                artists = a.artists.joinToString(", ") { it.name },
            )
        }
        val playlists = fetchAllPages { offset -> Spotify.myPlaylists(limit = 50, offset = offset) }.map { p ->
            io.github.sekademi.spotufi.data.entity.LibraryEntry(
                spotifyId = p.id,
                name = p.name,
                subtitle = "Playlist" + (p.owner?.displayName?.let { " • $it" } ?: ""),
                coverUri = p.images.firstOrNull()?.url ?: "",
                isPlaylist = true,
            )
        }
        // Pin "Liked Songs" first, exactly like the Spotify app.
        val liked = io.github.sekademi.spotufi.data.entity.LibraryEntry(
            spotifyId = LIKED_SONGS_ID,
            name = "Liked Songs",
            subtitle = "Playlist • Liked songs",
            coverUri = "https://misc.scdn.co/liked-songs/liked-songs-640.png",
            isPlaylist = true,
        )
        // Pin a "Downloaded" shortcut to the offline tracks, like Spotify's library.
        val downloaded = io.github.sekademi.spotufi.data.entity.LibraryEntry(
            spotifyId = DOWNLOADS_ID,
            name = "Downloaded",
            subtitle = "Available offline",
            coverUri = "",
            isPlaylist = true,
        )
        val merged = listOf(liked, downloaded) + playlists + albums
        HomeCache.library = merged
        emit(Response.Success(merged))
    }

    /**
     * The looping Spotify Canvas video URL for a track (or null). Process-cached
     * per track id — including negative results — so the player only fetches once.
     */
    suspend fun getCanvasUrl(trackId: String): String? {
        if (trackId.isBlank()) return null
        CanvasCache.map[trackId]?.let { return it.value }
        if (!SpotifyTokenProvider.ensureToken(context)) return null
        val url = runCatching { Spotify.canvasUrl(trackId) }.getOrNull()
        CanvasCache.map[trackId] = CanvasCache.Entry(url)
        return url
    }

    private object CanvasCache {
        class Entry(val value: String?)
        val map = java.util.concurrent.ConcurrentHashMap<String, Entry>()
    }

    /** The user's Spotify "Liked Songs" (saved tracks) as playable songs. */
    suspend fun getLikedSongs(): Flow<Response<List<SongsModel>>> = flow {
        emit(Response.Loading())
        if (!SpotifyTokenProvider.ensureToken(context)) {
            emit(Response.Error("Spotify not authenticated")); return@flow
        }
        Spotify.likedSongs(limit = 50).fold(
            onSuccess = { first ->
                val models = first.items.map { it.track.toSongModel() }.toMutableList()
                // Seed the local like registry so hearts/menus show these as liked
                // and unliking them can be mirrored back to Spotify.
                models.forEach { io.github.sekademi.spotufi.data.preferences.addLikedSongId(context, it.id.toString()) }
                // First page immediately, then page through the whole library.
                emit(Response.Success(models.toList()))
                var offset = first.items.size
                while (offset < first.total && first.items.isNotEmpty()) {
                    val page = Spotify.likedSongs(limit = 50, offset = offset).getOrNull() ?: break
                    if (page.items.isEmpty()) break
                    val pageModels = page.items.map { it.track.toSongModel() }
                    pageModels.forEach { io.github.sekademi.spotufi.data.preferences.addLikedSongId(context, it.id.toString()) }
                    models += pageModels
                    offset += page.items.size
                    emit(Response.Success(models.toList()))
                }
            },
            onFailure = { Log.e("Api", "getLikedSongs failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /** The logged-in user's account (name, email, avatar, plan) for settings. */
    suspend fun getAccount(): Flow<Response<io.github.sekademi.spotufi.data.entity.AccountModel>> = flow {
        emit(Response.Loading())
        if (!SpotifyTokenProvider.ensureToken(context)) {
            emit(Response.Error("Spotify not authenticated")); return@flow
        }
        Spotify.me().fold(
            onSuccess = { u ->
                emit(Response.Success(io.github.sekademi.spotufi.data.entity.AccountModel(
                    name = u.displayName ?: u.id,
                    email = u.email ?: "",
                    imageUrl = u.images.firstOrNull()?.url ?: "",
                    plan = u.product?.replaceFirstChar { it.uppercase() } ?: "",
                )))
            },
            onFailure = { Log.e("Api", "getAccount failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /** Loads playlist metadata (name, cover, owner, track count) by id. */
    suspend fun getPlaylist(playlistId: String): Flow<Response<AlbumsModel>> = flow {
        emit(Response.Loading())
        if (playlistId.isBlank()) {
            emit(Response.Error("missing playlist id")); return@flow
        }
        if (!SpotifyTokenProvider.ensureToken(context)) {
            emit(Response.Error("Spotify not authenticated — set sp_dc cookie")); return@flow
        }
        Spotify.playlist(playlistId).fold(
            onSuccess = { p ->
                emit(Response.Success(AlbumsModel(
                    id = stableId("playlist:${p.id}"),
                    artists = p.owner?.displayName ?: "",
                    coverUri = p.images.firstOrNull()?.url ?: "",
                    name = p.name,
                    time = stripHtml(p.description),
                )))
            },
            onFailure = { Log.e("Api", "getPlaylist failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /**
     * Spotify playlist descriptions come as HTML (e.g. `<a href=spotify:...>Rickey
     * F</a>, …`). Render to plain text — keep the link labels, drop the tags —
     * and decode entities so the UI doesn't show raw markup.
     */
    private fun stripHtml(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return androidx.core.text.HtmlCompat
            .fromHtml(raw, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .trim()
    }

    /**
     * From a list of same/similar-named album candidates, prefer one whose name
     * matches exactly AND whose artist matches the requested one; fall back to a
     * name match, then an artist match, then the first result.
     */
    private fun pickAlbum(
        candidates: List<com.metrolist.spotify.models.SpotifyAlbum>,
        albumName: String,
        artist: String,
    ): com.metrolist.spotify.models.SpotifyAlbum? {
        if (candidates.isEmpty()) return null
        if (artist.isBlank()) {
            return candidates.firstOrNull { it.name.equals(albumName, ignoreCase = true) }
                ?: candidates.first()
        }
        val wantArtists = artist.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        fun artistMatches(a: com.metrolist.spotify.models.SpotifyAlbum): Boolean {
            val names = a.artists.joinToString(" ") { it.name }.lowercase()
            return wantArtists.any { it.isNotBlank() && names.contains(it) }
        }
        val nameMatches = candidates.filter { it.name.equals(albumName, ignoreCase = true) }
        return nameMatches.firstOrNull { artistMatches(it) }
            ?: candidates.firstOrNull { artistMatches(it) }
            ?: nameMatches.firstOrNull()
            ?: candidates.first()
    }
}
