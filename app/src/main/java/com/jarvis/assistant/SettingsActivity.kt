package com.jarvis.assistant

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jarvis.assistant.databinding.ActivitySettingsBinding
import com.jarvis.assistant.service.WakeWordService

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
        binding.claudeKeyInput.setText(prefs.getString("claude_api_key", ""))
        binding.geminiKeyInput.setText(prefs.getString("gemini_api_key", ""))
        binding.openaiKeyInput.setText(prefs.getString("openai_api_key", ""))
        binding.deepseekKeyInput.setText(prefs.getString("deepseek_api_key", ""))

        // Select the currently active backend radio button
        when (prefs.getString("brain_backend", "claude")) {
            "gemini" -> binding.radioGemini.isChecked = true
            "openai" -> binding.radioOpenAI.isChecked = true
            "deepseek" -> binding.radioDeepSeek.isChecked = true
            "local" -> binding.radioLocal.isChecked = true
            else -> binding.radioClaude.isChecked = true
        }

        binding.saveButton.setOnClickListener {
            val backend = when (binding.backendGroup.checkedRadioButtonId) {
                binding.radioGemini.id -> "gemini"
                binding.radioOpenAI.id -> "openai"
                binding.radioDeepSeek.id -> "deepseek"
                binding.radioLocal.id -> "local"
                else -> "claude"
            }

            prefs.edit()
                .putString("brain_backend", backend)
                .putString("claude_api_key", binding.claudeKeyInput.text.toString().trim())
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
    }
}
