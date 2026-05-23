package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): User?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun getAllUsers(): Flow<List<User>>

    @Query("UPDATE users SET isBanned = :isBanned WHERE id = :userId")
    suspend fun updateBannedStatus(userId: String, isBanned: Boolean)
}

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: String)

    @Query("SELECT * FROM playlists WHERE userId = :userId ORDER BY createdAt DESC")
    fun getPlaylistsByUserId(userId: String): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: String): Playlist?
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteFavorite(id: String)

    @Query("DELETE FROM favorites WHERE songId = :songId AND userId = :userId")
    suspend fun deleteFavoriteBySongAndUser(songId: String, userId: String)

    @Query("SELECT * FROM favorites WHERE userId = :userId ORDER BY addedAt DESC")
    fun getFavoritesByUserId(userId: String): Flow<List<Favorite>>
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: Download)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: String)

    @Query("SELECT * FROM downloads WHERE userId = :userId ORDER BY downloadedAt DESC")
    fun getDownloadsByUserId(userId: String): Flow<List<Download>>

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<Download>>

    @Query("SELECT * FROM downloads WHERE videoId = :videoId LIMIT 1")
    suspend fun getDownloadByVideoId(videoId: String): Download?
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(searchHistory: SearchHistory)

    @Query("DELETE FROM search_history WHERE userId = :userId")
    suspend fun clearHistoryByUserId(userId: String)

    @Query("SELECT * FROM search_history WHERE userId = :userId ORDER BY timestamp DESC LIMIT 50")
    fun getSearchHistoryByUserId(userId: String): Flow<List<SearchHistory>>

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    fun getAllSearchHistory(): Flow<List<SearchHistory>>
}

@Dao
interface BannerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBanner(banner: Banner)

    @Query("DELETE FROM banners WHERE id = :id")
    suspend fun deleteBannerById(id: String)

    @Query("SELECT * FROM banners WHERE active = 1 ORDER BY createdAt DESC")
    fun getActiveBanners(): Flow<List<Banner>>

    @Query("SELECT * FROM banners ORDER BY createdAt DESC")
    fun getAllBanners(): Flow<List<Banner>>

    @Query("UPDATE banners SET active = :active WHERE id = :id")
    suspend fun updateBannerActive(id: String, active: Boolean)
}

@Dao
interface LyricsCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: LyricsCache)

    @Query("SELECT * FROM lyrics_cache WHERE id = :id LIMIT 1")
    suspend fun getLyricsById(id: String): LyricsCache?
}

@Dao
interface SiteSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: SiteSettings)

    @Query("SELECT * FROM site_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): SiteSettings?
}

@Database(
    entities = [
        User::class,
        Playlist::class,
        Favorite::class,
        Download::class,
        SearchHistory::class,
        Banner::class,
        LyricsCache::class,
        SiteSettings::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun downloadDao(): DownloadDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun bannerDao(): BannerDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
    abstract fun siteSettingsDao(): SiteSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "streamhub_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
