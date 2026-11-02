package app.renotify.service

import android.app.PendingIntent
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.renotify.data.RuleStore
import app.renotify.data.RuleMatcher
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationAccessService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = WeakReference(this)
        mutableConnectionState.value = true
        val active = try {
            activeNotifications.orEmpty().toList()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to read active notifications; keeping pending reminders", error)
            return
        }
        Log.i(TAG, "Notification listener connected; inspecting ${active.size} active notifications")
        val keys = active.asSequence()
            .filterNot { it.packageName == packageName }
            .map(StatusBarNotification::getKey)
            .toSet()
        synchronized(activeOriginalKeys) {
            activeOriginalKeys.clear()
            activeOriginalKeys.addAll(keys)
        }
        ReminderEngine.restoreAfterListenerConnect(this, keys)
        active.filterNot { it.packageName == packageName }.forEach(::inspect)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != packageName) {
            synchronized(activeOriginalKeys) { activeOriginalKeys.add(sbn.key) }
            RuleStore.recordSourceNotification(this, sbn.packageName, System.currentTimeMillis())
        }
        Log.d(TAG, "Notification posted by ${sbn.packageName}")
        inspect(sbn)
    }

    private fun inspect(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if ((sbn.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0) {
            Log.d(TAG, "Ignoring ongoing notification from ${sbn.packageName}")
            return
        }
        if ((sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) {
            Log.d(TAG, "Ignoring group summary from ${sbn.packageName}")
            return
        }

        val title = sbn.notification.extras
            .getCharSequence(android.app.Notification.EXTRA_TITLE)
            ?.toString()?.trim().orEmpty()
        RuleStore.rememberSenderSuggestion(this, sbn.packageName, title)
        val body = sbn.notification.extras
            .getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)
            ?.toString()
            ?: sbn.notification.extras
                .getCharSequence(android.app.Notification.EXTRA_TEXT)
                ?.toString().orEmpty()

        val config = RuleStore.config.value
        val rule = config.rules[sbn.packageName]?.takeIf { it.enabled } ?: return
        if (!RuleMatcher.matchesSender(rule, title)) {
            Log.d(TAG, "Notification from ${sbn.packageName} did not match its sender rule")
            return
        }
        RuleStore.recordRuleMatched(this, sbn.packageName, System.currentTimeMillis())
        Log.i(TAG, "Matched notification from ${sbn.packageName}; scheduling reminder")
        sbn.notification.contentIntent?.let { contentIntents[sbn.key] = it }
            ?: contentIntents.remove(sbn.key)
        ReminderEngine.startFromNotification(this, sbn.key, sbn.packageName, title, body.take(500))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        if (sbn.packageName != packageName) synchronized(activeOriginalKeys) { activeOriginalKeys.remove(sbn.key) }
        contentIntents.remove(sbn.key)
        if (RuleStore.config.value.pending.any { it.key == sbn.key }) {
            ReminderEngine.cancel(this, sbn.key)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected; requesting rebind")
        if (instance?.get() === this) instance = null
        mutableConnectionState.value = false
        synchronized(activeOriginalKeys) { activeOriginalKeys.clear() }
        NotificationListenerService.requestRebind(ComponentName(this, NotificationAccessService::class.java))
    }

    companion object {
        private val contentIntents = ConcurrentHashMap<String, PendingIntent?>()
        private val activeOriginalKeys = mutableSetOf<String>()
        private val mutableConnectionState = MutableStateFlow(false)
        val connectionState = mutableConnectionState.asStateFlow()
        @Volatile private var instance: WeakReference<NotificationAccessService>? = null
        private const val TAG = "NotificationAccess"

        fun contentIntentFor(key: String): PendingIntent? = contentIntents[key]

        fun isConnected(): Boolean = instance?.get() != null

        fun isOriginalActive(key: String): Boolean? {
            val service = instance?.get() ?: return null
            val active = try {
                service.activeNotifications.orEmpty()
                    .asSequence()
                    .filterNot { it.packageName == service.packageName }
                    .map(StatusBarNotification::getKey)
                    .toSet()
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to verify whether a source notification is still active", error)
                return null
            }
            synchronized(activeOriginalKeys) {
                activeOriginalKeys.clear()
                activeOriginalKeys.addAll(active)
            }
            return key in active
        }

        fun requestRefresh(context: android.content.Context) {
            if (!hasNotificationAccess(context)) return
            val service = instance?.get()
            if (service == null) {
                Log.i(TAG, "Notification listener is not connected; requesting rebind")
                NotificationListenerService.requestRebind(ComponentName(context, NotificationAccessService::class.java))
            } else {
                val active = try {
                    service.activeNotifications.orEmpty().toList()
                } catch (error: RuntimeException) {
                    Log.w(TAG, "Unable to refresh active notifications; keeping pending reminders", error)
                    return
                }
                val original = active.filterNot { it.packageName == context.packageName }
                val activeKeys = original.map(StatusBarNotification::getKey).toSet()
                synchronized(activeOriginalKeys) {
                    activeOriginalKeys.clear()
                    activeOriginalKeys.addAll(activeKeys)
                }
                Log.i(TAG, "Refreshing ${original.size} active notifications")
                ReminderEngine.restoreAfterListenerConnect(context, activeKeys)
                original.forEach(service::inspect)
            }
        }

        private fun hasNotificationAccess(context: android.content.Context): Boolean {
            val expected = ComponentName(context, NotificationAccessService::class.java)
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            )?.split(':').orEmpty()
            return enabled.any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
