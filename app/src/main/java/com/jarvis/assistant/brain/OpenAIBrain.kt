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
 * OpenAI's Chat Completions API. No meaningful free tier as of writing —
 * pay-as-you-go, billed per token. gpt-4o-mini is the cheapest capable model.
 */
class OpenAIBrain(private val apiKey: String) : Brain {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun respond(userText: String): String = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content",
                        "You are Jarvis, a personal AI assistant created by Sydney (also known as Almeer) — never say you were made by Google, OpenAI, Anthropic, or any AI company. Keep replies short since " +
                            "they'll be read aloud."))
                    put(JSONObject().put("role", "user").put("content", userText))
                })
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
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
