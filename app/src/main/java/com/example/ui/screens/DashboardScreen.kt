package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.database.Download
import com.example.data.database.Playlist
import com.example.data.database.Song
import com.example.data.database.Favorite
import com.example.ui.viewmodel.AppColorTheme
import com.example.ui.viewmodel.DashboardTab
import com.example.ui.viewmodel.MainViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, onOpenAdmin: () -> Unit) {
    val activeTab = viewModel.currentTab
    val user = viewModel.currentUser
    val siteSettings by viewModel.siteSettingsState.collectAsState()

    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf<Song?>(null) }

    Scaffold(
        bottomBar = {
            Column {
                // MINI PLAYER (visible if track selected)
                viewModel.currentTrack?.let { track ->
                    MiniPlayer(
                        track = track,
                        isPlaying = viewModel.isTrackPlaying,
                        isBuffering = viewModel.isTrackBuffering,
                        progress = viewModel.trackCurrentPosition,
                        onTogglePlay = { viewModel.togglePlay() },
                        onExpand = { isPlayerExpanded = true },
                        isLiked = viewModel.favoritesList.collectAsState().value.any { it.songId == track.id },
                        onToggleLike = { viewModel.toggleFavorite(track) }
                    )
                }

                // BOTTOM NAVIGATION
                BottomNavigationBar(
                    activeTab = activeTab,
                    onTabSelected = { viewModel.currentTab = it }
                )
            }
        },
        containerColor = Color(0xFF040404)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // TOP HEADER
                TopHeaderSection(
                    siteName = siteSettings?.siteName ?: "StreamHub Pro",
                    logoUrl = siteSettings?.logoUrl ?: "",
                    userAvatar = user?.avatarUrl ?: "",
                    userName = user?.username ?: "",
                    isAdmin = viewModel.isAdminLoggedIn,
                    onOpenAdmin = onOpenAdmin,
                    onProfileClick = { viewModel.currentTab = DashboardTab.PROFILE_CUSTOMIZE }
                )

                // ACTIVE AD BANNER (if active and not closed)
                val banners by viewModel.bannersList.collectAsState()
                var closedBannersList by remember { mutableStateOf(setOf<String>()) }
                val activeBanners = banners.filter { it.active && !closedBannersList.contains(it.id) }

                if (activeBanners.isNotEmpty()) {
                    val activeBanner = activeBanners.first()
                    BannerSection(
                        title = activeBanner.title,
                        imageUrl = activeBanner.imageUrl,
                        onClose = { closedBannersList = closedBannersList + activeBanner.id }
                    )
                }

                // SUBPAGES VIEWPORT
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        DashboardTab.HOME -> HomeScreen(viewModel, onQuickCardClick = { q ->
                            viewModel.currentTab = DashboardTab.SEARCH
                            viewModel.performSearch(q)
                        })
                        DashboardTab.SEARCH -> SearchScreen(
                            viewModel = viewModel,
                            onAddToPlaylist = { showAddToPlaylistDialog = it }
                        )
                        DashboardTab.LIBRARY -> LibraryScreen(
                            viewModel = viewModel,
                            onCreatePlaylistClick = { showCreatePlaylistDialog = true }
                        )
                        DashboardTab.DOWNLOADS -> DownloadsScreen(viewModel)
                        DashboardTab.LYRICS -> LyricsScreenTab(viewModel)
                        DashboardTab.HISTORY -> HistoryScreen(viewModel)
                        DashboardTab.PROFILE_CUSTOMIZE -> ProfileCustomizeScreen(viewModel)
                    }
                }
            }

            // FULL DISPLAY EXPANDED DRAWER PLAYER OVERLAY
            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 400)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 400)
                ) + fadeOut()
            ) {
                viewModel.currentTrack?.let { track ->
                    FullPlayerOverlay(
                        track = track,
                        isPlaying = viewModel.isTrackPlaying,
                        isBuffering = viewModel.isTrackBuffering,
                        progress = viewModel.trackCurrentPosition,
                        currentTimeText = viewModel.trackCurrentPositionText,
                        durationText = viewModel.trackDurationText,
                        isRepeat = viewModel.isRepeatEnabled,
                        onTogglePlay = { viewModel.togglePlay() },
                        onNext = { viewModel.next() },
                        onPrev = { viewModel.prev() },
                        onSeek = { viewModel.seekTo(it) },
                        onToggleRepeat = { viewModel.isRepeatEnabled = !viewModel.isRepeatEnabled },
                        onCollapse = { isPlayerExpanded = false },
                        isLiked = viewModel.favoritesList.collectAsState().value.any { it.songId == track.id },
                        onToggleLike = { viewModel.toggleFavorite(track) },
                        onDownload = { viewModel.downloadTrack(track) },
                        isDownloaded = viewModel.downloadsList.collectAsState().value.any { it.videoId == track.id },
                        viewModel = viewModel
                    )
                }
            }

            // CREATING PLAYLIST MODAL CONTEXT
            if (showCreatePlaylistDialog) {
                var newPlName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showCreatePlaylistDialog = false },
                    title = { Text("Yeni Çalma Listesi Oluştur", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = newPlName,
                            onValueChange = { newPlName = it },
                            label = { Text("Çalma Listesi Adı") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newPlName.isNotEmpty()) {
                                    viewModel.createPlaylist(newPlName)
                                    showCreatePlaylistDialog = false
                                }
                            }
                        ) {
                            Text("Oluştur")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreatePlaylistDialog = false }) {
                            Text("İptal")
                        }
                    },
                    containerColor = Color(0xFF1E1E1E)
                )
            }

            // ADD TO PLAYLIST SEARCH MODAL
            showAddToPlaylistDialog?.let { song ->
                val playlists by viewModel.playlistsList.collectAsState()
                AlertDialog(
                    onDismissRequest = { showAddToPlaylistDialog = null },
                    title = { Text("Listeye Ekle", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                        ) {
                            if (playlists.isEmpty()) {
                                Text("Henüz çalma listeniz yok.", color = Color.Gray)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(playlists) { pl ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .clickable {
                                                    viewModel.addSongToPlaylist(pl.id, song) {
                                                        showAddToPlaylistDialog = null
                                                    }
                                                }
                                                .padding(14.dp)
                                        ) {
                                            Text(pl.name, color = Color.White, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showAddToPlaylistDialog = null }) {
                            Text("Kapat")
                        }
                    },
                    containerColor = Color(0xFF1E1E1E)
                )
            }

            // Live download pop-up progress overlay
            viewModel.activeDownloadProgress?.let { progress ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
                            .padding(24.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "İndiriliyor...\n${viewModel.activeDownloadName}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = progress,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(200.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- Top header section ---
@Composable
fun TopHeaderSection(
    siteName: String,
    logoUrl: String,
    userAvatar: String,
    userName: String,
    isAdmin: Boolean,
    onOpenAdmin: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF040404))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = logoUrl,
                contentDescription = "Logo",
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = siteName,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isAdmin) {
                IconButton(onClick = onOpenAdmin) {
                    Icon(Icons.Default.Settings, contentDescription = "Admin Area", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // User Profile Link Click
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { onProfileClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = userAvatar,
                    contentDescription = "User Avatar",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = userName,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 80.dp)
                )
            }
        }
    }
}

// --- ad closable banner block ---
@Composable
fun BannerSection(title: String, imageUrl: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.DarkGray)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Title Overlay (semi gradient translucent)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .padding(12.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Close key
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

// --- Home tab screen ---
@Composable
fun HomeScreen(viewModel: MainViewModel, onQuickCardClick: (String) -> Unit) {
    val favorites by viewModel.favoritesList.collectAsState()
    val downloads by viewModel.downloadsList.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                text = "Tünaydın, ${viewModel.currentUser?.username ?: "Dinleyici"}",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        item {
            // Category exploration cards grid
            Text("Müzik Keşfet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(130.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { CategoryExploreCard("Türkçe Pop", Color(0xFF1DB954)) { onQuickCardClick("Türkçe Pop 2026") } }
                item { CategoryExploreCard("Global Top 50", Color(0xFFE91E63)) { onQuickCardClick("Global Top 50") } }
                item { CategoryExploreCard("Rock Classics", Color(0xFF673AB7)) { onQuickCardClick("Rock Hits Classics") } }
                item { CategoryExploreCard("Deep Focus", Color(0xFF03A9F4)) { onQuickCardClick("Deep Focus study sleep ambient") } }
            }
        }

        // Favorites item compilations
        if (favorites.isNotEmpty()) {
            item {
                Text("Beğendiğin Şarkılar", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            items(favorites.take(6)) { fav ->
                val song = Song(fav.songId, fav.title, fav.artist, fav.thumbnail, fav.duration, fav.source, fav.filename)
                TrackRowItem(
                    song = song,
                    isLiked = true,
                    isDownloaded = downloads.any { d -> d.videoId == song.id },
                    onPlay = { viewModel.playTrack(song, favorites.map { f -> Song(f.songId, f.title, f.artist, f.thumbnail, f.duration, f.source, f.filename) }) },
                    onToggleLike = { viewModel.toggleFavorite(song) },
                    onDownload = { viewModel.downloadTrack(song) }
                )
            }
        }

        // Download lists compilation
        if (downloads.isNotEmpty()) {
            item {
                Text("Çevrimdışı İndirilenler", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 8.dp))
            }
            items(downloads.take(4)) { dl ->
                val song = Song(dl.videoId, dl.title, dl.artist, dl.thumbnail, "", "download", dl.filename)
                TrackRowItem(
                    song = song,
                    isLiked = favorites.any { f -> f.songId == song.id },
                    isDownloaded = true,
                    onPlay = { viewModel.playTrack(song, downloads.map { d -> Song(d.videoId, d.title, d.artist, d.thumbnail, "", "download", d.filename) }) },
                    onToggleLike = { viewModel.toggleFavorite(song) },
                    onDownload = {}
                )
            }
        }

        // Buffer empty padding at the end
        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun CategoryExploreCard(label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint)
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// --- Search Screen viewport ---
@Composable
fun SearchScreen(viewModel: MainViewModel, onAddToPlaylist: (Song) -> Unit) {
    val searchResults = viewModel.searchResults
    val suggestions = viewModel.searchSuggestions
    val isSearching = viewModel.isSearching
    val downloads by viewModel.downloadsList.collectAsState()
    val favorites by viewModel.favoritesList.collectAsState()

    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            trailingIcon = {
                if (viewModel.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            },
            placeholder = { Text("Şarkı, sanatçı veya video ara...", color = Color.Gray) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                focusedContainerColor = Color(0xFF121212),
                unfocusedContainerColor = Color(0xFF121212),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("search_field")
        )

        Box(modifier = Modifier.weight(1f)) {
            // Loader
            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            // Results lists
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (searchResults.isNotEmpty()) {
                    items(searchResults) { song ->
                        val isLiked = favorites.any { f -> f.songId == song.id }
                        val isDownloaded = downloads.any { d -> d.videoId == song.id }

                        TrackSearchRowItem(
                            song = song,
                            isLiked = isLiked,
                            isDownloaded = isDownloaded,
                            onPlay = { viewModel.playTrack(song, searchResults) },
                            onToggleLike = { viewModel.toggleFavorite(song) },
                            onDownload = { viewModel.downloadTrack(song) },
                            onAddPlaylist = { onAddToPlaylist(song) },
                            onFetchLyrics = { viewModel.quickSearchLyrics(song.title, song.artist) }
                        )
                    }
                } else if (viewModel.searchQuery.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(80.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Ne dinlemek istersin?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Milyonlarca şarkı ve video sizi bekliyor.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Autocomplete pop up absolute box
            if (suggestions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(vertical = 4.dp)
                        .testTag("suggestions_dropdown")
                ) {
                    items(suggestions) { keyword ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    focusManager.clearFocus()
                                    viewModel.performSearch(keyword)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(keyword, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// --- Library tab viewport ---
@Composable
fun LibraryScreen(viewModel: MainViewModel, onCreatePlaylistClick: () -> Unit) {
    var librarySubTab by remember { mutableStateOf("playlists") }
    val playlists by viewModel.playlistsList.collectAsState()
    val favorites by viewModel.favoritesList.collectAsState()
    val downloads by viewModel.downloadsList.collectAsState()

    var activePlaylistDetails by remember { mutableStateOf<Playlist?>(null) }

    if (activePlaylistDetails != null) {
        val selectedPl = playlists.find { it.id == activePlaylistDetails?.id }
        if (selectedPl == null) {
            activePlaylistDetails = null
        } else {
            PlaylistDetailView(
                playlist = selectedPl,
                onBack = { activePlaylistDetails = null },
                onPlayTrack = { song, queue -> viewModel.playTrack(song, queue) },
                onRemoveTrack = { songId -> viewModel.removeSongFromPlaylist(selectedPl.id, songId) },
                onDeletePlaylist = {
                    viewModel.deletePlaylist(selectedPl.id)
                    activePlaylistDetails = null
                },
                favorites = favorites,
                downloads = downloads,
                onToggleLike = { viewModel.toggleFavorite(it) },
                onDownload = { viewModel.downloadTrack(it) }
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { librarySubTab = "playlists" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (librarySubTab == "playlists") MaterialTheme.colorScheme.primary else Color(0xFF121212)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text("Çalma Listeleri", color = if (librarySubTab == "playlists") Color.Black else Color.White)
                }

                Button(
                    onClick = { librarySubTab = "favorites" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (librarySubTab == "favorites") MaterialTheme.colorScheme.primary else Color(0xFF121212)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text("Beğenilenler", color = if (librarySubTab == "favorites") Color.Black else Color.White)
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onCreatePlaylistClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create", tint = Color.White)
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (librarySubTab == "playlists") {
                    if (playlists.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Henüz çalma listeniz yok.", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(playlists) { pl ->
                                val coverSongs = viewModel.getSongsOfPlaylist(pl)
                                val finalCover = if (coverSongs.isNotEmpty()) coverSongs.first().thumbnail else pl.coverUrl
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF121212))
                                        .clickable { activePlaylistDetails = pl }
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = finalCover,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = pl.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                         text = "${coverSongs.size} Şarkı",
                                         color = Color.Gray,
                                         fontSize = 11.sp,
                                         fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (favorites.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Henüz favori şarkınız yok.", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(favorites) { index, fav ->
                                val song = Song(fav.songId, fav.title, fav.artist, fav.thumbnail, fav.duration, fav.source, fav.filename)
                                TrackDetailRow(
                                    index = index + 1,
                                    song = song,
                                    isLiked = true,
                                    isDownloaded = downloads.any { d -> d.videoId == song.id },
                                    onPlay = { viewModel.playTrack(song, favorites.map { f -> Song(f.songId, f.title, f.artist, f.thumbnail, f.duration, f.source, f.filename) }) },
                                    onToggleLike = { viewModel.toggleFavorite(song) },
                                    onDownload = { viewModel.downloadTrack(song) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Playlist details compiled block ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    onBack: () -> Unit,
    onPlayTrack: (Song, List<Song>) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    favorites: List<Favorite>,
    downloads: List<Download>,
    onToggleLike: (Song) -> Unit,
    onDownload: (Song) -> Unit
) {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val songListType = Types.newParameterizedType(List::class.java, Song::class.java)
    val songAdapter = moshi.adapter<List<Song>>(songListType)
    val songList: List<Song> = try {
        songAdapter.fromJson(playlist.songsJson) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(playlist.name, fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onDeletePlaylist) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete List", tint = Color(0xFFE91E63))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        },
        containerColor = Color(0xFF040404)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Cover header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = if (songList.isNotEmpty()) songList.first().thumbnail else playlist.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(playlist.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Oluşturulma: ${playlist.createdAt}", fontSize = 12.sp, color = Color.Gray)
                    Text("${songList.size} Şarkı", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (songList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Listeniz boş. Arama yaparak şarkı ekleyin.", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(songList) { index, song ->
                            val isLiked = favorites.any { f -> f.songId == song.id }
                            val isDownloaded = downloads.any { d -> d.videoId == song.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF121212))
                                    .clickable { onPlayTrack(song, songList) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                                AsyncImage(
                                    model = song.thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                    Text(song.artist, fontSize = 11.sp, color = Color.Gray)
                                }
                                IconButton(onClick = { onToggleLike(song) }) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Like",
                                        tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { if (!isDownloaded) onDownload(song) }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Download",
                                        tint = if (isDownloaded) Color(0xFF4CAF50) else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onRemoveTrack(song.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Downloads Viewport ---
@Composable
fun DownloadsScreen(viewModel: MainViewModel) {
    val downloads by viewModel.downloadsList.collectAsState()
    val favorites by viewModel.favoritesList.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = "Yüklenen Çevrimdışı Dosyalar",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "Kullanılabilir yerel müzik listeleriniz.",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            if (downloads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Henüz indirilmiş şarkınız yok.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(downloads) { dl ->
                        val song = Song(dl.videoId, dl.title, dl.artist, dl.thumbnail, "", "download", dl.filename)
                        val isLiked = favorites.any { f -> f.songId == song.id }
                        val fileMB = (dl.size.toFloat() / (1024f * 1024f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF121212))
                                .clickable { viewModel.playTrack(song, downloads.map { d -> Song(d.videoId, d.title, d.artist, d.thumbnail, "", "download", d.filename) }) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.thumbnail,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                Text("${song.artist} • %.1f MB".format(fileMB), fontSize = 11.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.deleteDownload(dl.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE91E63), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Lyrics Screen Tab ---
@Composable
fun LyricsScreenTab(viewModel: MainViewModel) {
    val isLyricsLoading = viewModel.isLyricsLoading
    val title = viewModel.lyricsResultTitle
    val lyrics = viewModel.lyricsResultText
    val bio = viewModel.lyricsResultBio

    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text("Şarkı Sözleri ve Açıklamalar", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 16.dp))
        Text("Şarkı sözlerini ve hikayelerini Genius ve Gemini AI ile keşfedin.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))

        OutlinedTextField(
            value = viewModel.lyricsQuery,
            onValueChange = { viewModel.lyricsQuery = it },
            placeholder = { Text("Şarkı veya Sanatçı Adı girin...", color = Color.Gray) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            trailingIcon = {
                IconButton(onClick = {
                    focusManager.clearFocus()
                    viewModel.searchLyrics(viewModel.lyricsQuery)
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                focusedContainerColor = Color(0xFF121212),
                unfocusedContainerColor = Color(0xFF121212),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("lyrics_tab_search")
        )

        Box(modifier = Modifier.weight(1f)) {
            if (isLyricsLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (lyrics.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bir şarkı aratarak sözlerini yükleyin.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            if (bio.isNotEmpty()) {
                                Text("Şarkı Hikayesi & Trivia", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF121212))
                                        .padding(14.dp)
                                ) {
                                    Text(bio, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 18.sp)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }

                    item {
                        Text("Şarkı Sözleri", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF121212).copy(alpha = 0.5f))
                                .padding(20.dp)
                        ) {
                            Text(
                                text = lyrics,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 24.sp
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }
    }
}

// --- History viewport ---
@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val searchHistory by viewModel.searchHistoryList.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Arama Geçmişiniz", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            TextButton(onClick = { viewModel.clearSearchHistory() }) {
                Text("Temizle", color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (searchHistory.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Arama geçmişiniz temiz.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(searchHistory) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF121212))
                                .clickable {
                                    viewModel.currentTab = DashboardTab.SEARCH
                                    viewModel.performSearch(item.query)
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(item.query, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Text(
                                text = item.timestamp.substringAfter(" "),
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Next-gen profile customization tab ---
@Composable
fun ProfileCustomizeScreen(viewModel: MainViewModel) {
    val user = viewModel.currentUser ?: return

    var usernameText by remember { mutableStateOf(user.username) }
    var avatarUrlText by remember { mutableStateOf(user.avatarUrl) }
    var resultText by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Profilini Özelleştir",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Kullanıcı adını, avatarını ve uygulama temasını kişiselleştir.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF121212))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = avatarUrlText,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = usernameText,
                    onValueChange = { usernameText = it },
                    label = { Text("Kullanıcı Adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = avatarUrlText,
                    onValueChange = { avatarUrlText = it },
                    label = { Text("Profil Resmi (Avatar) URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (resultText.isNotEmpty()) {
                    Text(
                        text = resultText,
                        color = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFE91E63),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                Button(
                    onClick = {
                        viewModel.updateProfile(usernameText, avatarUrlText) { success, msg ->
                            isSuccess = success
                            resultText = msg
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Profili Güncelle", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Real-Time Theme Accent selection
        item {
            Text(
                text = "Uygulama Teması (Accent Color)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF121212))
                    .padding(14.dp)
            ) {
                AppColorTheme.values().forEach { configTheme ->
                    val isSelected = viewModel.activeTheme == configTheme
                    val accentColor = when (configTheme) {
                        AppColorTheme.SPOTIFY_GREEN -> Color(0xFF1DB954)
                        AppColorTheme.COSMIC_INDIGO -> Color(0xFF673AB7)
                        AppColorTheme.CYBERPUNK_AMBER -> Color(0xFFFFB300)
                        AppColorTheme.NEON_PINK -> Color(0xFFE91E63)
                        AppColorTheme.CRIMSON_RED -> Color(0xFFD50000)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                            .clickable { viewModel.changeAppTheme(configTheme) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(configTheme.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }

                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Sign Out key
        item {
            Button(
                onClick = { viewModel.signOut() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(46.dp)
                    .testTag("sign_out_button")
            ) {
                Text("Çıkış Yap (Oturumu Kapat)", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// --- Floating bottom mini player ---
@Composable
fun MiniPlayer(
    track: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: Float,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xFF1B1B1B))
            .clickable { onExpand() }
            .testTag("mini_player")
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onToggleLike) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onTogglePlay) {
                if (isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    if (isPlaying) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.size(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(Color.White))
                            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(Color.White))
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Fine progress bar on extremely bottom edge
        LinearProgressIndicator(
            progress = progress,
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
        )
    }
}

// --- Player drawer Full Player layout ---
@Composable
fun FullPlayerOverlay(
    track: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: Float,
    currentTimeText: String,
    durationText: String,
    isRepeat: Boolean,
    isLiked: Boolean,
    isDownloaded: Boolean,
    onToggleLike: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleRepeat: () -> Unit,
    onDownload: () -> Unit,
    onCollapse: () -> Unit,
    viewModel: MainViewModel
) {
    var showPlayerLyrics by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF242424), Color(0xFF0C0C0C))
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Text("OYNATIYOR", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp)
            IconButton(onClick = {}) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Large graphics cover
        AsyncImage(
            model = track.thumbnail,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.weight(0.1f))

        // Title Row
        Row(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, fontSize = 14.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            IconButton(onClick = onToggleLike) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress bar seek
        Slider(
            value = progress,
            onValueChange = onSeek,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth(0.9f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(currentTimeText, color = Color.Gray, fontSize = 11.sp)
            Text(durationText, color = Color.Gray, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Control Keys
        Row(
            modifier = Modifier.fillMaxWidth(0.95f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Repeat switch
            IconButton(onClick = onToggleRepeat) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = if (isRepeat) MaterialTheme.colorScheme.primary else Color.White
                )
            }

            IconButton(onClick = onPrev) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(36.dp))
            }

            // Big play key
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onTogglePlay() },
                contentAlignment = Alignment.Center
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.Black)
                } else {
                    if (isPlaying) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.size(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(Color.Black))
                            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(Color.Black))
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            IconButton(onClick = onNext) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
            }

            // Download toggle offline (DropDown acts as download)
            IconButton(onClick = { if (!isDownloaded) onDownload() }) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = if (isDownloaded) Color(0xFF4CAF50) else Color.White
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Expandable bottom lyrics sheet toggle
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.35f)
                .clickable {
                    showPlayerLyrics = !showPlayerLyrics
                    if (showPlayerLyrics && viewModel.lyricsResultText.isEmpty()) {
                        viewModel.searchLyrics("${track.title} ${track.artist}")
                    }
                }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Şarki Sözleri", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Icon(
                        imageVector = if (showPlayerLyrics) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (showPlayerLyrics) {
                    if (viewModel.isLyricsLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally).size(24.dp))
                    } else if (viewModel.lyricsResultText.isNotEmpty()) {
                        Text(
                            text = viewModel.lyricsResultText,
                            color = Color.White,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    } else {
                        Text("Sözler yükleniyor veya bulunamadı...", color = Color.Gray, fontSize = 12.sp)
                    }
                } else {
                    Text("Detayları ve şarkı sözlerini görmek için dokunun...", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

// --- Bottom navigation component ---
@Composable
fun BottomNavigationBar(activeTab: DashboardTab, onTabSelected: (DashboardTab) -> Unit) {
    NavigationBar(
        containerColor = Color(0xFF121212),
        contentColor = Color.White,
        modifier = Modifier.navigationBarsPadding()
    ) {
        NavigationBarItem(
            selected = activeTab == DashboardTab.HOME,
            onClick = { onTabSelected(DashboardTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Ana Sayfa", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = activeTab == DashboardTab.SEARCH,
            onClick = { onTabSelected(DashboardTab.SEARCH) },
            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            label = { Text("Ara", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = activeTab == DashboardTab.LIBRARY,
            onClick = { onTabSelected(DashboardTab.LIBRARY) },
            icon = { Icon(Icons.Default.List, contentDescription = "Library") },
            label = { Text("Kitaplık", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = activeTab == DashboardTab.DOWNLOADS,
            onClick = { onTabSelected(DashboardTab.DOWNLOADS) },
            icon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Downloads") },
            label = { Text("İndirilenler", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = activeTab == DashboardTab.LYRICS,
            onClick = { onTabSelected(DashboardTab.LYRICS) },
            icon = { Icon(Icons.Default.Star, contentDescription = "Lyrics") },
            label = { Text("Sözler", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.Transparent
            )
        )
    }
}

// Helper tracks rows visual components
@Composable
fun TrackRowItem(
    song: Song,
    isLiked: Boolean,
    isDownloaded: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121212))
            .clickable { onPlay() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnail,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, fontSize = 11.sp, color = Color.Gray)
        }

        IconButton(onClick = onToggleLike) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(onClick = { if (!isDownloaded) onDownload() }) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = if (isDownloaded) Color(0xFF4CAF50) else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun TrackSearchRowItem(
    song: Song,
    isLiked: Boolean,
    isDownloaded: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    onAddPlaylist: () -> Unit,
    onFetchLyrics: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121212))
            .clickable { onPlay() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnail,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        IconButton(onClick = onToggleLike) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(onClick = { if (!isDownloaded) onDownload() }) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = if (isDownloaded) Color(0xFF4CAF50) else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(onClick = onAddPlaylist) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        }

        IconButton(onClick = onFetchLyrics) {
            Icon(Icons.Default.Info, contentDescription = "Lyrics", tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun TrackDetailRow(
    index: Int,
    song: Song,
    isLiked: Boolean,
    isDownloaded: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121212))
            .clickable { onPlay() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$index", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(24.dp))
        AsyncImage(
            model = song.thumbnail,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, fontSize = 11.sp, color = Color.Gray)
        }
        IconButton(onClick = onToggleLike) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = { if (!isDownloaded) onDownload() }) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = if (isDownloaded) Color(0xFF4CAF50) else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
