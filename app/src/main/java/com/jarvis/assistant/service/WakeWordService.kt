package com.jarvis.assistant.service

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.engine.JarvisEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fully hands-free background listener. Flow:
 *   1. Porcupine listens continuously for "Jarvis" / "Hey Jarvis"
 *   2. On detection, pause Porcupine (mic can only be used by one listener
 *      at a time) and start Android's SpeechRecognizer to capture the command
 *   3. Send the recognized text through the shared JarvisEngine (same brain
 *      + device actions + TTS as the tap-to-talk flow)
 *   4. Speak the reply, then resume Porcupine to listen for the next "Jarvis"
 *
 * No activity is launched and no recognizer UI is shown — everything happens
 * silently in this background service, matching a real "always listening"
 * assistant.
 *
 * SETUP REQUIRED:
 * 1. Get a free AccessKey from https://console.picovoice.ai
 * 2. Use the built-in "Jarvis" keyword (ships with the Porcupine SDK), or
 *    train a custom "Hey Jarvis" .ppn file and place it at
 *    app/src/main/assets/hey_jarvis.ppn (see README)
 * 3. Fill in ACCESS_KEY below
 */
class WakeWordService : Service(), JarvisEngine.Listener {

    private var porcupineManager: PorcupineManager? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var engine: JarvisEngine
    private val mainHandler = Handler(Looper.getMainLooper())

    private val CHANNEL_ID = "jarvis_wake_word"
    private val NOTIF_ID = 1001

    companion object {
        const val ACCESS_KEY = "YOUR_PICOVOICE_ACCESS_KEY"
        // Built-in keyword name from the Porcupine SDK's resource bundle.
        // Swap for BuiltInKeyword.JARVIS if using the SDK's enum-based API,
        // or point at your own trained .ppn asset instead (see setKeywordPath).
    }

    override fun onCreate() {
        super.onCreate()
        engine = JarvisEngine(applicationContext, this)
        engine.initTts()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Say \"Jarvis\" any time"))
        startWakeWordListening()
    }

    private fun startWakeWordListening() {
        try {
            val callback = PorcupineManagerCallback { _ -> onWakeWordDetected() }
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setKeywordPath("hey_jarvis.ppn") // relative to assets/; swap to a built-in keyword if preferred
                .setSensitivity(0.6f)
                .build(applicationContext, callback)
            porcupineManager?.start()
        } catch (e: Exception) {
            onLog("wake word engine failed to start: ${e.message}")
            stopSelf()
        }
    }

    private fun onWakeWordDetected() {
        mainHandler.post {
            updateNotification("Listening...")
            onStateChanged(JarvisEngine.EngineState.LISTENING)
            porcupineManager?.stop() // free the mic for speech recognition
            startCommandCapture()
        }
    }

    private fun startCommandCapture() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onLog("speech recognition not available on this device")
            resumeWakeWordListening()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (text.isNullOrBlank()) {
                        resumeWakeWordListening()
                    } else {
                        handleCommand(text)
                    }
                }
                override fun onError(error: Int) {
                    onLog("didn't catch that (error $error)")
                    resumeWakeWordListening()
                }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            startListening(intent)
        }
    }

    private fun handleCommand(text: String) {
        onStateChanged(JarvisEngine.EngineState.THINKING)
        CoroutineScope(Dispatchers.Main).launch {
            engine.handleUtterance(text) {
                resumeWakeWordListening()
            }
        }
    }

    private fun resumeWakeWordListening() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        onStateChanged(JarvisEngine.EngineState.IDLE)
        updateNotification("Say \"Jarvis\" any time")
        porcupineManager?.start()
    }

    // JarvisEngine.Listener callbacks — surface state/log via the notification and system log
    override fun onStateChanged(state: JarvisEngine.EngineState) {
        val label = when (state) {
            JarvisEngine.EngineState.LISTENING -> "Listening..."
            JarvisEngine.EngineState.THINKING -> "Thinking..."
            JarvisEngine.EngineState.SPEAKING -> "Speaking..."
            JarvisEngine.EngineState.ERROR -> "Something went wrong"
            JarvisEngine.EngineState.IDLE -> "Say \"Jarvis\" any time"
        }
        mainHandler.post { updateNotification(label) }
    }

    override fun onLog(line: String) {
        android.util.Log.d("Jarvis", line)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
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
        speechRecognizer?.destroy()
        engine.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
