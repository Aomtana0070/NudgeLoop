package app.renotify.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        ReminderEngine.deliver(context, key)
    }

    companion object {
        const val EXTRA_KEY = "reminder_key"
    }
}
