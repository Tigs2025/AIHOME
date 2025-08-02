package and.ai.aihome

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.IOException

class VoiceGPTModule(private val context: Context, private val apiKey: String) {
    private var recorder: MediaRecorder? = null
    private var audioFilePath: String = ""
    private val conversationHistory = mutableListOf<JSONObject>()
    private val handler = Handler(Looper.getMainLooper())

    // === Start recording with silence detection ===
    fun startRecording(onSilence: () -> Unit) {
        val outputDir = context.externalCacheDir ?: context.filesDir
        val audioFile = File(outputDir, "recording.m4a")
        audioFilePath = audioFile.absolutePath

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(audioFilePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
            start()
        }
        Log.d("VoiceGPTModule", "Recording started (silence detection ON)")

        val silenceThreshold = 2000  // Adjust for mic sensitivity
        val silenceMaxDuration = 1500 // 1.5 seconds
        var silenceStartTime = -1L

        val checkSilenceRunnable = object : Runnable {
            override fun run() {
                val amplitude = recorder?.maxAmplitude ?: 0
                if (amplitude < silenceThreshold) {
                    if (silenceStartTime == -1L) {
                        silenceStartTime = System.currentTimeMillis()
                    }
                    if (System.currentTimeMillis() - silenceStartTime > silenceMaxDuration) {
                        Log.d("VoiceGPTModule", "Silence detected — stopping recording")
                        stopRecording()
                        onSilence()
                        return
                    }
                } else {
                    silenceStartTime = -1L // reset if talking
                }
                handler.postDelayed(this, 200)
            }
        }
        handler.post(checkSilenceRunnable)
    }

    fun stopRecording() {
        try {
            recorder?.apply { stop() }
        } catch (e: Exception) {
            Log.e("VoiceGPTModule", "Error stopping recorder: ${e.message}")
        }
        recorder?.release()
        recorder = null
        Log.d("VoiceGPTModule", "Recording stopped")
    }

    // === Greeting version ===
    fun fullVoiceChatFlow(initialPrompt: String, onResult: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                conversationHistory.add(JSONObject().apply {
                    put("role", "user")
                    put("content", initialPrompt)
                })
                val chatResponse = askChatGPT()
                conversationHistory.add(JSONObject().apply {
                    put("role", "assistant")
                    put("content", chatResponse)
                })
                speakResponse(chatResponse) {
                    startRecording {
                        stopAndSend(onResult)
                    }
                }
                onResult(chatResponse)
            } catch (e: Exception) {
                onResult("Sorry babe, something went wrong.")
            }
        }
    }

    // === Voice flow ===
    fun fullVoiceChatFlow(onResult: (String) -> Unit) {
        startRecording {
            stopAndSend(onResult)
        }
    }

    private fun stopAndSend(onResult: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transcript = transcribeAudio()
                conversationHistory.add(JSONObject().apply {
                    put("role", "user")
                    put("content", transcript)
                })
                val chatResponse = askChatGPT()
                conversationHistory.add(JSONObject().apply {
                    put("role", "assistant")
                    put("content", chatResponse)
                })
                speakResponse(chatResponse) {
                    startRecording {
                        stopAndSend(onResult)
                    }
                }
                onResult(chatResponse)
            } catch (e: Exception) {
                onResult("Sorry babe, something went wrong.")
            }
        }
    }

    private fun transcribeAudio(): String {
        val client = OkHttpClient()
        val file = File(audioFilePath)
        val mediaType = "audio/m4a".toMediaType()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
            .addFormDataPart("model", "whisper-1")
            .build()
        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("Empty response")
            return JSONObject(body).getString("text")
        }
    }

    private fun askChatGPT(): String {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("model", "gpt-4o")
            put("messages", JSONArray(conversationHistory))
        }
        val body = RequestBody.create("application/json".toMediaType(), json.toString())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw IOException("Empty response")
            val choices = JSONObject(responseBody).getJSONArray("choices")
            return choices.getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    private fun speakResponse(text: String, onFinished: () -> Unit) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("model", "tts-1")
            put("input", text)
            put("voice", "nova")
        }
        val body = RequestBody.create("application/json".toMediaType(), json.toString())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/speech")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val file = File.createTempFile("tts_output", ".mp3", context.cacheDir)
            file.writeBytes(response.body!!.bytes())
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { onFinished() }
                start()
            }
        }
    }
}
