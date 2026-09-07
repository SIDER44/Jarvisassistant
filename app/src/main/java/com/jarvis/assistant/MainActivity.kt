package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jarvis.assistant.actions.DeviceActions
import com.jarvis.assistant.brain.Brain
import com.jarvis.assistant.brain.ClaudeBrain
import com.jarvis.assistant.brain.LocalBrain
import com.jarvis.assistant.databinding.ActivityMainBinding
import com.jarvis.assistant.service.WakeWordService
import com.jarvis.assistant.ui.ReactorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tts: TextToSpeech
    private lateinit var brain: Brain
    private lateinit var prefs: android.content.SharedPreferences
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (text != null) {
            handleUserUtterance(text)
        } else {
            setState(ReactorState.IDLE, "STANDBY")
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        prefs = EncryptedSharedPreferences.create(
            this, "jarvis_secure_prefs", masterKey, applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        tts = TextToSpeech(this) { }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread { setState(ReactorState.SPEAKING, "SPEAKING") }
            }
            override fun onDone(utteranceId: String?) {
                runOnUiThread { setState(ReactorState.IDLE, "STANDBY") }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread { setState(ReactorState.IDLE, "STANDBY") }
            }
        })

        requestNeededPermissions()
        rebuildBrain()

        binding.reactorView.setOnClickListener { startListening() }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.wakeWordToggle.isChecked = prefs.getBoolean("wake_word_enabled", false)
        binding.wakeWordToggle.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean("wake_word_enabled", enabled).apply()
            val serviceIntent = Intent(this, WakeWordService::class.java)
            if (enabled) startForegroundService(serviceIntent) else stopService(serviceIntent)
        }

        log("system online. tap the core to speak.")

        if (intent.getBooleanExtra("TRIGGERED_BY_WAKE_WORD", false)) {
            startListening()
        }
    }

    private fun rebuildBrain() {
        val backend = prefs.getString("brain_backend", "claude")
        brain = if (backend == "local") LocalBrain(applicationContext)
        else ClaudeBrain(prefs.getString("claude_api_key", "") ?: "")
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val toRequest = needed.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) permissionLauncher.launch(toRequest.toTypedArray())
    }

    private fun startListening() {
        setState(ReactorState.LISTENING, "LISTENING")
        binding.reactorView.setAmplitude(0.8f)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
        }
        speechLauncher.launch(intent)
    }

    private fun handleUserUtterance(text: String) {
        log("you: $text")

        val localResult = DeviceActions.tryHandle(this, text)
        if (localResult != null) {
            respondAndSpeak(localResult)
            return
        }

        setState(ReactorState.THINKING, "PROCESSING")
        CoroutineScope(Dispatchers.Main).launch {
            val reply = brain.respond(text)
            respondAndSpeak(reply)
        }
    }

    private fun respondAndSpeak(reply: String) {
        val isError = reply.startsWith("ERROR:")
        log(if (isError) "! $reply" else "jarvis: $reply")
        if (isError) {
            setState(ReactorState.ERROR, "ERROR")
            binding.root.postDelayed({ setState(ReactorState.IDLE, "STANDBY") }, 1500)
            return
        }
        binding.reactorView.setAmplitude(0.6f)
        val params = Bundle()
        tts.speak(reply, TextToSpeech.QUEUE_FLUSH, params, "jarvis_reply")
    }

    private fun setState(state: ReactorState, label: String) {
        binding.reactorView.setState(state)
        binding.statusLabel.text = label
    }

    private fun log(line: String) {
        val stamp = timeFmt.format(java.util.Date())
        binding.logText.append("\n[$stamp] $line")
        binding.logScroll.post { binding.logScroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
