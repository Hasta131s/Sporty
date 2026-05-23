package com.example.data.network

import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getSongLyricsAndBio(title: String, artist: String): Pair<String, String> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiClient", "Gemini API Key is not set or placeholder!")
            return Pair(
                "Could not fetch lyrics because the Gemini API key was not found. Please add your GEMINI_API_KEY in the AI Studio Secrets panel of the app settings.",
                "Biography cannot be loaded due to a missing AI config key."
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        
        val prompt = """
            Provide the actual song lyrics and a brief biography/background info (about 2-3 sentences) for the music song '$title' by '$artist'.
            Please respond ONLY with a valid, clean JSON object containing exactly two keys:
            - "lyrics": A string containing the complete song lyrics formatted with line breaks, nicely readable.
            - "bio": A string containing a brief background biography/trivia about the song and artist.
            Do not include any markdown formatting wrappers like '```json' or similar around the response. Return raw JSON text.
        """.trimIndent()

        try {
            val part = JSONObject().put("text", prompt)
            val content = JSONObject().put("parts", JSONArray().put(part))
            val payload = JSONObject().put("contents", JSONArray().put(content))

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return Pair("No lyrics returned.", "No background biography details found.")
                    val jsonResponse = JSONObject(body)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val generatedText = candidates.getJSONObject(0)
                            .optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.getJSONObject(0)
                            ?.optString("text") ?: ""

                        // Clean any markdown formatting wrappers such as ```json ... ```
                        var cleanedText = generatedText.trim()
                        if (cleanedText.startsWith("```json")) {
                            cleanedText = cleanedText.substringAfter("```json")
                        } else if (cleanedText.startsWith("```")) {
                            cleanedText = cleanedText.substringAfter("```")
                        }
                        if (cleanedText.contains("```")) {
                            cleanedText = cleanedText.substringBefore("```")
                        }
                        cleanedText = cleanedText.trim()

                        val resultJson = JSONObject(cleanedText)
                        val lyrics = resultJson.optString("lyrics", "Lyrics not found.")
                        val bio = resultJson.optString("bio", "Biography background is not found.")
                        return Pair(lyrics, bio)
                    }
                } else {
                    Log.e("GeminiClient", "API error code: ${response.code} - ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Error calling Gemini API", e)
        }

        return Pair(
            "Could not fetch lyrics for '$title' by '$artist'. You can still listen to the music directly!",
            "Biography background is temporarily unavailable."
        )
    }
}
