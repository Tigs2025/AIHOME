package and.ai.aihome

import and.ai.aihome.databinding.ActivityMainBinding
import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var voiceGPTModule: VoiceGPTModule
    private lateinit var serviceIntent: Intent

    private val apiKey = "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"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        voiceGPTModule = VoiceGPTModule(this, apiKey)
        serviceIntent = Intent(this, PorcupineService::class.java)

        if (!checkAndRequestPermissions()) return

        // Listen button starts chat without greeting
        binding.listenButton.setOnClickListener {
            if (checkAndRequestPermissions()) {
                startVoiceFlow()
            }
        }

        // History button shows conversation popup
        binding.historyButton.setOnClickListener {
            showConversationHistory()
        }

        ContextCompat.startForegroundService(this, serviceIntent)

        // Wake word triggers greeting flow
        if (intent?.getBooleanExtra("wake_gpt", false) == true) {
            Log.d("MainActivity", "Wake GPT detected — greeting start")
            startVoiceFlowWithGreeting("Hello, how can I assist you?")
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra("wake_gpt", false) == true) {
            Log.d("MainActivity", "Wake GPT intent in onNewIntent")
            startVoiceFlowWithGreeting("Hello")
        }
    }

    private fun startVoiceFlowWithGreeting(greeting: String) {
        appendToHistory("You: $greeting")
        voiceGPTModule.fullVoiceChatFlow(greeting) { response ->
            runOnUiThread {
                appendToHistory("GPT: $response")
            }
        }
    }

    private fun startVoiceFlow() {
        voiceGPTModule.fullVoiceChatFlow { response ->
            runOnUiThread {
                appendToHistory("GPT: $response")
            }
        }
    }

    private fun appendToHistory(message: String) {
        runOnUiThread {
            binding.conversationHistory.append("$message\n\n")
            binding.scrollView.post {
                binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun showConversationHistory() {
        val historyText = binding.conversationHistory.text.toString()
        Toast.makeText(this, historyText.ifEmpty { "No conversation yet." }, Toast.LENGTH_LONG).show()
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        }
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        return if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 1001)
            false
        } else true
    }
}
