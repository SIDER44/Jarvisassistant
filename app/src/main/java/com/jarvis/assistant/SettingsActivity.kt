package com.jarvis.assistant

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jarvis.assistant.databinding.ActivitySettingsBinding

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

        binding.apiKeyInput.setText(prefs.getString("claude_api_key", ""))
        binding.localBackendSwitch.isChecked = prefs.getString("brain_backend", "claude") == "local"

        binding.saveButton.setOnClickListener {
            prefs.edit()
                .putString("claude_api_key", binding.apiKeyInput.text.toString().trim())
                .putString("brain_backend", if (binding.localBackendSwitch.isChecked) "local" else "claude")
                .apply()
            finish()
        }
    }
}
