package app.renotify.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.renotify.data.RuleStore

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY)
        if (key == null) {
            Log.e(TAG, "Reminder alarm was delivered without a reminder key")
            return
        }
        val reminder = RuleStore.config.value.pending.firstOrNull { it.key == key }
        reminder?.let {
            RuleStore.recordAlarmReceived(context, it.packageName, System.currentTimeMillis())
        }
        Log.i(TAG, "Received reminder alarm: $key")
        ReminderEngine.deliver(context, key)
    }

    companion object {
        const val EXTRA_KEY = "reminder_key"
        private const val TAG = "ReminderReceiver"
    }
}
