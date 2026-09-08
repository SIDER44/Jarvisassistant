package com.jarvis.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jarvis.assistant.databinding.ActivitySettingsBinding
import com.jarvis.assistant.engine.UpdateChecker
import com.jarvis.assistant.service.WakeWordService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            applicationContext, "jarvis_secure_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // Load existing keys
        binding.geminiKeyInput.setText(prefs.getString("gemini_api_key", ""))
        binding.openaiKeyInput.setText(prefs.getString("openai_api_key", ""))
        binding.deepseekKeyInput.setText(prefs.getString("deepseek_api_key", ""))

        // Select the currently active backend radio button
        when (prefs.getString("brain_backend", "pollinations")) {
            "gemini" -> binding.radioGemini.isChecked = true
            "openai" -> binding.radioOpenAI.isChecked = true
            "deepseek" -> binding.radioDeepSeek.isChecked = true
            "local" -> binding.radioLocal.isChecked = true
            else -> binding.radioPollinations.isChecked = true
        }

        when (prefs.getString("theme_id", "matrix")) {
            "iron_man" -> binding.themeIronMan.isChecked = true
            "ultron" -> binding.themeUltron.isChecked = true
            "vibranium" -> binding.themeVibranium.isChecked = true
            "gold_titanium" -> binding.themeGold.isChecked = true
            else -> binding.themeMatrix.isChecked = true
        }

        binding.saveButton.setOnClickListener {
            val backend = when (binding.backendGroup.checkedRadioButtonId) {
                binding.radioGemini.id -> "gemini"
                binding.radioOpenAI.id -> "openai"
                binding.radioDeepSeek.id -> "deepseek"
                binding.radioLocal.id -> "local"
                else -> "pollinations"
            }

            val themeId = when (binding.themeGroup.checkedRadioButtonId) {
                binding.themeIronMan.id -> "iron_man"
                binding.themeUltron.id -> "ultron"
                binding.themeVibranium.id -> "vibranium"
                binding.themeGold.id -> "gold_titanium"
                else -> "matrix"
            }

            prefs.edit()
                .putString("brain_backend", backend)
                .putString("theme_id", themeId)
                .putString("gemini_api_key", binding.geminiKeyInput.text.toString().trim())
                .putString("openai_api_key", binding.openaiKeyInput.text.toString().trim())
                .putString("deepseek_api_key", binding.deepseekKeyInput.text.toString().trim())
                .apply()

            // Restart the wake-word service so it picks up the new brain/API key immediately
            if (prefs.getBoolean("wake_word_enabled", true)) {
                val serviceIntent = Intent(this, WakeWordService::class.java)
                stopService(serviceIntent)
                startForegroundService(serviceIntent)
            }
            finish()
        }

        binding.checkUpdateButton.setOnClickListener {
            binding.updateStatusText.text = "Checking..."
            CoroutineScope(Dispatchers.Main).launch {
                val currentCode = packageManager.getPackageInfo(packageName, 0).let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode.toInt() else it.versionCode
                }
                val update = UpdateChecker.checkForUpdate(currentCode)
                if (update == null) {
                    binding.updateStatusText.text = "You're on the latest build (#$currentCode)."
                } else {
                    binding.updateStatusText.text = "Downloading ${update.versionTag}..."
                    val ok = UpdateChecker.downloadAndInstall(applicationContext, update)
                    binding.updateStatusText.text = if (ok) {
                        "Downloaded — confirm the install prompt."
                    } else {
                        "Update download failed. Check your connection and try again."
                    }
                }
            }
        }
    }
}
