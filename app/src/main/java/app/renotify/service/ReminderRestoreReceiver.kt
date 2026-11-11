package app.renotify.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ListenerKeepAliveService.startIfAllowed(context)
                NotificationAccessService.requestRefresh(context)
            }
        }
    }
}
