package com.example.encryptedkeyboard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val manageKeysButton = findViewById<Button>(R.id.manage_keys_button)
        manageKeysButton.setOnClickListener {
            startActivity(Intent(this, KeyManagementActivity::class.java))
        }
    }
}