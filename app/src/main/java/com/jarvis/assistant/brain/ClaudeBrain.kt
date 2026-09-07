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
 * Calls the Anthropic Messages API directly from the device.
 *
 * IMPORTANT: Shipping a raw API key inside an APK is not safe for a public app
 * (anyone can decompile it and steal your key/quota). For personal use on your
 * own phone this is fine. For anything you'd share with others, put this call
 * behind your own small backend instead and call that from the app.
 */
class ClaudeBrain(private val apiKey: String) : Brain {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun respond(userText: String): String = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", "claude-sonnet-4-6")
                put("max_tokens", 500)
                put("system", "You are Jarvis, a concise, helpful voice assistant running on the " +
                        "user's Android phone. Keep replies short and conversational since they will " +
                        "be read aloud by text-to-speech.")
                put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", userText)
                ))
            }

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val json = response.body?.string() ?: return@use "ERROR: empty response"
                if (!response.isSuccessful) {
                    return@use "ERROR: ${response.code} $json"
                }
                val parsed = JSONObject(json)
                val content = parsed.getJSONArray("content")
                val texts = StringBuilder()
                for (i in 0 until content.length()) {
                    val block = content.getJSONObject(i)
                    if (block.optString("type") == "text") {
                        texts.append(block.getString("text"))
                    }
                }
                texts.toString()
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}
