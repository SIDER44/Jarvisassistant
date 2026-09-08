package com.jarvis.assistant.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WeatherInfo(val tempC: Double, val code: Int)

/**
 * Free weather via Open-Meteo (https://open-meteo.com) — no API key needed.
 * Uses the device's last-known location (coarse permission). Falls back to
 * null if location or network isn't available, so callers should handle that
 * gracefully (hide the weather widget rather than error out).
 */
object WeatherProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchForCurrentLocation(context: Context): WeatherInfo? = withContext(Dispatchers.IO) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) return@withContext null

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = locationManager.getProviders(true)
            var lat: Double? = null
            var lon: Double? = null
            for (provider in providers) {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                lat = loc.latitude
                lon = loc.longitude
                break
            }
            if (lat == null || lon == null) return@withContext null

            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use null
                if (!response.isSuccessful) return@use null
                val json = JSONObject(body).getJSONObject("current_weather")
                WeatherInfo(json.getDouble("temperature"), json.getInt("weathercode"))
            }
        } catch (e: Exception) {
            null
        }
    }
}
