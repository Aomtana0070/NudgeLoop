package app.renotify.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class OpenReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(ReminderReceiver.EXTRA_KEY)?.let { ReminderEngine.openOrStop(context, it) }
    }
}
