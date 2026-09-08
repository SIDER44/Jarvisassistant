package com.jarvis.assistant.brain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Google's Gemini API — currently the most generous genuinely-free tier of
 * the major providers. Get a free key at https://aistudio.google.com/apikey
 */
class GeminiBrain(private val apiKey: String) : Brain {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun respond(userText: String): String = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text",
                            "You are Jarvis, a concise voice assistant. Keep replies short " +
                                "since they'll be read aloud.\n\nUser: $userText")
                    ))
                ))
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                "gemini-3.6-flash:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .addHeader("content-type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val json = response.body?.string() ?: return@use "ERROR: empty response"
                if (!response.isSuccessful) return@use "ERROR: ${response.code} $json"

                val parsed = JSONObject(json)
                parsed.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}
