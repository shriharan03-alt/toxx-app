package com.toxx.assistant

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.toxx.assistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var wakeWordRunning = false

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handle grant results if you want to react immediately */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnNotificationAccess.setOnClickListener {
            // Notification access can't be requested as a runtime permission —
            // it's a special setting the user must toggle manually.
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.btnGrantCallPermissions.setOnClickListener {
            requestPermissions.launch(
                arrayOf(
                    android.Manifest.permission.CALL_PHONE,
                    android.Manifest.permission.READ_CALL_LOG,
                    android.Manifest.permission.READ_PHONE_STATE,
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.READ_CONTACTS
                )
            )
        }

        binding.btnStartService.setOnClickListener {
            startForegroundService(Intent(this, TOXXForegroundService::class.java))
        }

        binding.btnToggleWakeWord.setOnClickListener {
            if (wakeWordRunning) {
                stopService(Intent(this, WakeWordService::class.java))
                binding.btnToggleWakeWord.text = "4. Start Listening for \"Wake up TOXX\""
            } else {
                startForegroundService(Intent(this, WakeWordService::class.java))
                binding.btnToggleWakeWord.text = "4. Stop Listening"
            }
            wakeWordRunning = !wakeWordRunning
        }

        binding.btnTestCall.setOnClickListener {
            val number = binding.inputPhoneNumber.text.toString()
            if (number.isNotBlank()) {
                CallManager.placeCall(this, number)
            }
        }
    }
}
