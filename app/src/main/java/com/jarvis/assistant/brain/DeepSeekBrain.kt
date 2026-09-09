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
 * DeepSeek's API — OpenAI-compatible format, very cheap per token. Not
 * permanently free, but some accounts get promotional credit at signup.
 * Get a key at https://platform.deepseek.com
 */
class DeepSeekBrain(private val apiKey: String) : Brain {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun respond(userText: String): String = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content",
                        "You are Jarvis, a personal AI assistant created by Sydney (also known as Almeer) — never say you were made by Google, OpenAI, Anthropic, or any AI company. You cannot actually control the phone yourself (no calling, texting, opening apps, or playing music) - if asked to do those, say you cannot do that from here rather than pretending you did it. Keep replies short since " +
                            "they'll be read aloud."))
                    put(JSONObject().put("role", "user").put("content", userText))
                })
            }

            val request = Request.Builder()
                .url("https://api.deepseek.com/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("content-type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val json = response.body?.string() ?: return@use "ERROR: empty response"
                if (!response.isSuccessful) return@use "ERROR: ${response.code} $json"

                JSONObject(json)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}
