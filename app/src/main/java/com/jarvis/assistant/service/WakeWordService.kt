package com.jarvis.assistant.service

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R

/**
 * Runs continuously in the background listening for "Hey Jarvis".
 * Requires a foreground notification per Android 8+ background-service rules,
 * and battery-optimization exemption to avoid being killed on Android 13.
 *
 * SETUP REQUIRED:
 * 1. Get a free AccessKey from https://console.picovoice.ai
 * 2. Train a custom "Hey Jarvis" wake-word .ppn file at the same console
 *    (Porcupine ships built-in words like "Jarvis" you can also use as-is)
 * 3. Put the .ppn file in app/src/main/assets/hey_jarvis.ppn
 * 4. Fill in ACCESS_KEY below (or better, load it from EncryptedSharedPreferences)
 */
class WakeWordService : Service() {

    private var porcupineManager: PorcupineManager? = null
    private val CHANNEL_ID = "jarvis_wake_word"
    private val NOTIF_ID = 1001

    companion object {
        const val ACCESS_KEY = "YOUR_PICOVOICE_ACCESS_KEY"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        startListening()
    }

    private fun startListening() {
        try {
            val callback = PorcupineManagerCallback { _ ->
                // Wake word detected — launch the assistant UI to start listening for a command
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("TRIGGERED_BY_WAKE_WORD", true)
                }
                startActivity(intent)
            }

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setKeywordPath("hey_jarvis.ppn") // relative to assets/
                .setSensitivity(0.6f)
                .build(applicationContext, callback)

            porcupineManager?.start()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis is listening")
            .setContentText("Say \"Hey Jarvis\" to activate")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Jarvis Wake Word", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        porcupineManager?.stop()
        porcupineManager?.delete()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
