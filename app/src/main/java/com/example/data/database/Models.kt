package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val duration: String = "",
    val source: String = "youtube",
    val filename: String? = null
)

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    val username: String,
    val passwordHash: String,
    val avatarUrl: String,
    val createdAt: String,
    val isBanned: Boolean = false,
    val isAdmin: Boolean = false
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val songsJson: String, // Stringified list of Song
    val createdAt: String,
    val coverUrl: String
)

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val id: String, // songId + "_" + userId to be unique
    val songId: String,
    val userId: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val duration: String,
    val source: String,
    val filename: String? = null,
    val addedAt: String
)

@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey val id: String,
    val userId: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val filename: String,
    val filepath: String,
    val downloadedAt: String,
    val size: Long,
    val shared: Boolean = true
)

@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey val id: String,
    val userId: String,
    val query: String,
    val timestamp: String
)

@Entity(tableName = "banners")
data class Banner(
    @PrimaryKey val id: String,
    val title: String,
    val imageUrl: String,
    val link: String,
    val active: Boolean,
    val createdAt: String
)

@Entity(tableName = "lyrics_cache")
data class LyricsCache(
    @PrimaryKey val id: String, // md5 of query
    val query: String,
    val fullTitle: String,
    val artUrl: String,
    val lyrics: String,
    val bio: String,
    val cachedAt: String
)

@Entity(tableName = "site_settings")
data class SiteSettings(
    @PrimaryKey val id: Int = 1,
    val siteName: String,
    val logoUrl: String,
    val adminPasswordHash: String,
    val enableBanners: Boolean = true,
    val maxFileSizeMB: Int = 100
)
