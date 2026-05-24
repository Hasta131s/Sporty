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
    val duration: String,
    val source: String = "youtube"
)

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val email: String,
    val profilePicUrl: String = "",
    val isPremium: Boolean = false,
    val themePreference: String = "dark"
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val songIdsJson: String = "[]", // Stores list of song IDs
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val duration: String,
    val source: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val duration: String,
    val source: String,
    val localFilePath: String,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "banners")
data class Banner(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val imageUrl: String,
    val title: String,
    val actionUrl: String = ""
)

@Entity(tableName = "lyrics_cache")
data class LyricsCache(
    @PrimaryKey val songId: String,
    val lyrics: String,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "site_settings")
data class SiteSettings(
    @PrimaryKey val key: String,
    val value: String
)
