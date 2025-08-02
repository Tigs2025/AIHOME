package and.ai.aihome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WakeWordReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("WakeWordReceiver", "Wake word broadcast received")

        if (context != null && intent?.action == "and.ai.aihome.WAKE_WORD_DETECTED") {
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("wake_gpt", true)
            }
            context.startActivity(mainIntent)
            Log.d("WakeWordReceiver", "Started MainActivity")
        }
    }
}
