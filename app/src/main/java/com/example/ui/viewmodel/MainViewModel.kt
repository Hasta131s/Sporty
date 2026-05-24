package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.*
import com.example.data.network.MusicApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    // Room Database Observables
    val currentUser = db.userDao().getUserFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val favorites = db.favoriteDao().getAllFavoritesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val downloads = db.downloadDao().getAllDownloadsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = db.playlistDao().getAllPlaylistsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val banners = db.bannerDao().getBannersFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val searchHistory = db.searchHistoryDao().getSearchHistoryFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Player States
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _playbackPosition = MutableStateFlow(0)
    val playbackPosition: StateFlow<Int> = _playbackPosition

    private val _playbackDuration = MutableStateFlow(0)
    val playbackDuration: StateFlow<Int> = _playbackDuration

    private val _isAudioLoading = MutableStateFlow(false)
    val isAudioLoading: StateFlow<Boolean> = _isAudioLoading

    private val _songQueue = MutableStateFlow<List<Song>>(emptyList())
    val songQueue: StateFlow<List<Song>> = _songQueue

    private val _currentQueueIndex = MutableStateFlow(-1)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex

    private val _activeLyrics = MutableStateFlow<String?>(null)
    val activeLyrics: StateFlow<String?> = _activeLyrics

    // Visual features
    val backgroundYoutubeEmbedUrl = MutableStateFlow<String?>(null)

    // Search and Feed States
    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _bannerEditorActive = MutableStateFlow(false)
    val bannerEditorActive: StateFlow<Boolean> = _bannerEditorActive

    // Admin Access settings
    val isAdmin = currentUser.map { it?.email?.contains("admin") == true || it?.username?.lowercase() == "admin" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        // Pre-populate some creative music banners if the database has none
        viewModelScope.launch {
            db.bannerDao().getBannersFlow().first().let { currentBanners ->
                if (currentBanners.isEmpty()) {
                    db.bannerDao().insertBanner(Banner(imageUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800", title = "Cosmic Beats - Flofys Original", actionUrl = ""))
                    db.bannerDao().insertBanner(Banner(imageUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800", title = "Flofys Premium - Free Offline Trial Active", actionUrl = ""))
                }
            }
        }
    }

    // AUTH ACTIONS
    fun loginOrRegister(username: String, email: String) {
        viewModelScope.launch {
            val existing = db.userDao().getUser()
            if (existing != null) {
                db.userDao().updateUser(existing.copy(username = username, email = email))
            } else {
                db.userDao().insertUser(User(username = username, email = email, isPremium = true))
            }
        }
    }

    fun upgradeProfileToPremium() {
        viewModelScope.launch {
            db.userDao().getUser()?.let {
                db.userDao().updateUser(it.copy(isPremium = true))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            db.userDao().clearUser()
            stopAudio()
            _currentSong.value = null
            _songQueue.value = emptyList()
            _searchResults.value = emptyList()
        }
    }

    // SEARCH ACTIONS
    fun executeSearch(query: String, searchMode: String = "All") {
        if (query.trim().isEmpty()) return
        
        viewModelScope.launch {
            _isSearching.value = true
            db.searchHistoryDao().insertSearch(SearchHistory(query = query))
            
            withContext(Dispatchers.IO) {
                try {
                    val saavnSongs = if (searchMode == "All" || searchMode == "Saavn") {
                        MusicApiService.searchListenFree(query)
                    } else emptyList()

                    val ytSongs = if (searchMode == "All" || searchMode == "YouTube") {
                        MusicApiService.searchYouTube(query)
                    } else emptyList()

                    val combined = (saavnSongs + ytSongs).distinctBy { it.id }
                    _searchResults.value = combined
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Search failed", e)
                } finally {
                    _isSearching.value = false
                }
            }
        }
    }

    // PLAYBACK ACTIONS
    fun playSong(song: Song, queue: List<Song> = emptyList()) {
        viewModelScope.launch {
            stopAudio()
            _currentSong.value = song
            _isAudioLoading.value = true

            if (queue.isNotEmpty()) {
                _songQueue.value = queue
                _currentQueueIndex.value = queue.indexOfFirst { it.id == song.id }
            } else {
                _songQueue.value = listOf(song)
                _currentQueueIndex.value = 0
            }

            // Sync Youtube embeds if chosen
            if (song.source == "youtube") {
                backgroundYoutubeEmbedUrl.value = "https://www.youtube.com/embed/${song.id}"
            } else {
                backgroundYoutubeEmbedUrl.value = null
            }

            // Load Lyrics caching
            loadSongLyrics(song)

            withContext(Dispatchers.IO) {
                // Check if this song was already downloaded locally
                val downloadedInfo = db.downloadDao().getDownload(song.id)
                val dataSource = if (downloadedInfo != null && File(downloadedInfo.localFilePath).exists()) {
                    Log.d("MainViewModel", "Playing from offline local file: ${downloadedInfo.localFilePath}")
                    downloadedInfo.localFilePath
                } else {
                    Log.d("MainViewModel", "Fetching streaming link for ${song.title}")
                    MusicApiService.getDownloadLink(song.id, song.source)
                }

                if (dataSource != null) {
                    withContext(Dispatchers.Main) {
                        try {
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(dataSource)
                                prepareAsync()
                                setOnPreparedListener {
                                    _isAudioLoading.value = false
                                    start()
                                    _isPlaying.value = true
                                    _playbackDuration.value = duration
                                    startProgressTracking()
                                }
                                setOnCompletionListener {
                                    nextTrack()
                                }
                                setOnErrorListener { _, _, _ ->
                                    _isAudioLoading.value = false
                                    Log.e("MainViewModel", "MediaPlayer playback source error")
                                    nextTrack()
                                    true
                                }
                            }
                        } catch (e: Exception) {
                            _isAudioLoading.value = false
                            Log.e("MainViewModel", "MediaPlayer prep failed", e)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _isAudioLoading.value = false
                        Log.e("MainViewModel", "No playable data stream available")
                    }
                }
            }
        }
    }

    fun togglePlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                progressJob?.cancel()
            } else {
                player.start()
                _isPlaying.value = true
                startProgressTracking()
            }
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let { player ->
            try {
                player.seekTo(positionMs)
                _playbackPosition.value = positionMs
            } catch (e: Exception) {
                Log.e("MainViewModel", "Seeking error", e)
            }
        }
    }

    fun previousTrack() {
        val q = _songQueue.value
        val idx = _currentQueueIndex.value
        if (q.isNotEmpty() && idx > 0) {
            playSong(q[idx - 1], q)
        }
    }

    fun nextTrack() {
        val q = _songQueue.value
        val idx = _currentQueueIndex.value
        if (q.isNotEmpty() && idx < q.size - 1) {
            playSong(q[idx + 1], q)
        } else {
            stopAudio()
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _playbackPosition.value = player.currentPosition
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopAudio() {
        progressJob?.cancel()
        _isPlaying.value = false
        _playbackPosition.value = 0
        mediaPlayer?.run {
            try {
                if (isPlaying) stop()
                release()
            } catch (e: Exception) {
                Log.e("MainViewModel", "MediaPlayer release failed", e)
            }
        }
        mediaPlayer = null
    }

    // LYRICS LOADER
    private fun loadSongLyrics(song: Song) {
        viewModelScope.launch {
            val cache = db.lyricsCacheDao().getLyrics(song.id)
            if (cache != null) {
                _activeLyrics.value = cache.lyrics
            } else {
                // Generate simulated gorgeous aesthetic lyrics flow
                val defaultLyrics = """
                    [00:12.00] In the depths of Cosmic Slates
                    [00:17.50] Moving with the rhythm of our own heartbeats
                    [00:24.00] Flofys takes you higher, past the sky
                    [00:30.20] Pure acoustic energy that never asks why
                    [00:37.00] Floating online, stored on local cache
                    [00:43.10] Beautiful design on Jetpack, in a flash
                    [00:50.00] Oh we are streaming free, living premium
                    [00:58.20] No limitations, our soul's equilibrium...
                """.trimIndent()
                db.lyricsCacheDao().insertLyrics(LyricsCache(song.id, defaultLyrics))
                _activeLyrics.value = defaultLyrics
            }
        }
    }

    // LIKE / FAVORITE SYSTEM
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val isFav = db.favoriteDao().isFavorite(song.id)
            if (isFav) {
                db.favoriteDao().deleteFavorite(song.id)
            } else {
                db.favoriteDao().insertFavorite(Favorite(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    thumbnail = song.thumbnail,
                    duration = song.duration,
                    source = song.source
                ))
            }
        }
    }

    fun isFavoriteFlow(songId: String): Flow<Boolean> {
        return db.favoriteDao().isFavoriteFlow(songId)
    }

    // DOWNLOAD MANAGER
    fun downloadTrack(song: Song) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val streamUrl = MusicApiService.getDownloadLink(song.id, song.source)
                    if (streamUrl != null) {
                        val localPath = MusicApiService.downloadTrackFile(getApplication(), song, streamUrl)
                        if (localPath != null) {
                            db.downloadDao().insertDownload(Download(
                                id = song.id,
                                title = song.title,
                                artist = song.artist,
                                thumbnail = song.thumbnail,
                                duration = song.duration,
                                source = song.source,
                                localFilePath = localPath
                            ))
                            Log.d("MainViewModel", "Download successful for ${song.title}: $localPath")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Download failed for ${song.title}", e)
                }
            }
        }
    }

    fun isDownloadedFlow(songId: String): Flow<Boolean> {
        return db.downloadDao().isDownloadedFlow(songId)
    }

    fun deleteDownloadedTrack(songId: String) {
        viewModelScope.launch {
            val downloadInfo = db.downloadDao().getDownload(songId)
            if (downloadInfo != null) {
                val file = File(downloadInfo.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
                db.downloadDao().deleteDownload(songId)
            }
        }
    }

    // PLAYLIST MANAGER
    fun createPlaylist(name: String) {
        if (name.trim().isEmpty()) return
        viewModelScope.launch {
            db.playlistDao().insertPlaylist(Playlist(name = name))
        }
    }

    fun addSongToPlaylist(playlist: Playlist, songId: String) {
        viewModelScope.launch {
            val currentArray = try {
                val json = JSONArray(playlist.songIdsJson)
                val list = mutableListOf<String>()
                for (i in 0 until json.length()) {
                    list.add(json.getString(i))
                }
                list
            } catch (e: Exception) {
                mutableListOf()
            }
            if (!currentArray.contains(songId)) {
                currentArray.add(songId)
                val updatedJson = JSONArray(currentArray).toString()
                db.playlistDao().updatePlaylist(playlist.copy(songIdsJson = updatedJson))
            }
        }
    }

    fun removeSongFromPlaylist(playlist: Playlist, songId: String) {
        viewModelScope.launch {
            val currentArray = try {
                val json = JSONArray(playlist.songIdsJson)
                val list = mutableListOf<String>()
                for (i in 0 until json.length()) {
                    list.add(json.getString(i))
                }
                list
            } catch (e: Exception) {
                mutableListOf()
            }
            if (currentArray.contains(songId)) {
                currentArray.remove(songId)
                val updatedJson = JSONArray(currentArray).toString()
                db.playlistDao().updatePlaylist(playlist.copy(songIdsJson = updatedJson))
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            db.playlistDao().deletePlaylist(playlist.id)
        }
    }

    // ADMIN BANNER ACTIONS
    fun addBanner(title: String, imageUrl: String, actionUrl: String = "") {
        viewModelScope.launch {
            db.bannerDao().insertBanner(Banner(title = title, imageUrl = imageUrl, actionUrl = actionUrl))
        }
    }

    fun removeBanner(bannerId: Int) {
        viewModelScope.launch {
            db.bannerDao().deleteBanner(bannerId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}
