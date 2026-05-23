package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.viewmodel.MainViewModel

@Composable
fun AdminScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    var selectedAdminTab by remember { mutableStateOf("stats") }

    val users by viewModel.usersList.collectAsState()
    val playlists by viewModel.playlistsList.collectAsState()
    val downloads by viewModel.downloadsList.collectAsState()
    val searchHistory by viewModel.searchHistoryList.collectAsState()
    val banners by viewModel.bannersList.collectAsState()
    val settings by viewModel.siteSettingsState.collectAsState()

    // Form states
    var bannerTitle by remember { mutableStateOf("") }
    var bannerImage by remember { mutableStateOf("") }
    var bannerLink by remember { mutableStateOf("") }

    var settingsName by remember { mutableStateOf(settings?.siteName ?: "StreamHub Pro") }
    var settingsLogo by remember { mutableStateOf(settings?.logoUrl ?: "") }
    var settingsPassword by remember { mutableStateOf("") }

    LaunchedEffect(settings) {
        settings?.let {
            settingsName = it.siteName
            settingsLogo = it.logoUrl
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("StreamHub Yönetim Paneli", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
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
            // Tabs row selection (Stats, Users, Banners, Settings)
            ScrollableTabRow(
                selectedTabIndex = when (selectedAdminTab) {
                    "stats" -> 0
                    "users" -> 1
                    "banners" -> 2
                    else -> 3
                },
                containerColor = Color(0xFF121212),
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp
            ) {
                Tab(
                    selected = selectedAdminTab == "stats",
                    onClick = { selectedAdminTab = "stats" },
                    text = { Text("İstatistikler", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedAdminTab == "users",
                    onClick = { selectedAdminTab = "users" },
                    text = { Text("Kullanıcılar", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedAdminTab == "banners",
                    onClick = { selectedAdminTab = "banners" },
                    text = { Text("Bannerlar", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedAdminTab == "settings",
                    onClick = { selectedAdminTab = "settings" },
                    text = { Text("Ayarlar", fontWeight = FontWeight.SemiBold) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedAdminTab) {
                    "stats" -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item {
                                Text("Genel Sistem Özeti", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    StatCard("Toplam Kullanıcı", users.size.toString(), Modifier.weight(1f))
                                    StatCard("Toplam İndirme", downloads.size.toString(), Modifier.weight(1f))
                                }
                            }
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    StatCard("Toplam Arama", searchHistory.size.toString(), Modifier.weight(1f))
                                    StatCard("Aktif Banner", banners.filter { it.active }.size.toString(), Modifier.weight(1f))
                                }
                            }

                            // Top searches term compilation dynamically from logs
                            val searchCounts = searchHistory.groupBy { it.query.lowercase().trim() }
                                .mapValues { it.value.size }
                                .toList()
                                .sortedByDescending { it.second }
                                .take(8)

                            if (searchCounts.isNotEmpty()) {
                                item {
                                    Text("En Çok Aranan Kelimeler", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 16.dp))
                                }
                                items(searchCounts) { (query, count) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF121212), RoundedCornerShape(12.dp))
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(query, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text("$count Arama", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    "users" -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                Text("Kullanıcı Kayıt Listesi", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            items(users) { u ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF121212))
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Avatar
                                    AsyncImage(
                                        model = u.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(Color.DarkGray)
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(u.username, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Kayıt: ${u.createdAt}", fontSize = 12.sp, color = Color.Gray)
                                    }

                                    Button(
                                        onClick = { viewModel.toggleBanUser(u.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (u.isBanned) Color(0xFF4CAF50) else Color(0xFFE91E63)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(
                                            text = if (u.isBanned) "Yasağı Kaldır" else "Yasakla",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    "banners" -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item {
                                Text("Yeni Reklam Banner Ekle", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF121212))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = bannerTitle,
                                        onValueChange = { bannerTitle = it },
                                        label = { Text("Banner Başlığı") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = bannerImage,
                                        onValueChange = { bannerImage = it },
                                        label = { Text("Resim URL'si") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = bannerLink,
                                        onValueChange = { bannerLink = it },
                                        label = { Text("Tıklama Bağlantısı (URL)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            if (bannerTitle.isNotEmpty() && bannerImage.isNotEmpty()) {
                                                viewModel.addBanner(bannerTitle, bannerImage, bannerLink)
                                                bannerTitle = ""
                                                bannerImage = ""
                                                bannerLink = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(44.dp)
                                    ) {
                                        Text("Bannerı Kaydet", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            item {
                                Text("Mevcut Banners", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 12.dp))
                            }

                            items(banners) { b ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF121212))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = b.imageUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(width = 80.dp, height = 48.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.DarkGray),
                                        contentScale = ContentScale.Crop
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(b.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(
                                            text = if (b.active) "Durum: Aktif" else "Durum: Pasif",
                                            fontSize = 11.sp,
                                            color = if (b.active) Color(0xFF4CAF50) else Color.Gray,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(onClick = { viewModel.toggleBanner(b.id, !b.active) }) {
                                            Icon(
                                                imageVector = if (b.active) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = "Toggle Active",
                                                tint = if (b.active) MaterialTheme.colorScheme.primary else Color.Gray
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteBanner(b.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE91E63))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "settings" -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                Text("Global Site Ayarları", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF121212))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    OutlinedTextField(
                                        value = settingsName,
                                        onValueChange = { settingsName = it },
                                        label = { Text("Site Adı") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = settingsLogo,
                                        onValueChange = { settingsLogo = it },
                                        label = { Text("Site Logo URL") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = settingsPassword,
                                        onValueChange = { settingsPassword = it },
                                        label = { Text("Yönetici Şifresini Değiştir") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.updateSiteSettings(settingsName, settingsLogo, settingsPassword)
                                            settingsPassword = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(44.dp)
                                    ) {
                                        Text("Ayarları Güncelle", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(label, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
