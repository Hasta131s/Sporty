package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.*
import com.example.data.network.GeminiClient
import com.example.data.network.MusicApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.File
import java.net.URLEncoder
import java.util.UUID

sealed interface Screen {
    object Login : Screen
    object Register : Screen
    object Dashboard : Screen
    object Admin : Screen
}

enum class DashboardTab {
    HOME, SEARCH, LIBRARY, DOWNLOADS, LYRICS, HISTORY, PROFILE_CUSTOMIZE
}

enum class AppColorTheme(val displayName: String) {
    SPOTIFY_GREEN("Spotify Green"),
    COSMIC_INDIGO("Cosmic Indigo"),
    CYBERPUNK_AMBER("Cyberpunk Amber"),
    NEON_PINK("Neon Pink"),
    CRIMSON_RED("Crimson Red")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val songListType = Types.newParameterizedType(List::class.java, Song::class.java)
    private val songAdapter = moshi.adapter<List<Song>>(songListType)
    private val singleSongAdapter = moshi.adapter(Song::class.java)

    // Current Screen and User states
    var currentScreen by mutableStateOf<Screen>(Screen.Login)
        private set

    fun navigateTo(screen: Screen) {
        currentScreen = screen
    }

    var currentTab by mutableStateOf(DashboardTab.HOME)
    var currentUser by mutableStateOf<User?>(null)
        private set
    var isAdminLoggedIn by mutableStateOf(false)
        private set

    // Current Personalization configuration
    var activeTheme by mutableStateOf(AppColorTheme.SPOTIFY_GREEN)

    // DB lists reflecting room collections
    val usersList = MutableStateFlow<List<User>>(emptyList())
    val playlistsList = MutableStateFlow<List<Playlist>>(emptyList())
    val favoritesList = MutableStateFlow<List<Favorite>>(emptyList())
    val downloadsList = MutableStateFlow<List<Download>>(emptyList())
    val searchHistoryList = MutableStateFlow<List<SearchHistory>>(emptyList())
    val bannersList = MutableStateFlow<List<Banner>>(emptyList())
    val siteSettingsState = MutableStateFlow<SiteSettings?>(null)

    // Search and Suggestion State
    var searchQuery by mutableStateOf("")
    var searchSuggestions by mutableStateOf<List<String>>(emptyList())
    var searchResults by mutableStateOf<List<Song>>(emptyList())
    var isSearching by mutableStateOf(false)
    private var searchDebounceJob: Job? = null

    // Lyrics Search State
    var lyricsQuery by mutableStateOf("")
    var lyricsResultTitle by mutableStateOf("")
    var lyricsResultArt by mutableStateOf("")
    var lyricsResultText by mutableStateOf("")
    var lyricsResultBio by mutableStateOf("")
    var isLyricsLoading by mutableStateOf(false)

    // Music Playback engine state
    val backgroundYoutubeEmbedUrl = MutableStateFlow<String?>(null)
    private var mediaPlayer: MediaPlayer? = null
    var currentTrack by mutableStateOf<Song?>(null)
    var currentQueue by mutableStateOf<List<Song>>(emptyList())
    var queueIndex by mutableStateOf(0)
    var isTrackPlaying by mutableStateOf(false)
    var isTrackBuffering by mutableStateOf(false)
    var trackCurrentPosition by mutableStateOf(0f) // 0.0 to 1.0 progress pct
    var trackCurrentPositionText by mutableStateOf("0:00")
    var trackDurationText by mutableStateOf("0:00")
    var isRepeatEnabled by mutableStateOf(false)

    // General download progress tracking
    var activeDownloadProgress by mutableStateOf<Float?>(null) // null when no download
    var activeDownloadName by mutableStateOf("")

    private var progressTrackerJob: Job? = null
    private var playerInitJob: Job? = null
    private val CHANNEL_ID = "music_channel"
    private var consecutiveLoadFailures = 0

    private val mediaControlReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "ACTION_PLAY_PAUSE" -> togglePlay()
                "ACTION_NEXT" -> next()
                "ACTION_PREV" -> prev()
            }
        }
    }

    private fun registerReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction("ACTION_PLAY_PAUSE")
            addAction("ACTION_NEXT")
            addAction("ACTION_PREV")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(mediaControlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            getApplication<Application>().registerReceiver(mediaControlReceiver, filter)
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Music Playback"
            val descriptionText = "Shows current playing track"
            val importance = android.app.NotificationManager.IMPORTANCE_LOW
            val channel = android.app.NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: android.app.NotificationManager =
                getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(song: Song, isPlaying: Boolean) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Pre-fetch cover bitmap if possible
                var bitmap: android.graphics.Bitmap? = null
                try {
                    val loader = coil.ImageLoader(context)
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(song.thumbnail)
                        .allowHardware(false) // Disable hardware bitmaps so NotificationManager can read it
                        .build()
                    val result = loader.execute(request)
                    if (result is coil.request.SuccessResult) {
                        bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    }
                } catch (e: Exception) {}

                withContext(Dispatchers.Main) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    
                    val playPauseIntent = android.app.PendingIntent.getBroadcast(
                        context, 0, Intent("ACTION_PLAY_PAUSE").setPackage(context.packageName), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    val nextIntent = android.app.PendingIntent.getBroadcast(
                        context, 1, Intent("ACTION_NEXT").setPackage(context.packageName), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    val prevIntent = android.app.PendingIntent.getBroadcast(
                        context, 2, Intent("ACTION_PREV").setPackage(context.packageName), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )

                    @Suppress("DEPRECATION")
                    val builder = android.app.Notification.Builder(context, CHANNEL_ID)
                        .setSmallIcon(com.example.R.drawable.app_logo)
                        .setContentTitle(song.title)
                        .setContentText(song.artist)
                        .setLargeIcon(bitmap)
                        .addAction(android.app.Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource("android", android.R.drawable.ic_media_previous), "Previous", prevIntent).build())
                        .addAction(android.app.Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource("android", if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play), "Play/Pause", playPauseIntent).build())
                        .addAction(android.app.Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource("android", android.R.drawable.ic_media_next), "Next", nextIntent).build())
                        .setStyle(android.app.Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2))
                        .setOngoing(isPlaying)

                    notificationManager.notify(1, builder.build())
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Notification error", e)
            }
        }
    }

    init {
        // Clear media player, load configs
        createNotificationChannel()
        registerReceiver()
        setupSiteSettings()
        loadAllBanners()
        checkSavedSession()

        // Start helper PlaybackService for task removal cleanup
        try {
            val context = getApplication<Application>()
            context.startService(Intent(context, com.example.PlaybackService::class.java))
        } catch (e: Exception) {}
    }

    private fun checkSavedSession() {
        val prefs = getApplication<Application>().getSharedPreferences("streamhub_prefs", Context.MODE_PRIVATE)
        val savedUserId = prefs.getString("logged_in_user_id", null)
        val themePref = prefs.getString("selected_app_theme", null)
        if (themePref != null) {
            try {
                activeTheme = AppColorTheme.valueOf(themePref)
            } catch (e: Exception) {}
        }
        if (savedUserId != null) {
            viewModelScope.launch {
                val user = db.userDao().getUserById(savedUserId)
                if (user != null && !user.isBanned) {
                    currentUser = user
                    if (user.username == "admin" || user.isAdmin) {
                        isAdminLoggedIn = true
                    }
                    currentScreen = Screen.Dashboard
                    loadUserDataFlows(user.id)
                    
                    // Recover last played song
                    val savedSongJson = prefs.getString("last_played_song", null)
                    if (savedSongJson != null) {
                        try {
                            val lastSong = singleSongAdapter.fromJson(savedSongJson)
                            if (lastSong != null) {
                                currentTrack = lastSong
                                currentQueue = listOf(lastSong)
                                queueIndex = 0
                            }
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Failed to restore last song", e)
                        }
                    }
                } else {
                    prefs.edit().remove("logged_in_user_id").apply()
                }
            }
        }
    }

    private fun setupSiteSettings() {
        viewModelScope.launch {
            val settings = db.siteSettingsDao().getSettings()
            if (settings == null) {
                val defaultSettings = SiteSettings(
                    siteName = "Flofys",
                    logoUrl = "https://ui-avatars.com/api/?name=Flofys&background=1DB954&color=fff",
                    adminPasswordHash = "admin123" // default base
                )
                db.siteSettingsDao().insertSettings(defaultSettings)
                siteSettingsState.value = defaultSettings
            } else {
                siteSettingsState.value = settings
            }
        }
    }

    private fun loadUserDataFlows(userId: String) {
        viewModelScope.launch {
            db.playlistDao().getPlaylistsByUserId(userId).collect {
                playlistsList.value = it
            }
        }
        viewModelScope.launch {
            db.favoriteDao().getFavoritesByUserId(userId).collect {
                favoritesList.value = it
            }
        }
        viewModelScope.launch {
            db.downloadDao().getDownloadsByUserId(userId).collect {
                downloadsList.value = it
            }
        }
        viewModelScope.launch {
            db.searchHistoryDao().getSearchHistoryByUserId(userId).collect {
                searchHistoryList.value = it
            }
        }
        viewModelScope.launch {
            db.userDao().getAllUsers().collect {
                usersList.value = it
            }
        }
    }

    private fun loadAllBanners() {
        viewModelScope.launch {
            db.bannerDao().getAllBanners().collect {
                bannersList.value = it
            }
        }
    }

    // --- User login, register, sign out actions ---
    fun login(username: String, passwordRaw: String, onResult: (Boolean, String) -> Unit) {
        if (username.isEmpty() || passwordRaw.isEmpty()) {
            onResult(false, "Lütfen tüm alanları doldurun.")
            return
        }
        viewModelScope.launch {
            val user = db.userDao().getUserByUsername(username)
            if (user != null) {
                if (user.isBanned) {
                    onResult(false, "Hesabınız yasaklanmıştır.")
                    return@launch
                }
                // Check simple password matcher
                if (user.passwordHash == passwordRaw) {
                    currentUser = user
                    if (user.username == "admin" || user.isAdmin) {
                        isAdminLoggedIn = true
                    }
                    val prefs = getApplication<Application>().getSharedPreferences("streamhub_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("logged_in_user_id", user.id).apply()

                    currentScreen = Screen.Dashboard
                    loadUserDataFlows(user.id)
                    onResult(true, "Başarıyla giriş yapıldı!")
                } else {
                    onResult(false, "Şifre hatalı.")
                }
            } else {
                onResult(false, "Kullanıcı bulunamadı.")
            }
        }
    }

    fun register(username: String, passwordRaw: String, onResult: (Boolean, String) -> Unit) {
        if (username.length < 3 || passwordRaw.length < 4) {
            onResult(false, "Kullanıcı adı min 3, şifre min 4 karakter olmalıdır.")
            return
        }
        viewModelScope.launch {
            val existing = db.userDao().getUserByUsername(username)
            if (existing != null) {
                onResult(false, "Bu kullanıcı adı zaten alınmış.")
                return@launch
            }

            val userId = "user_" + UUID.randomUUID().toString().substring(0, 8)
            val newUser = User(
                id = userId,
                username = username,
                passwordHash = passwordRaw,
                avatarUrl = "https://ui-avatars.com/api/?name=${URLEncoder.encode(username)}&background=1DB954&color=fff",
                createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                isBanned = false,
                isAdmin = (username.lowercase() == "admin")
            )
            db.userDao().insertUser(newUser)
            currentUser = newUser
            if (newUser.isAdmin) isAdminLoggedIn = true

            val prefs = getApplication<Application>().getSharedPreferences("streamhub_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("logged_in_user_id", newUser.id).apply()

            currentScreen = Screen.Dashboard
            loadUserDataFlows(userId)
            onResult(true, "Kayıt başarıyla tamamlandı!")
        }
    }

    fun signOut() {
        viewModelScope.launch {
            stopPlayback()
            currentUser = null
            isAdminLoggedIn = false
            currentScreen = Screen.Login
            val prefs = getApplication<Application>().getSharedPreferences("streamhub_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("logged_in_user_id").apply()
        }
    }

    // --- Profile customization ---
    fun updateProfile(newUsername: String, customAvatarUrl: String, onResult: (Boolean, String) -> Unit) {
        val user = currentUser ?: return
        if (newUsername.length < 3) {
            onResult(false, "Kullanıcı adı en az 3 karakter olmalıdır.")
            return
        }
        viewModelScope.launch {
            // check if name taken
            val check = db.userDao().getUserByUsername(newUsername)
            if (check != null && check.id != user.id) {
                onResult(false, "Bu kullanıcı adı başka biri tarafından kullanılıyor.")
                return@launch
            }

            val updatedUser = user.copy(
                username = newUsername,
                avatarUrl = if (customAvatarUrl.isNotEmpty()) customAvatarUrl else user.avatarUrl
            )
            db.userDao().insertUser(updatedUser)
            currentUser = updatedUser
            onResult(true, "Profil başarıyla güncellendi!")
        }
    }

    fun changeAppTheme(theme: AppColorTheme) {
        activeTheme = theme
        val prefs = getApplication<Application>().getSharedPreferences("streamhub_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_app_theme", theme.name).apply()
    }

    // --- Administrative functionality ---
    fun toggleBanUser(userId: String) {
        if (!isAdminLoggedIn) return
        viewModelScope.launch {
            val user = db.userDao().getUserById(userId) ?: return@launch
            val newBanStatus = !user.isBanned
            db.userDao().updateBannedStatus(userId, newBanStatus)
        }
    }

    fun addBanner(title: String, imageUrl: String, link: String) {
        if (!isAdminLoggedIn) return
        viewModelScope.launch {
            val banner = Banner(
                id = "banner_" + UUID.randomUUID().toString().substring(0, 6),
                title = title,
                imageUrl = imageUrl,
                link = link,
                active = true,
                createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            )
            db.bannerDao().insertBanner(banner)
        }
    }

    fun toggleBanner(bannerId: String, active: Boolean) {
        if (!isAdminLoggedIn) return
        viewModelScope.launch {
            db.bannerDao().updateBannerActive(bannerId, active)
        }
    }

    fun deleteBanner(bannerId: String) {
        if (!isAdminLoggedIn) return
        viewModelScope.launch {
            db.bannerDao().deleteBannerById(bannerId)
        }
    }

    fun updateSiteSettings(siteName: String, logoUrl: String, adminPass: String) {
        if (!isAdminLoggedIn) return
        viewModelScope.launch {
            val current = db.siteSettingsDao().getSettings() ?: return@launch
            val updated = current.copy(
                siteName = siteName,
                logoUrl = logoUrl,
                adminPasswordHash = if (adminPass.isNotEmpty()) adminPass else current.adminPasswordHash
            )
            db.siteSettingsDao().insertSettings(updated)
            siteSettingsState.value = updated
        }
    }

    // --- Search logic matching PHP triggers ---
    fun onSearchQueryChanged(newQuery: String) {
        searchQuery = newQuery
        if (newQuery.length < 2) {
            searchSuggestions = emptyList()
            searchResults = emptyList()
            return
        }
        
        // 1. Get suggestions instantly
        viewModelScope.launch(Dispatchers.IO) {
            val suggestions = MusicApiService.getSuggestions(newQuery)
            withContext(Dispatchers.Main) {
                // Keep suggestions updated
                if (searchQuery == newQuery) {
                    searchSuggestions = suggestions
                }
            }
        }

        // 2. Debounce instant search trigger
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            kotlinx.coroutines.delay(750) // Wait 750ms after user stops typing
            if (searchQuery == newQuery) {
                performSearch(newQuery)
            }
        }
    }

    fun performSearch(query: String) {
        if (query.isBlank()) return
        searchQuery = query
        searchSuggestions = emptyList()
        isSearching = true
        searchResults = emptyList()

        // Log search query hist
        recordSearchHistory(query)

        viewModelScope.launch(Dispatchers.IO) {
            val saavnResults = MusicApiService.searchListenFree(query)
            val ytResults = MusicApiService.searchYouTube(query)
            
            val combinedResults = mutableListOf<Song>()
            combinedResults.addAll(saavnResults)
            for (ytSong in ytResults) {
                if (saavnResults.none { it.title.equals(ytSong.title, ignoreCase = true) && it.artist.equals(ytSong.artist, ignoreCase = true) }) {
                    combinedResults.add(ytSong)
                }
            }
            
            withContext(Dispatchers.Main) {
                searchResults = combinedResults
                isSearching = false
            }
        }
    }

    private fun recordSearchHistory(query: String) {
        val user = currentUser ?: return
        viewModelScope.launch {
            val entry = SearchHistory(
                id = "search_" + UUID.randomUUID().toString(),
                userId = user.id,
                query = query,
                timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            )
            db.searchHistoryDao().insertSearchHistory(entry)
        }
    }

    fun clearSearchHistory() {
        val user = currentUser ?: return
        viewModelScope.launch {
            db.searchHistoryDao().clearHistoryByUserId(user.id)
        }
    }

    // --- Playlists creation and modification ---
    fun createPlaylist(name: String) {
        val user = currentUser ?: return
        viewModelScope.launch {
            val playlistId = "pl_" + UUID.randomUUID().toString().substring(0, 6)
            val textDate = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            val playlist = Playlist(
                id = playlistId,
                userId = user.id,
                name = name,
                songsJson = songAdapter.toJson(emptyList()),
                createdAt = textDate,
                coverUrl = "https://ui-avatars.com/api/?name=${URLEncoder.encode(name)}&background=333&color=fff"
            )
            db.playlistDao().insertPlaylist(playlist)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            db.playlistDao().deletePlaylistById(playlistId)
        }
    }

    fun addSongToPlaylist(playlistId: String, song: Song, onComplete: () -> Unit) {
        viewModelScope.launch {
            val playlist = db.playlistDao().getPlaylistById(playlistId) ?: return@launch
            val currentSongs: List<Song> = try {
                songAdapter.fromJson(playlist.songsJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            if (!currentSongs.any { it.id == song.id }) {
                val updatedSongs = currentSongs + song
                val updatedPlaylist = playlist.copy(
                    songsJson = songAdapter.toJson(updatedSongs)
                )
                db.playlistDao().insertPlaylist(updatedPlaylist)
            }
            onComplete()
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            val playlist = db.playlistDao().getPlaylistById(playlistId) ?: return@launch
            val currentSongs: List<Song> = try {
                songAdapter.fromJson(playlist.songsJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            val updatedSongs = currentSongs.filterNot { it.id == songId }
            val updatedPlaylist = playlist.copy(
                songsJson = songAdapter.toJson(updatedSongs)
            )
            db.playlistDao().insertPlaylist(updatedPlaylist)
        }
    }

    fun getSongsOfPlaylist(playlist: Playlist): List<Song> {
        return try {
            songAdapter.fromJson(playlist.songsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Favorites ---
    fun toggleFavorite(song: Song) {
        val user = currentUser ?: return
        val favId = "${song.id}_${user.id}"
        viewModelScope.launch {
            val isFav = favoritesList.value.any { it.songId == song.id }
            if (isFav) {
                db.favoriteDao().deleteFavorite(favId)
            } else {
                val textDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val fav = Favorite(
                    id = favId,
                    songId = song.id,
                    userId = user.id,
                    title = song.title,
                    artist = song.artist,
                    thumbnail = song.thumbnail,
                    duration = song.duration,
                    source = song.source,
                    filename = song.filename,
                    addedAt = textDate
                )
                db.favoriteDao().insertFavorite(fav)
            }
        }
    }

    // --- Offline download management ---
    fun downloadTrack(song: Song) {
        Toast.makeText(getApplication(), "Bu özellik şuanda kullanılamıyor", Toast.LENGTH_SHORT).show()
        return
    }

    fun deleteDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = downloadsList.value.find { it.id == downloadId } ?: return@launch
            val file = File(record.filepath)
            if (file.exists()) {
                file.delete()
            }
            db.downloadDao().deleteDownloadById(downloadId)

            // If the deleted track was playing from offline file path, flip it to online
            withContext(Dispatchers.Main) {
                if (currentTrack?.id == record.videoId && currentTrack?.source == "download") {
                    currentTrack = currentTrack?.copy(source = "youtube", filename = null)
                }
            }
        }
    }

    // --- Lyrics Genius Search AND Gemini Fallback ---
    fun searchLyrics(query: String) {
        Toast.makeText(getApplication(), "Bu özellik şuanda kullanılamıyor", Toast.LENGTH_SHORT).show()
        return
    }

    fun quickSearchLyrics(title: String, artist: String) {
        Toast.makeText(getApplication(), "Bu özellik şuanda kullanılamıyor", Toast.LENGTH_SHORT).show()
        return
    }

    // --- Comprehensive MediaPlayer Music Control Engine ---
    fun playTrack(song: Song, queue: List<Song> = listOf(song)) {
        stopPlayback()
        backgroundYoutubeEmbedUrl.value = null
        currentTrack = song
        currentQueue = queue
        queueIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        
        // Save to cache for offline recovery
        try {
            val prefs = getApplication<Application>().getSharedPreferences("streamhub_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("last_played_song", singleSongAdapter.toJson(song)).apply()
        } catch (e: Exception) {}
        
        isTrackPlaying = false
        isTrackBuffering = true
        trackCurrentPosition = 0f
        trackCurrentPositionText = "0:00"
        trackDurationText = "0:00"

        playerInitJob?.cancel()
        playerInitJob = viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var player: MediaPlayer? = null
            try {
                player = MediaPlayer().apply {
                    setOnPreparedListener { mp ->
                        viewModelScope.launch(Dispatchers.Main) {
                            if (mediaPlayer == mp) {
                                isTrackBuffering = false
                                isTrackPlaying = true
                                consecutiveLoadFailures = 0 // Reset on successful load
                                showNotification(song, true)
                                mp.start()
                                trackDurationText = formatMillis(mp.duration)
                                startProgressTracker()
                            } else {
                                mp.release()
                            }
                        }
                    }
                    setOnCompletionListener { mp ->
                        viewModelScope.launch(Dispatchers.Main) {
                            if (mediaPlayer == mp) {
                                handleTrackEnded()
                            }
                        }
                    }
                    setOnErrorListener { mp, _, _ ->
                        viewModelScope.launch(Dispatchers.Main) {
                            if (mediaPlayer == mp) {
                                isTrackBuffering = false
                                Log.e("MainViewModel", "MediaPlayer error. Loading background YouTube embed fallback.")
                                Toast.makeText(context, "Bağlantı hatası. YouTube yedek oynatıcı açılıyor...", Toast.LENGTH_LONG).show()
                                isTrackPlaying = true
                                backgroundYoutubeEmbedUrl.value = "https://www.youtube.com/embed/${song.id}?autoplay=1&mute=0"
                            }
                        }
                        true
                    }
                }

                if (!isActive) {
                    player.release()
                    return@launch
                }

                // If offline track, play direct File Path
                val localDownload = db.downloadDao().getDownloadByVideoId(song.id)
                if (localDownload != null) {
                    val localFile = File(localDownload.filepath)
                    if (localFile.exists()) {
                        player.setDataSource(localFile.absolutePath)
                        currentTrack = song.copy(source = "download", filename = localFile.name)
                        player.prepareAsync()
                        withContext(Dispatchers.Main) {
                            mediaPlayer = player
                            consecutiveLoadFailures = 0 // Reset on local track
                        }
                        return@launch
                    }
                }

                // Play from Online URL
                val downloadLink = MusicApiService.getDownloadLink(song.id, song.source)
                if (!downloadLink.isNullOrEmpty() && isActive) {
                    player.setDataSource(downloadLink)
                    player.prepareAsync()
                    withContext(Dispatchers.Main) {
                        mediaPlayer = player
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isTrackBuffering = false
                        Log.e("MainViewModel", "Could not load online audio link. Loading background YouTube embed backup.")
                        if (isActive) {
                            Toast.makeText(context, "Bağlantı hatası. YouTube yedek oynatıcı açılıyor...", Toast.LENGTH_LONG).show()
                            isTrackPlaying = true
                            backgroundYoutubeEmbedUrl.value = "https://www.youtube.com/embed/${song.id}?autoplay=1&mute=0"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isTrackBuffering = false
                    Log.e("MainViewModel", "Exception loading DataSource to MediaPlayer", e)
                    if (isActive) {
                        Toast.makeText(context, "Bağlantı hatası. YouTube yedek oynatıcı açılıyor...", Toast.LENGTH_LONG).show()
                        isTrackPlaying = true
                        backgroundYoutubeEmbedUrl.value = "https://www.youtube.com/embed/${song.id}?autoplay=1&mute=0"
                    }
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) {
                    if (mediaPlayer != player) {
                        player?.release()
                    }
                }
            }
        }
    }

    fun togglePlay() {
        val player = mediaPlayer
        if (player == null) {
            val embedUrl = backgroundYoutubeEmbedUrl.value
            if (!embedUrl.isNullOrEmpty()) {
                if (isTrackPlaying) {
                    backgroundYoutubeEmbedUrl.value = null
                    isTrackPlaying = false
                    currentTrack?.let { showNotification(it, false) }
                } else {
                    currentTrack?.let { song ->
                        backgroundYoutubeEmbedUrl.value = "https://www.youtube.com/embed/${song.id}?autoplay=1&mute=0"
                        isTrackPlaying = true
                        showNotification(song, true)
                    }
                }
                return
            }
            
            currentTrack?.let { playTrack(it, currentQueue) }
            return
        }
        try {
            if (isTrackPlaying) {
                player.pause()
                isTrackPlaying = false
                stopProgressTracker()
                currentTrack?.let { showNotification(it, false) }
            } else {
                player.start()
                isTrackPlaying = true
                startProgressTracker()
                currentTrack?.let { showNotification(it, true) }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "togglePlay error", e)
        }
    }

    fun next() {
        if (currentQueue.isEmpty()) return
        if (isRepeatEnabled) {
            currentTrack?.let { playTrack(it, currentQueue) }
            return
        }
        val nextIndex = queueIndex + 1
        if (nextIndex < currentQueue.size) {
            playTrack(currentQueue[nextIndex], currentQueue)
        } else {
            // Loop back to start
            playTrack(currentQueue[0], currentQueue)
        }
    }

    fun prev() {
        if (currentQueue.isEmpty()) return
        
        try {
            val player = mediaPlayer
            val currentPos = player?.currentPosition ?: 0
            
            // If played more than 3 seconds, or there is no previous track, restart current track
            if (currentPos > 3000 || queueIndex - 1 < 0) {
                player?.seekTo(0)
                trackCurrentPosition = 0f
                trackCurrentPositionText = "0:00"
            } else {
                val prevIndex = queueIndex - 1
                playTrack(currentQueue[prevIndex], currentQueue)
            }
        } catch (e: Exception) {
            // Fallback if player throws illegal state
            if (queueIndex - 1 >= 0) {
                playTrack(currentQueue[queueIndex - 1], currentQueue)
            }
        }
    }

    fun seekTo(fraction: Float) {
        val player = mediaPlayer ?: return
        val positionMillis = (fraction * player.duration).toInt()
        player.seekTo(positionMillis)
        trackCurrentPosition = fraction
        trackCurrentPositionText = formatMillis(positionMillis)
    }

    private fun handleTrackEnded() {
        next()
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressTrackerJob = viewModelScope.launch {
            while (true) {
                try {
                    val player = mediaPlayer
                    if (player != null && isTrackPlaying) {
                        val current = player.currentPosition
                        val duration = player.duration
                        if (duration > 0) {
                            trackCurrentPosition = current.toFloat() / duration.toFloat()
                            trackCurrentPositionText = formatMillis(current)
                            trackDurationText = formatMillis(duration)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore IllegalStateException from Media Player if it is resetting
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
    }

    private fun stopPlayback() {
        playerInitJob?.cancel()
        playerInitJob = null
        stopProgressTracker()
        backgroundYoutubeEmbedUrl.value = null
        
        val notificationManager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(1)
        
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        isTrackPlaying = false
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(mediaControlReceiver)
        } catch (e: Exception) {}
        stopPlayback()
    }

    private fun formatMillis(millis: Int): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
