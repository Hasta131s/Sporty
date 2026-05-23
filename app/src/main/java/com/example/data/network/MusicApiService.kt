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

    // 2. YouTube InnerTube Search API
    fun searchYouTube(query: String): List<Song> {
        val url = "https://www.youtube.com/youtubei/v1/search?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        
        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20210721.00.00")
                })
            })
            put("query", query)
        }

        val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()

        val results = mutableListOf<Song>()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return emptyList()
                    val jsonResponse = JSONObject(body)
                    
                    // Traverse YouTube InnerTube Response json
                    val sectionList = jsonResponse
                        .optJSONObject("contents")
                        ?.optJSONObject("twoColumnSearchResultsRenderer")
                        ?.optJSONObject("primaryContents")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents")

                    val itemSection = sectionList?.optJSONObject(0)
                        ?.optJSONObject("itemSectionRenderer")
                        ?.optJSONArray("contents")

                    if (itemSection != null) {
                        for (i in 0 until itemSection.length()) {
                            val videoRenderer = itemSection.optJSONObject(i)?.optJSONObject("videoRenderer") ?: continue
                            val videoId = videoRenderer.optString("videoId") ?: continue
                            
                            var title = "Unknown Title"
                            val titleRuns = videoRenderer.optJSONObject("title")?.optJSONArray("runs")
                            if (titleRuns != null && titleRuns.length() > 0) {
                                title = titleRuns.getJSONObject(0).optString("text")
                            } else {
                                val simpleText = videoRenderer.optJSONObject("title")?.optString("simpleText")
                                if (!simpleText.isNullOrEmpty()) title = simpleText
                            }

                            var artist = "Unknown Artist"
                            val ownerRuns = videoRenderer.optJSONObject("ownerText")?.optJSONArray("runs")
                            if (ownerRuns != null && ownerRuns.length() > 0) {
                                artist = ownerRuns.getJSONObject(0).optString("text")
                            }

                            var thumbnail = ""
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
            }
        } catch (e: Exception) {
            Log.e("MusicApiService", "Error searching InnerTube", e)
        }

        // HTML Scraping Fallback
        if (results.isEmpty()) {
            try {
                val searchUrl = "https://www.youtube.com/results?search_query=${URLEncoder.encode(query)}"
                val fallbackRequest = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                client.newCall(fallbackRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        
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
            } catch (e: Exception) {
                Log.e("MusicApiService", "Error searching YouTube fallback", e)
            }
        }

        return results
    }

    // 3. YouTube Session Download API (RapidAPI parsed from PHP)
    fun getDownloadLink(videoId: String): String? {
        val apiUrl = "https://yt-api.p.rapidapi.com/dl?id=$videoId"
        val request = Request.Builder()
            .url(apiUrl)
            .header("X-RapidAPI-Key", "a4045ed1d4msh6e87c936fa34978p1193c7jsn50ce376054f4")
            .header("X-RapidAPI-Host", "yt-api.p.rapidapi.com")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    return json.optString("link")
                }
            }
        } catch (e: Exception) {
            Log.e("MusicApiService", "Error calling RapidAPI downloader", e)
        }
        return null
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
