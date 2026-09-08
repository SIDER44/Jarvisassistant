package com.jarvis.assistant.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jarvis.assistant.actions.DeviceActions
import com.jarvis.assistant.brain.Brain
import com.jarvis.assistant.brain.ClaudeBrain
import com.jarvis.assistant.brain.DeepSeekBrain
import com.jarvis.assistant.brain.GeminiBrain
import com.jarvis.assistant.brain.LocalBrain
import com.jarvis.assistant.brain.OpenAIBrain
import com.jarvis.assistant.brain.PollinationsBrain

/**
 * All the "thinking" logic in one place: reads settings, picks a brain,
 * tries local device actions first, falls back to the LLM, and speaks the
 * result out loud. Used identically whether triggered by a tap in the UI
 * or by the wake word in the background.
 */
class JarvisEngine(private val appContext: Context, private val listener: Listener? = null) {

    interface Listener {
        fun onStateChanged(state: EngineState) {}
        fun onLog(line: String) {}
    }

    enum class EngineState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

    val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            appContext, "jarvis_secure_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private var brain: Brain = buildBrain()
    var tts: TextToSpeech? = null
        private set

    private var onSpeechDone: (() -> Unit)? = null

    private var isShutdown = false

    fun initTts(onReady: () -> Unit = {}) {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS && !isShutdown) onReady()
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (!isShutdown) listener?.onStateChanged(EngineState.SPEAKING)
                }
                override fun onDone(utteranceId: String?) {
                    if (isShutdown) return
                    listener?.onStateChanged(EngineState.IDLE)
                    onSpeechDone?.invoke()
                    onSpeechDone = null
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (isShutdown) return
                    listener?.onStateChanged(EngineState.IDLE)
                    onSpeechDone?.invoke()
                    onSpeechDone = null
                }
            })
        } catch (e: Exception) {
            listener?.onLog("! text-to-speech failed to initialize: ${e.message}")
        }
    }

    fun refreshBrain() {
        brain = buildBrain()
    }

    private fun buildBrain(): Brain {
        return when (prefs.getString("brain_backend", "claude")) {
            "local" -> LocalBrain(appContext)
            "gemini" -> GeminiBrain(prefs.getString("gemini_api_key", "") ?: "")
            "openai" -> OpenAIBrain(prefs.getString("openai_api_key", "") ?: "")
            "deepseek" -> DeepSeekBrain(prefs.getString("deepseek_api_key", "") ?: "")
            "pollinations" -> PollinationsBrain()
            else -> ClaudeBrain(prefs.getString("claude_api_key", "") ?: "")
        }
    }

    /**
     * Handles one full turn: user said [utterance] -> try a device action ->
     * otherwise ask the brain -> speak the result. Calls [onComplete] once
     * speech finishes (useful for resuming wake-word listening afterward).
     */
    suspend fun handleUtterance(utterance: String, onComplete: () -> Unit = {}) {
        try {
            listener?.onLog("you: $utterance")

            val localResult = try {
                DeviceActions.tryHandle(appContext, utterance)
            } catch (e: Exception) {
                listener?.onLog("! action failed: ${e.message}")
                null
            }
            if (localResult != null) {
                speak(localResult, onComplete)
                return
            }

            listener?.onStateChanged(EngineState.THINKING)
            val reply = try {
                brain.respond(utterance)
            } catch (e: Exception) {
                "ERROR: ${e.message}"
            }
            val isError = reply.startsWith("ERROR:")
            listener?.onLog(if (isError) "! $reply" else "jarvis: $reply")
            if (isError) {
                listener?.onStateChanged(EngineState.ERROR)
                onComplete()
                return
            }
            speak(reply, onComplete)
        } catch (e: Exception) {
            // Absolute last resort — never let a turn crash the host app/service.
            listener?.onLog("! unexpected error: ${e.message}")
            listener?.onStateChanged(EngineState.ERROR)
            onComplete()
        }
    }

    fun speak(text: String, onComplete: () -> Unit = {}) {
        try {
            onSpeechDone = onComplete
            val params = Bundle()
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "jarvis_utterance")
            if (result == null || result == TextToSpeech.ERROR) {
                // TTS engine not ready/available on this device — don't hang forever
                listener?.onLog("! text-to-speech unavailable, showing reply as text only")
                listener?.onStateChanged(EngineState.IDLE)
                onSpeechDone?.invoke()
                onSpeechDone = null
            }
        } catch (e: Exception) {
            listener?.onLog("! speech failed: ${e.message}")
            listener?.onStateChanged(EngineState.IDLE)
            onComplete()
        }
    }

    fun shutdown() {
        isShutdown = true
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // ignore — we're tearing down anyway
        }
    }
}
