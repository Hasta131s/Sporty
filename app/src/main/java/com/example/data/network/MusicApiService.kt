package com.example.data.network

import android.content.Context
import android.util.Log
import com.example.data.database.Song
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object MusicApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    // 1. Google/YouTube Suggest Queries
    fun getSuggestions(query: String): List<String> {
        val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=${URLEncoder.encode(query)}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return emptyList()
                    val jsonArray = JSONArray(body)
                    if (jsonArray.length() > 1) {
                        val suggestionsArray = jsonArray.getJSONArray(1)
                        val suggestions = mutableListOf<String>()
                        for (i in 0 until minOf(suggestionsArray.length(), 8)) {
                            suggestions.add(suggestionsArray.getString(i))
                        }
                        return suggestions
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicApiService", "Error getting suggestions", e)
        }
        return emptyList()
    }

    // Helper to parse YouTube Search JSON structure
    fun parseSearchJson(root: JSONObject): List<Song> {
        val results = mutableListOf<Song>()
        try {
            var contents = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (contents == null) {
                contents = root.optJSONObject("contents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
            }

            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val itemSection = contents.optJSONObject(i)
                        ?.optJSONObject("itemSectionRenderer")
                        ?.optJSONArray("contents") ?: continue

                    for (j in 0 until itemSection.length()) {
                        val videoRenderer = itemSection.optJSONObject(j)?.optJSONObject("videoRenderer") ?: continue
                        val videoId = videoRenderer.optString("videoId") ?: continue
                        if (videoId.isEmpty() || videoId == "null") continue

                        var title = "Unknown Title"
                        val titleRuns = videoRenderer.optJSONObject("title")?.optJSONArray("runs")
                        if (titleRuns != null && titleRuns.length() > 0) {
                            title = titleRuns.getJSONObject(0).optString("text")
                        } else {
                            val simpleText = videoRenderer.optJSONObject("title")?.optString("simpleText")
                            if (!simpleText.isNullOrEmpty()) title = simpleText
                        }

                        var artist = "YouTube Music"
                        val ownerRuns = videoRenderer.optJSONObject("ownerText")?.optJSONArray("runs")
                        if (ownerRuns != null && ownerRuns.length() > 0) {
                            artist = ownerRuns.getJSONObject(0).optString("text")
                        } else {
                            val shortRuns = videoRenderer.optJSONObject("shortBylineText")?.optJSONArray("runs")
                            if (shortRuns != null && shortRuns.length() > 0) {
                                artist = shortRuns.getJSONObject(0).optString("text")
                            }
                        }

                        var thumbnail = "https://img.youtube.com/vi/$videoId/mqdefault.jpg"
                        val thumbnails = videoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        if (thumbnails != null && thumbnails.length() > 0) {
                            thumbnail = thumbnails.getJSONObject(0).optString("url")
                            thumbnail = thumbnail.replace("hqdefault", "mqdefault")
                        }

                        var duration = ""
                        val lengthText = videoRenderer.optJSONObject("lengthText")?.optString("simpleText")
                        if (!lengthText.isNullOrEmpty()) {
                            duration = lengthText
                        }

                        results.add(Song(
                            id = videoId,
                            title = title,
                            artist = artist,
                            thumbnail = thumbnail,
                            duration = duration,
                            source = "youtube"
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicApiService", "Error parsing search JSON", e)
        }
        return results
    }

    // 2. YouTube InnerTube Search API
    fun searchYouTube(query: String): List<Song> {
        val url = "https://www.youtube.com/youtubei/v1/search?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        
        // Using ANDROID client context to simulate official mobile client which bypasses typical restrictions
        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.05.35")
                    put("hl", "tr")
                    put("gl", "TR")
                })
            })
            put("query", query)
        }

        val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()

        val results = mutableListOf<Song>()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotEmpty()) {
                        val jsonResponse = JSONObject(body)
                        val parsed = parseSearchJson(jsonResponse)
                        if (parsed.isNotEmpty()) {
                            results.addAll(parsed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicApiService", "Error searching InnerTube", e)
        }

        // HTML Scraping Fallback (searches via results page and extracts ytInitialData state JSON)
        if (results.isEmpty()) {
            try {
                val searchUrl = "https://www.youtube.com/results?search_query=${URLEncoder.encode(query)}&sp=EgIQAQ%253D%253D"
                val fallbackRequest = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "tr,en;q=0.9")
                    .build()
                client.newCall(fallbackRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        val prefix = "var ytInitialData = "
                        if (html.contains(prefix)) {
                            val startIdx = html.indexOf(prefix) + prefix.length
                            var endIdx = html.indexOf("};", startIdx)
                            if (endIdx != -1) {
                                endIdx += 1 // Include ending curly brace
                                val jsonStr = html.substring(startIdx, endIdx)
                                val initialDataObj = JSONObject(jsonStr)
                                val parsed = parseSearchJson(initialDataObj)
                                if (parsed.isNotEmpty()) {
                                    results.addAll(parsed)
                                }
                            }
                        }

                        // Last resort regex matches if ytInitialData fails
                        if (results.isEmpty()) {
                            val videoIds = mutableListOf<String>()
                            val videoIdMatcher = java.util.regex.Pattern.compile("\"videoId\":\"([^\"]{11})\"")
                            val idMatcher = videoIdMatcher.matcher(html)
                            while (idMatcher.find() && videoIds.size < 20) {
                                val id = idMatcher.group(1)
                                if (id != null && !videoIds.contains(id)) videoIds.add(id)
                            }

                            val titles = mutableListOf<String>()
                            val titleMatcher = java.util.regex.Pattern.compile("\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+?)\"\\}\\]")
                            val tMatcher = titleMatcher.matcher(html)
                            while (tMatcher.find() && titles.size < videoIds.size) {
                                titles.add(tMatcher.group(1) ?: "Unknown")
                            }

                            for (i in 0 until minOf(videoIds.size, titles.size)) {
                                results.add(Song(
                                    id = videoIds[i],
                                    title = titles[i],
                                    artist = "YouTube Music",
                                    thumbnail = "https://img.youtube.com/vi/${videoIds[i]}/mqdefault.jpg",
                                    duration = "",
                                    source = "youtube"
                                ))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicApiService", "Error searching YouTube HTML fallback", e)
            }
        }

        return results
    }

    // 3. YouTube Session Download API (RapidAPI parsed from PHP)
    fun getFallbackDownloadLink(videoId: String): String? {
        val instances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.yt",
            "https://pipedapi.lvkno.in",
            "https://pipedapi.tokhmi.xyz"
        )
        for (instance in instances) {
            try {
                val url = "$instance/streams/$videoId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val audioStreams = json.optJSONArray("audioStreams")
                            if (audioStreams != null && audioStreams.length() > 0) {
                                // Try to find an mp4 audio stream or take the first one
                                var bestUrl: String? = null
                                for (i in 0 until audioStreams.length()) {
                                    val stream = audioStreams.getJSONObject(i)
                                    val streamUrl = stream.optString("url")
                                    val mimeType = stream.optString("mimeType") ?: ""
                                    if (mimeType.contains("audio/mp4")) {
                                        bestUrl = streamUrl
                                        break
                                    }
                                    if (bestUrl == null) {
                                        bestUrl = streamUrl
                                    }
                                }
                                if (!bestUrl.isNullOrEmpty()) {
                                    Log.d("MusicApiService", "Found fallback Piped stream from $instance: $bestUrl")
                                    return bestUrl
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicApiService", "Error calling Piped instance $instance", e)
            }
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
                                for (attempt in 1..10) {
                                    try {
                                        val progressRequest = Request.Builder()
                                            .url(progressUrl)
                                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                            .build()
                                        client.newCall(progressRequest).execute().use { progressResponse ->
                                            if (progressResponse.isSuccessful) {
                                                val progressBody = progressResponse.body?.string()
                                                if (!progressBody.isNullOrEmpty()) {
                                                    val progressJson = JSONObject(progressBody)
                                                    val downloadUrl = progressJson.optString("download_url")
                                                    if (downloadUrl.isNotEmpty()) {
                                                        Log.d("MusicApiService", "Found download URL in progress: $downloadUrl")
                                                        return downloadUrl
                                                    }
                                                    val urlField = progressJson.optString("url")
                                                    if (urlField.isNotEmpty()) {
                                                        Log.d("MusicApiService", "Found url in progress: $urlField")
                                                        return urlField
                                                    }
                                                    val progressValue = progressJson.optInt("progress", 0)
                                                    Log.d("MusicApiService", "Progress attempt $attempt: value in percent = $progressValue")
                                                    if (progressValue >= 1000) {
                                                        val finalUrl = progressJson.optString("download_url").ifEmpty { progressJson.optString("url") }
                                                        if (finalUrl.isNotEmpty()) return finalUrl
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MusicApiService", "Error polling progress URL", e)
                                    }
                                    Thread.sleep(1000)
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
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
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
                Log.e("MusicApiService", "Error fetching Saavn details from $baseUrl, trying next...", e)
            }
        }
        return null
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
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "$baseUrl/search/songs?query=$encodedQuery"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
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
                Log.e("MusicApiService", "Error calling ListenFree search at $baseUrl, trying next...", e)
            }
        }
        return results
    }

    fun getDownloadLink(id: String, source: String = "youtube"): String? {
        if (source == "saavn") {
            val saavnUrl = getSaavnSongDownloadUrl(id)
            if (!saavnUrl.isNullOrEmpty()) {
                return saavnUrl
            }
        }

        Log.d("MusicApiService", "Trying premium youtube-info-download-api stream link for $id.")
        val premiumUrl = getYoutubeInfoDownloadApiStream(id)
        if (!premiumUrl.isNullOrEmpty()) {
            return premiumUrl
        }

        val apiUrl = "https://youtube-mp36.p.rapidapi.com/dl?id=$id"
        val request = Request.Builder()
            .url(apiUrl)
            .header("x-rapidapi-key", "6448bb7ff1mshd973524f6873a42p14bfb8jsn800441a82f14")
            .header("x-rapidapi-host", "youtube-mp36.p.rapidapi.com")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val status = json.optString("status")
                        if (status == "ok" || status == "success" || json.has("link")) {
                            val link = json.optString("link")
                            if (!link.isNullOrEmpty()) {
                                return link
                            }
                        }
                    }
                } else {
                    Log.e("MusicApiService", "RapidAPI Fail: code = ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("MusicApiService", "Error calling RapidAPI youtube-mp36, trying fallback...", e)
        }
        
        Log.d("MusicApiService", "Primary download link failed. Invoking youtube-media-downloader stream backup.")
        val backupLink = getYoutubeMediaDownloaderStream(id)
        if (!backupLink.isNullOrEmpty()) {
            return backupLink
        }
        
        Log.d("MusicApiService", "backupLink failed. Invoking Piped fallback service.")
        return getFallbackDownloadLink(id)
    }

    // Download the MP3 file to local storage inside Context Files
    fun downloadSongFile(context: Context, videoId: String, downloadUrl: String, onProgress: (Float) -> Unit): File? {
        val request = Request.Builder()
            .url(downloadUrl)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body ?: return null
                    val contentLength = body.contentLength()
                    
                    val downloadsDir = File(context.filesDir, "downloads")
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    
                    val filename = "${System.currentTimeMillis()}_${videoId}.mp3"
                    val file = File(downloadsDir, filename)
                    
                    var bytesCopied: Long = 0
                    val buffer = ByteArray(1024 * 8)
                    var bytes = body.byteStream().read(buffer)
                    
                    FileOutputStream(file).use { out ->
                        while (bytes >= 0) {
                            out.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            if (contentLength > 0) {
                                onProgress(bytesCopied.toFloat() / contentLength.toFloat())
                            }
                            bytes = body.byteStream().read(buffer)
                        }
                    }
                    return file
                }
            }
        } catch (e: Exception) {
            Log.e("MusicApiService", "Error downloading actual MP3", e)
        }
        return null
    }

    // 4. API to search Genius for song URL
    fun searchGeniusSongUrl(query: String): String? {
        val url = "https://genius.com/api/search/multi?q=${URLEncoder.encode(query)}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    val sections = json.optJSONObject("response")?.optJSONArray("sections")
                    if (sections != null) {
                        for (i in 0 until sections.length()) {
                            val section = sections.getJSONObject(i)
                            if (section.optString("type") == "song") {
                                val hits = section.optJSONArray("hits")
                                if (hits != null && hits.length() > 0) {
                                    return hits.getJSONObject(0).optJSONObject("result")?.optString("url")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicApiService", "Error calling Genius API", e)
        }
        return null
    }

    // MD5 Helper for Lyrics cache
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

// Simple URL Encoder because java.net.URLEncoder exists
object URLEncoder {
    fun encode(s: String): String {
        return java.net.URLEncoder.encode(s, "UTF-8")
    }
}
