package and.ai.aihome

import ai.picovoice.porcupine.PorcupineManager
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream

class PorcupineService : Service() {
    private lateinit var porcupineManager: PorcupineManager

    override fun onCreate() {
        super.onCreate()
        Log.d("PorcupineService", "onCreate called")

        try {
            val keywordPath = copyAssetToFile(this, "hey-device.ppn")
            val modelPath = copyAssetToFile(this, "porcupine_params.pv")

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey("2OJu8ANA6ePHuoWtNxnl+P36loZ1smHiNvi30sboX1GDupGVSDCjgA==")
                .setKeywordPath(keywordPath)
                .setModelPath(modelPath)
                .setSensitivity(0.65f)
                .build(applicationContext) { _ ->
                    Log.d("PorcupineService", "Wake word detected, sending broadcast")
                    val broadcastIntent = Intent("and.ai.aihome.WAKE_WORD_DETECTED")
                    broadcastIntent.setPackage(packageName)
                    sendBroadcast(broadcastIntent)
                }

            Log.d("PorcupineService", "PorcupineManager initialized")
        } catch (e: Exception) {
            Log.e("PorcupineService", "Error initializing Porcupine: ${e.message}", e)
        }
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("PorcupineService", "onStartCommand called")
        try {
            porcupineManager.start()
            Log.d("PorcupineService", "Porcupine started")
        } catch (e: Exception) {
            Log.e("PorcupineService", "Failed to start Porcupine: ${e.message}", e)
        }
        startForeground(1, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            porcupineManager.stop()
            porcupineManager.delete()
            Log.d("PorcupineService", "Porcupine stopped and deleted")
        } catch (e: Exception) {
            Log.e("PorcupineService", "Failed to stop Porcupine: ${e.message}", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun copyAssetToFile(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            Log.d("PorcupineService", "Copied asset: $assetName")
        }
        return file.absolutePath
    }

    private fun createNotification(): Notification {
        val channelId = "porcupine_service"
        val channel = NotificationChannel(
            channelId,
            "Wake Word Detection",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Voice Wake Word Listening")
            .setContentText("Listening for the wake word...")
            .setSmallIcon(R.drawable.outline_adaptive_audio_mic_24)
            .build()
    }
}
