package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.jarvis.assistant.databinding.ActivityMainBinding
import com.jarvis.assistant.engine.JarvisEngine
import com.jarvis.assistant.engine.SystemStatsProvider
import com.jarvis.assistant.engine.WeatherProvider
import com.jarvis.assistant.service.WakeWordService
import com.jarvis.assistant.ui.ReactorState
import com.jarvis.assistant.ui.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity(), JarvisEngine.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var engine: JarvisEngine
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var accentColor: Int = 0

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (text != null) {
            onStateChanged(JarvisEngine.EngineState.THINKING)
            CoroutineScope(Dispatchers.Main).launch { engine.handleUtterance(text) }
        } else {
            onStateChanged(JarvisEngine.EngineState.IDLE)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        maybeAutoStartWakeWord()
        refreshWeather()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        engine = JarvisEngine(applicationContext, this)
        applyTheme()

        requestNeededPermissions()

        binding.reactorView.setOnClickListener { startListening() }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        wireTaskbarShortcuts()

        val wakeWordOn = engine.prefs.getBoolean("wake_word_enabled", true)
        binding.wakeWordToggle.isChecked = wakeWordOn
        binding.wakeWordToggle.setOnCheckedChangeListener { _, enabled ->
            engine.prefs.edit().putBoolean("wake_word_enabled", enabled).apply()
            setWakeWordServiceRunning(enabled)
        }

        engine.initTts {
            log("system online. tap the core to speak, or just say \"Jarvis\".")
            maybeGreetOnFirstLaunch()
            maybeAutoStartWakeWord()
        }

        refreshStatsGauges()
        refreshWeather()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        refreshStatsGauges()
    }

    private fun applyTheme() {
        val theme = ThemeManager.byId(engine.prefs.getString("theme_id", "matrix"))
        accentColor = theme.accent
        binding.titleLabel.setTextColor(accentColor)
        binding.logText.setTextColor(accentColor)
        binding.settingsButton.setTextColor(accentColor)
        binding.reactorView.setThemeAccent(accentColor)
    }

    private fun refreshStatsGauges() {
        val stats = SystemStatsProvider.read(applicationContext)
        binding.batteryGauge.setValue(stats.batteryPercent / 100f, "${stats.batteryPercent}%", "BATTERY", accentColor)
        binding.storageGauge.setValue(stats.storageUsedPercent / 100f, "${stats.storageUsedPercent}%", "STORAGE", accentColor)
    }

    private fun refreshWeather() {
        CoroutineScope(Dispatchers.Main).launch {
            val weather = WeatherProvider.fetchForCurrentLocation(applicationContext)
            binding.weatherLabel.text = if (weather != null) "${weather.tempC.toInt()}°C" else ""
        }
    }

    private fun wireTaskbarShortcuts() {
        binding.shortcutPhone.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL))
        }
        binding.shortcutCamera.setOnClickListener {
            startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE))
        }
        binding.shortcutBrowser.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
        }
        binding.shortcutMessages.setOnClickListener {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MESSAGING)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                log("! no messaging app found")
            }
        }
    }

    private fun maybeGreetOnFirstLaunch() {
        val alreadyGreeted = engine.prefs.getBoolean("has_greeted_once", false)
        if (!alreadyGreeted) {
            engine.prefs.edit().putBoolean("has_greeted_once", true).apply()
            engine.speak(
                "Hello. I'm Jarvis, online and ready. Just say Jarvis any time you need me, " +
                    "or tap the core to talk right now."
            )
        }
    }

    private fun maybeAutoStartWakeWord() {
        val wakeWordOn = engine.prefs.getBoolean("wake_word_enabled", true)
        if (wakeWordOn && hasRecordAudioPermission()) {
            setWakeWordServiceRunning(true)
        }
    }

    private fun setWakeWordServiceRunning(running: Boolean) {
        val serviceIntent = Intent(this, WakeWordService::class.java)
        if (running) startForegroundService(serviceIntent) else stopService(serviceIntent)
    }

    private fun hasRecordAudioPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestNeededPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val toRequest = needed.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) permissionLauncher.launch(toRequest.toTypedArray())
    }

    private fun startListening() {
        onStateChanged(JarvisEngine.EngineState.LISTENING)
        binding.reactorView.setAmplitude(0.8f)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
        }
        speechLauncher.launch(intent)
    }

    // JarvisEngine.Listener
    override fun onStateChanged(state: JarvisEngine.EngineState) {
        val reactorState = when (state) {
            JarvisEngine.EngineState.LISTENING -> ReactorState.LISTENING
            JarvisEngine.EngineState.THINKING -> ReactorState.THINKING
            JarvisEngine.EngineState.SPEAKING -> ReactorState.SPEAKING
            JarvisEngine.EngineState.ERROR -> ReactorState.ERROR
            JarvisEngine.EngineState.IDLE -> ReactorState.IDLE
        }
        val label = when (state) {
            JarvisEngine.EngineState.LISTENING -> "LISTENING"
            JarvisEngine.EngineState.THINKING -> "PROCESSING"
            JarvisEngine.EngineState.SPEAKING -> "SPEAKING"
            JarvisEngine.EngineState.ERROR -> "ERROR"
            JarvisEngine.EngineState.IDLE -> "STANDBY"
        }
        binding.reactorView.setState(reactorState)
        binding.statusLabel.text = label
        if (state == JarvisEngine.EngineState.SPEAKING) binding.reactorView.setAmplitude(0.6f)
        if (state == JarvisEngine.EngineState.ERROR) {
            binding.root.postDelayed({
                if (!isFinishing && !isDestroyed) onStateChanged(JarvisEngine.EngineState.IDLE)
            }, 1500)
        }
        if (state == JarvisEngine.EngineState.IDLE) refreshStatsGauges()
    }

    override fun onLog(line: String) {
        log(line)
    }

    private fun log(line: String) {
        val stamp = timeFmt.format(java.util.Date())
        binding.logText.append("\n[$stamp] $line")
        binding.logScroll.post { binding.logScroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        engine.shutdown()
        super.onDestroy()
    }
}
