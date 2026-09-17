package io.github.sekademi.spotufi.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sekademi.spotufi.data.api.Response
import io.github.sekademi.spotufi.data.entity.AlbumsModel
import io.github.sekademi.spotufi.data.entity.SongsModel
import io.github.sekademi.spotufi.data.playlist.LocalPlaylistManager
import io.github.sekademi.spotufi.di.CurrentSongState
import io.github.sekademi.spotufi.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: AppRepository,
    private val currentSongState: CurrentSongState,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val currentSongPlayingState: State<Boolean> get() = currentSongState.playingState
    val currentSongId: State<Int> get() = currentSongState.songId

    private val _songs: MutableStateFlow<Response<List<SongsModel>>> = MutableStateFlow(Response.Loading())
    val songs: StateFlow<Response<List<SongsModel>>> = _songs

    private val _playlist: MutableStateFlow<Response<AlbumsModel>> = MutableStateFlow(Response.Loading())
    val playlist: StateFlow<Response<AlbumsModel>> = _playlist

    val queue: State<List<SongsModel>> get() = currentSongState.queue

    fun updateQueue(songs: List<SongsModel>) = currentSongState.updateQueue(songs)

    fun addAllToQueue(songs: List<SongsModel>) = currentSongState.addAllToQueue(songs)

    fun startShuffled(songs: List<SongsModel>) = currentSongState.startShuffled(songs)

    /** Pause/resume global playback (the header play button stays visible while playing). */
    fun setPlaying(playing: Boolean) {
        if (playing) io.github.sekademi.spotufi.di.SongPlayer.play() else io.github.sekademi.spotufi.di.SongPlayer.pause()
        currentSongState.updatePlayingState(playing)
    }

    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId: Int, songIndex: Int = 0, album: String = "") {
        currentSongState.updateSongState(coverUri, title, singer, playingState, songId, songIndex, album)
    }

    val likeState = currentSongState.likeState

    fun updateLikeState(likeState: Boolean) {
        currentSongState.updateLikeState(likeState)
    }

    private var playlistKey: String? = null

    fun loadPlaylist(playlistId: String) {
        if (playlistKey == playlistId) return
        playlistKey = playlistId

        if (playlistId.startsWith("local_")) {
            val rawId = playlistId.removePrefix("local_")
            viewModelScope.launch(Dispatchers.IO) {
                val local = LocalPlaylistManager.getPlaylist(context, rawId)
                if (local != null) {
                    _playlist.value = Response.Success(
                        AlbumsModel(
                            id = rawId.hashCode() and 0x7fffffff,
                            artists = if (local.description.isNotBlank()) local.description else "Custom Local Playlist",
                            coverUri = local.coverUri,
                            name = local.title,
                            time = "${local.songs.size} tracks",
                        )
                    )
                    _songs.value = Response.Success(local.songs)
                } else {
                    _playlist.value = Response.Error("Local playlist not found")
                    _songs.value = Response.Error("Local playlist not found")
                }
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.providePlaylist(playlistId).collect { _playlist.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.providePlaylistSongs(playlistId).collect { response ->
                _songs.value = if (response is Response.Success) {
                    Response.Success(response.data.distinctBy { it.id })
                } else {
                    response
                }
            }
        }
    }

    fun deleteLocalPlaylist(rawPlaylistId: String) {
        LocalPlaylistManager.deletePlaylist(context, rawPlaylistId)
    }

    fun removeLocalSong(songId: Int, rawPlaylistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            LocalPlaylistManager.removeSongFromPlaylist(context, rawPlaylistId, songId)
            val local = LocalPlaylistManager.getPlaylist(context, rawPlaylistId)
            if (local != null) {
                _playlist.value = Response.Success(
                    AlbumsModel(
                        id = rawPlaylistId.hashCode() and 0x7fffffff,
                        artists = if (local.description.isNotBlank()) local.description else "Custom Local Playlist",
                        coverUri = local.coverUri,
                        name = local.title,
                        time = "${local.songs.size} tracks",
                    )
                )
                _songs.value = Response.Success(local.songs)
            }
        }
    }
}
