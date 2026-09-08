package com.jarvis.assistant.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(val versionTag: String, val downloadUrl: String)

/**
 * Checks the GitHub repo's Releases for a newer build than the one currently
 * installed, using the version code baked in at build time (see
 * app/build.gradle.kts — versionCode is set from the CI run number).
 *
 * IMPORTANT: replace REPO below with your actual "username/reponame".
 */
object UpdateChecker {

    private const val REPO = "SIDER44/JarvisAssistant" // <-- change if your repo differs

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Returns update info if a newer build exists, or null if already up to date / on error. */
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .addHeader("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use null
                if (!response.isSuccessful) return@use null

                val json = JSONObject(body)
                val tag = json.getString("tag_name") // e.g. "build-42"
                val remoteBuildNumber = tag.substringAfterLast("-").toIntOrNull() ?: return@use null
                if (remoteBuildNumber <= currentVersionCode) return@use null

                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                if (apkUrl == null) return@use null

                UpdateInfo(tag, apkUrl)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads the APK to cache and launches the system package installer. */
    suspend fun downloadAndInstall(context: Context, update: UpdateInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(update.downloadUrl).build()
            val outFile = File(context.cacheDir, "jarvis-update.apk")

            client.newCall(request).execute().use { response ->
                val body = response.body ?: return@withContext false
                outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            withContext(Dispatchers.Main) {
                context.startActivity(installIntent)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
