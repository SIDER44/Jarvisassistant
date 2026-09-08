package com.jarvis.assistant.brain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Pollinations.ai — a free, open-source AI platform that requires no API key
 * for its basic text endpoint. No signup, no billing, no key management.
 * Quality/reliability isn't guaranteed the way a paid API's is (it's a free
 * community service), but it costs nothing and needs zero setup.
 * https://pollinations.ai
 */
class PollinationsBrain : Brain {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun respond(userText: String): String = withContext(Dispatchers.IO) {
        try {
            val prompt = "You are Jarvis, a personal AI assistant created by Sydney (also known as Almeer) — never say you were made by Google, OpenAI, Anthropic, or any AI company. Keep replies short " +
                "since they'll be read aloud.\n\nUser: $userText"
            val encoded = URLEncoder.encode(prompt, "UTF-8")
            val url = "https://text.pollinations.ai/$encoded"

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()?.trim()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return@use "ERROR: ${response.code} Pollinations may be temporarily unavailable " +
                        "or changed its API — this is a free community service without guaranteed uptime."
                }
                body
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}
