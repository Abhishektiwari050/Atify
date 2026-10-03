package io.github.sekademi.spotufi.ui.viewmodel

import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sekademi.spotufi.data.api.Response
import io.github.sekademi.spotufi.data.entity.SearchResults
import io.github.sekademi.spotufi.data.entity.SongsModel
import io.github.sekademi.spotufi.di.CurrentSongState
import io.github.sekademi.spotufi.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(private val repository: AppRepository, private val currentSongState: CurrentSongState) : ViewModel() {

    private val _songs : MutableStateFlow<Response<List<SongsModel>>> = MutableStateFlow(Response.Loading())
    val songs : StateFlow<Response<List<SongsModel>>> = _songs

    private val _results : MutableStateFlow<Response<SearchResults>> = MutableStateFlow(Response.Success(SearchResults()))
    val results : StateFlow<Response<SearchResults>> = _results

    val likeState = currentSongState.likeState

    val currentSongId: State<Int> get() = currentSongState.songId


    fun updateLikeState(likeState : Boolean){
        currentSongState.updateLikeState(likeState)
    }

    private var searchJob: Job? = null

    init {
        // Start empty — results come from live searches, not the rate-limited
        // personalized top-tracks feed (which returned 429 and blanked the screen).
        _songs.value = Response.Success(emptyList())
    }

    fun updateQueue(songs: List<SongsModel>) = currentSongState.updateQueue(songs)

    fun addAllToQueue(songs: List<SongsModel>) = currentSongState.addAllToQueue(songs)

    fun playSongFromList(song: SongsModel, list: List<SongsModel>, context: android.content.Context) {
        val effectiveList = if (list.isNotEmpty() && list.any { it.id == song.id }) list else listOf(song)
        val index = effectiveList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        currentSongState.updateQueue(effectiveList)
        updateSongState(
            song.coverUri,
            song.title,
            song.singer,
            true,
            song.id,
            index,
            song.album
        )
        io.github.sekademi.spotufi.di.SongPlayer.playSong(song.url, context)

        if (effectiveList.size <= 1) {
            startRadioFromSong(song)
        }
    }

    /**
     * Start playback of a single search result as a *radio*, the way Spotify does:
     * the queue becomes just this track, then recommended tracks (Spotify or YouTube)
     * are appended as they load.
     */
    fun startRadioFromSong(song: SongsModel) {
        currentSongState.updateQueue(listOf(song))
        val seed = song.spotifyTrackId
        if (seed.isBlank()) {
            val query = listOf(song.singer, song.title).filter { it.isNotBlank() }.joinToString(" ")
            if (query.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val ytRes = com.metrolist.innertube.YouTube.search(query, com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    val current = currentSongState.queue.value
                    if (current.size == 1 && current.first().id == song.id) {
                        val fresh = ytRes?.items.orEmpty().mapNotNull { item ->
                            when (item) {
                                is com.metrolist.innertube.models.SongItem -> {
                                    val artistName = item.artists.joinToString(", ") { it.name }.ifBlank { "YouTube" }
                                    val cleanTitle = io.github.sekademi.spotufi.di.StreamResolver.cleanSpotifySearchTitle(item.title)
                                    val id = ("yt:${item.id}").hashCode() and 0x7fffffff
                                    if (id == song.id) return@mapNotNull null
                                    SongsModel(
                                        id = id,
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
                        if (fresh.isNotEmpty()) currentSongState.updateQueue(current + fresh)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SearchViewModel", "YouTube radio lookup failed: ${e.message}")
                }
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val recs = repository.provideRecommendations(listOf(seed))
            val current = currentSongState.queue.value
            // Only extend if this radio is still the active queue (user didn't tap away).
            if (current.size == 1 && current.first().id == song.id) {
                val fresh = recs.filter { it.id != song.id }
                if (fresh.isNotEmpty()) currentSongState.updateQueue(current + fresh)
            }
        }
    }

    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId : Int, songIndex : Int = 0, album : String = "") {
        currentSongState.updateSongState(coverUri, title, singer, playingState, songId, songIndex, album)
    }

    fun search(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _results.value = Response.Success(SearchResults())
            _songs.value = Response.Success(emptyList())
            return
        }
        _results.value = Response.Loading()
        _songs.value = Response.Loading()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300) // 300ms debounce to avoid spamming API on fast typing
            repository.searchEverything(trimmed).collect { result ->
                when (result) {
                    is Response.Success -> {
                        val rerankedSongs = io.github.sekademi.spotufi.data.recommendation.SmartMusicRanker.rerankSearchResults(
                            io.github.sekademi.spotufi.MyApplication.instance,
                            result.data.songs,
                            trimmed,
                        )
                        val rerankedResults = result.data.copy(songs = rerankedSongs)
                        _results.value = Response.Success(rerankedResults)
                        _songs.value = Response.Success(rerankedSongs)
                    }
                    is Response.Error -> {
                        _results.value = result
                        _songs.value = Response.Error(result.error)
                    }
                    is Response.Loading -> {
                        _results.value = result
                        _songs.value = Response.Loading()
                    }
                }
            }
        }
    }
}