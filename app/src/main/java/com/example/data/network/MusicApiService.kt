package com.example.data.network

import android.content.Context
import android.util.Log
import com.example.data.database.Song
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object MusicApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.yt",
        "https://pipedapi.oxp.li",
        "https://pipedapi.leptons.xyz"
    )

    fun searchYouTube(query: String): List<Song> {
        val results = mutableListOf<Song>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        
        for (instance in pipedInstances) {
            try {
                val url = "$instance/search?q=$encodedQuery&filter=streams"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val items = json.optJSONArray("streams") ?: json.optJSONArray("items")
                            if (items != null) {
                                for (i in 0 until items.length()) {
                                    val item = items.getJSONObject(i)
                                    val itemType = item.optString("type", "stream")
                                    if (itemType == "stream" || item.has("videoId")) {
                                        val videoId = item.optString("id").ifEmpty { item.optString("videoId") }
                                        val title = item.optString("title")
                                        val artist = item.optString("uploaderName").ifEmpty { item.optString("uploader", "Unknown Artist") }
                                        val thumbnail = item.optString("thumbnail").ifEmpty { "https://img.youtube.com/vi/$videoId/0.jpg" }
                                        val durationSec = item.optInt("duration", 0)
                                        val durationStr = if (durationSec > 0) {
                                            val m = durationSec / 60
                                            val s = durationSec % 60
                                            String.format("%d:%02d", m, s)
                                        } else {
                                            ""
                                        }
                                        results.add(Song(
                                            id = videoId,
                                            title = title,
                                            artist = artist,
                                            thumbnail = thumbnail,
                                            duration = durationStr,
                                            source = "youtube"
                                        ))
                                    }
                                }
                                if (results.isNotEmpty()) {
                                    Log.d("MusicApiService", "Successfully search YT from $instance: ${results.size} matches")
                                    return results
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicApiService", "Error searching YT from $instance, trying next...", e)
            }
        }
        return results
    }

    fun searchListenFree(query: String): List<Song> {
        val backendUrls = listOf(
            "https://jiosavan-api2.vercel.app/api",
            "https://music-backend-dup.vercel.app/api",
            "https://backend-music-henna.vercel.app/api"
        )
        val results = mutableListOf<Song>()
        for (baseUrl in backendUrls) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "$baseUrl/search/songs?query=$encodedQuery"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Origin", "https://listenfree.in")
                    .header("Referer", "https://listenfree.in/")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val success = json.optBoolean("success", false)
                            if (success) {
                                val dataObj = json.optJSONObject("data")
                                if (dataObj != null) {
                                    val resultsArray = dataObj.optJSONArray("results")
                                    if (resultsArray != null) {
                                        for (i in 0 until resultsArray.length()) {
                                            val songObj = resultsArray.getJSONObject(i)
                                            val id = songObj.optString("id")
                                            val name = songObj.optString("name")
                                            val artist = songObj.optString("primaryArtists").ifEmpty { "Unknown Artist" }
                                            var imgUrl = "https://listenfree.in/favicon.ico"
                                            val imgArray = songObj.optJSONArray("image")
                                            if (imgArray != null && imgArray.length() > 0) {
                                                imgUrl = imgArray.getJSONObject(imgArray.length() - 1).optString("link")
                                            }
                                            val durationSec = songObj.optInt("duration", 0)
                                            val durationStr = if (durationSec > 0) {
                                                val m = durationSec / 60
                                                val s = durationSec % 60
                                                String.format("%d:%02d", m, s)
                                            } else {
                                                ""
                                            }
                                            results.add(Song(
                                                id = id,
                                                title = name,
                                                artist = artist,
                                                thumbnail = imgUrl,
                                                duration = durationStr,
                                                source = "saavn"
                                            ))
                                        }
                                        if (results.isNotEmpty()) {
                                            Log.d("MusicApiService", "Successfully fetched ${results.size} songs from ListenFree search at $baseUrl")
                                            return results
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicApiService", "Error calling ListenFree search at $baseUrl", e)
            }
        }
        return results
    }

    fun getSaavnSongDownloadUrl(songId: String): String? {
        val backendUrls = listOf(
            "https://jiosavan-api2.vercel.app/api",
            "https://music-backend-dup.vercel.app/api",
            "https://backend-music-henna.vercel.app/api"
        )
        for (baseUrl in backendUrls) {
            try {
                val url = "$baseUrl/songs/$songId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Origin", "https://listenfree.in")
                    .header("Referer", "https://listenfree.in/")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val success = json.optBoolean("success", false)
                            if (success) {
                                val dataArray = json.optJSONArray("data")
                                if (dataArray != null && dataArray.length() > 0) {
                                    val songDetails = dataArray.getJSONObject(0)
                                    val downloadUrlArray = songDetails.optJSONArray("downloadUrl")
                                    if (downloadUrlArray != null && downloadUrlArray.length() > 0) {
                                        val streamUrl = downloadUrlArray.getJSONObject(downloadUrlArray.length() - 1).optString("link")
                                        if (streamUrl.isNotEmpty()) {
                                            Log.d("MusicApiService", "Found Saavn stream link from $baseUrl: $streamUrl")
                                            return streamUrl
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicApiService", "Error fetching Saavn details from $baseUrl", e)
            }
        }
        return null
    }

    fun getYoutubeInfoDownloadApiStream(videoId: String): String? {
        val url = "https://youtube-info-download-api.p.rapidapi.com/ajax/download.php?format=mp3&add_info=0&url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D$videoId&audio_quality=128&allow_extended_duration=false&no_merge=false&audio_language=en"
        val request = Request.Builder()
            .url(url)
            .header("x-rapidapi-key", "6448bb7ff1mshd973524f6873a42p14bfb8jsn800441a82f14")
            .header("x-rapidapi-host", "youtube-info-download-api.p.rapidapi.com")
            .header("Content-Type", "application/json")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val success = json.optBoolean("success", false)
                        if (success) {
                            var progressUrl = json.optString("progress_url")
                            val id = json.optString("id")
                            if (progressUrl.isEmpty() && id.isNotEmpty()) {
                                progressUrl = "https://p.savenow.to/api/progress?id=$id"
                            }
                            if (progressUrl.isNotEmpty()) {
                                Log.d("MusicApiService", "Polling progress URL: $progressUrl")
                                for (attempt in 1..5) {
                                    try {
                                        val progressRequest = Request.Builder()
                                            .url(progressUrl)
                                            .header("User-Agent", "Mozilla/5.0")
                                            .build()
                                        client.newCall(progressRequest).execute().use { progressResponse ->
                                            if (progressResponse.isSuccessful) {
                                                val progressBody = progressResponse.body?.string()
                                                if (!progressBody.isNullOrEmpty()) {
                                                    val progressJson = JSONObject(progressBody)
                                                    val downloadUrl = progressJson.optString("download_url")
                                                    if (downloadUrl.isNotEmpty()) {
                                                        Log.d("MusicApiService", "Found download URL: $downloadUrl")
                                                        return downloadUrl
                                                    }
                                                    val urlField = progressJson.optString("url")
                                                    if (urlField.isNotEmpty()) {
                                                        return urlField
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MusicApiService", "Progress polling error", e)
                                    }
                                    Thread.sleep(800)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicApiService", "Error calling youtube-info-download-api", e)
        }
        return null
    }

    fun getYoutubeMediaDownloaderStream(videoId: String): String? {
        val apiUrl = "https://youtube-media-downloader.p.rapidapi.com/v2/video/streams?videoId=$videoId"
        val request = Request.Builder()
            .url(apiUrl)
            .header("x-rapidapi-key", "6448bb7ff1mshd973524f6873a42p14bfb8jsn800441a82f14")
            .header("x-rapidapi-host", "youtube-media-downloader.p.rapidapi.com")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val streams = json.optJSONArray("streams")
                        if (streams != null && streams.length() > 0) {
                            var bestUrl: String? = null
                            for (i in 0 until streams.length()) {
                                val stream = streams.getJSONObject(i)
                                val streamUrl = stream.optString("url")
                                val audioOnly = stream.optBoolean("audioOnly", false)
                                val mimeType = stream.optString("mimeType", "")
                                if (audioOnly || mimeType.contains("audio/")) {
                                    bestUrl = streamUrl
                                    break
                                }
                                if (bestUrl == null) {
                                    bestUrl = streamUrl
                                }
                            }
                            if (!bestUrl.isNullOrEmpty()) {
                                Log.d("MusicApiService", "Found stream from youtube-media-downloader: $bestUrl")
                                return bestUrl
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicApiService", "Error calling youtube-media-downloader", e)
        }
        return null
    }

    fun getFallbackDownloadLink(videoId: String): String? {
        for (instance in pipedInstances) {
            try {
                val url = "$instance/streams/$videoId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val audioStreams = json.optJSONArray("audioStreams")
                            if (audioStreams != null && audioStreams.length() > 0) {
                                val streamUrl = audioStreams.getJSONObject(0).optString("url")
                                if (streamUrl.isNotEmpty()) {
                                    Log.d("MusicApiService", "Found stream on Piped: $streamUrl")
                                    return streamUrl
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicApiService", "Fallback streams fail from $instance", e)
            }
        }
        return null
    }

    fun getDownloadLink(id: String, source: String = "youtube"): String? {
        if (source == "saavn") {
            val saavnUrl = getSaavnSongDownloadUrl(id)
            if (!saavnUrl.isNullOrEmpty()) {
                return saavnUrl
            }
        }

        val premiumUrl = getYoutubeInfoDownloadApiStream(id)
        if (!premiumUrl.isNullOrEmpty()) {
            return premiumUrl
        }

        val backupUrl = getYoutubeMediaDownloaderStream(id)
        if (!backupUrl.isNullOrEmpty()) {
            return backupUrl
        }

        return getFallbackDownloadLink(id)
    }

    fun downloadTrackFile(context: Context, song: Song, streamUrl: String): String? {
        try {
            val request = Request.Builder()
                .url(streamUrl)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body ?: return null
                    val downloadsDir = File(context.filesDir, "downloads")
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }
                    val fileName = "${song.id}.mp3"
                    val file = File(downloadsDir, fileName)
                    body.byteStream().use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    return file.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.e("MusicApiService", "Error downloading song file", e)
        }
        return null
    }
}
