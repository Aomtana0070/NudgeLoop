package app.renotify.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(ReminderReceiver.EXTRA_KEY)
        val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)
        key?.let { ReminderEngine.snooze(context, it, minutes) }
    }

    companion object {
        const val EXTRA_SNOOZE_MINUTES = "app.renotify.extra.SNOOZE_MINUTES"
        private const val TAG = "SnoozeReceiver"
    }
}