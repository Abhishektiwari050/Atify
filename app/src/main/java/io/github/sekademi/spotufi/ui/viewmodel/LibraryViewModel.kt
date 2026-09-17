package io.github.sekademi.spotufi.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sekademi.spotufi.data.api.Response
import io.github.sekademi.spotufi.data.entity.AccountModel
import io.github.sekademi.spotufi.data.entity.LibraryEntry
import io.github.sekademi.spotufi.data.playlist.LocalPlaylist
import io.github.sekademi.spotufi.data.playlist.LocalPlaylistManager
import io.github.sekademi.spotufi.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: AppRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _entries: MutableStateFlow<Response<List<LibraryEntry>>> = MutableStateFlow(Response.Loading())
    val entries: StateFlow<Response<List<LibraryEntry>>> = _entries

    private val _account: MutableStateFlow<Response<AccountModel>> = MutableStateFlow(Response.Loading())
    val account: StateFlow<Response<AccountModel>> = _account

    private val _followedArtists: MutableStateFlow<List<io.github.sekademi.spotufi.data.entity.ArtistsModel>> =
        MutableStateFlow(emptyList())
    val followedArtists: StateFlow<List<io.github.sekademi.spotufi.data.entity.ArtistsModel>> = _followedArtists

    private var rawLibraryEntries: List<LibraryEntry> = emptyList()

    init {
        load()
        loadAccount()
        loadFollowedArtists()
    }

    private fun getLocalEntries(): List<LibraryEntry> {
        return LocalPlaylistManager.getPlaylists(context).map { local ->
            LibraryEntry(
                spotifyId = "local_${local.id}",
                name = local.title,
                subtitle = "Playlist • ${local.songs.size} ${if (local.songs.size == 1) "track" else "tracks"}",
                coverUri = local.coverUri,
                isPlaylist = true,
                artists = ""
            )
        }
    }

    private fun mergeEntries(baseEntries: List<LibraryEntry>): List<LibraryEntry> {
        val local = getLocalEntries()
        if (local.isEmpty()) return baseEntries

        val likedSongsId = io.github.sekademi.spotufi.data.api.Api.HomeCache.LIKED_SONGS_ID
        val downloadsId = io.github.sekademi.spotufi.data.api.Api.HomeCache.DOWNLOADS_ID

        val pinned = baseEntries.filter { it.spotifyId == likedSongsId || it.spotifyId == downloadsId }
        val rest = baseEntries.filterNot { it.spotifyId == likedSongsId || it.spotifyId == downloadsId }

        return pinned + local + rest
    }

    fun load() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideLibrary().collect { resp ->
            if (resp is Response.Success) {
                rawLibraryEntries = resp.data
                _entries.value = Response.Success(mergeEntries(rawLibraryEntries))
            } else {
                val local = getLocalEntries()
                if (local.isNotEmpty()) {
                    _entries.value = Response.Success(local)
                } else {
                    _entries.value = resp
                }
            }
        }
    }

    fun refreshLocalPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            if (rawLibraryEntries.isNotEmpty()) {
                _entries.value = Response.Success(mergeEntries(rawLibraryEntries))
            } else {
                val local = getLocalEntries()
                if (local.isNotEmpty()) {
                    _entries.value = Response.Success(local)
                }
            }
        }
    }

    fun createLocalPlaylist(title: String, description: String = ""): LocalPlaylist {
        val pl = LocalPlaylistManager.createPlaylist(context, title, description)
        refreshLocalPlaylists()
        return pl
    }

    fun importM3u8(uri: Uri): LocalPlaylist? {
        return try {
            val pl = context.contentResolver.openInputStream(uri)?.use { stream ->
                LocalPlaylistManager.importFromM3u8(context, stream)
            }
            if (pl != null) {
                refreshLocalPlaylists()
            }
            pl
        } catch (e: Exception) {
            null
        }
    }

    private fun loadFollowedArtists() = viewModelScope.launch(Dispatchers.IO) {
        _followedArtists.value = repository.provideFollowedArtists()
    }

    private fun loadAccount() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideAccount().collect { _account.value = it }
    }
}
