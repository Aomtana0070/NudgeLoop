package app.renotify.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.renotify.R
import app.renotify.data.AppRule
import app.renotify.data.PendingReminder
import app.renotify.data.ReminderSettings
import app.renotify.data.ReminderPolicy
import app.renotify.data.RuleStore
import app.renotify.data.RuleMatcher
import java.security.MessageDigest
import java.util.Calendar

object ReminderEngine {
    private const val CHANNEL_SOUND = "repeat_alerts_sound"
    private const val CHANNEL_SILENT = "repeat_alerts_silent"
    private const val ACTION_REPEAT = "app.renotify.action.REPEAT"
    private const val ACTION_OPEN = "app.renotify.action.OPEN"
    private const val ACTION_STOP = "app.renotify.action.STOP"
    private const val ACTION_SNOOZE = "app.renotify.action.SNOOZE"
    private const val EXTRA_SNOOZE_MINUTES = "app.renotify.extra.SNOOZE_MINUTES"
    private const val TAG = "ReminderEngine"

    private val SNOOZE_DELAYS_MINUTES = listOf(5, 10, 15)
    private val SNOOZE_STRING_IDS = intArrayOf(R.string.snooze_5min, R.string.snooze_10min, R.string.snooze_15min)

    fun canDisplayNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.notificationChannels.any { channel ->
            channel.id.startsWith("repeat_") && channel.importance != NotificationManager.IMPORTANCE_NONE
        }
    }

    fun createChannelsForApp(context: Context) = createChannels(context)

    fun startFromNotification(context: Context, key: String, packageName: String, title: String, body: String) {
        if (!hasNotificationAccess(context)) return
        val config = RuleStore.config.value
        val rule = config.rules[packageName]?.takeIf(AppRule::enabled) ?: return
        val sender = title.trim()
        if (!RuleMatcher.matchesSender(rule, sender)) return
        if (config.pending.any { it.key == key }) return

        val label = app.renotify.data.InstalledApps.label(context, packageName)
        val firstAt = System.currentTimeMillis() + rule.intervalMinutes * 60_000L
        val reminder = PendingReminder(
            key = key,
            packageName = packageName,
            appLabel = label,
            sender = sender,
            preview = if (config.settings.showPreview) body else "",
            intervalMinutes = rule.intervalMinutes,
            maxRepeats = rule.maxRepeats,
            nextAtMillis = firstAt,
        )
        RuleStore.putPending(context, reminder)
        schedule(context, reminder, firstAt)
    }

    fun startDemo(context: Context, intervalMinutes: Int = 1) {
        val key = "demo-${System.currentTimeMillis()}"
        val reminder = PendingReminder(
            key = key,
            packageName = "demo",
            appLabel = "ตัวอย่างข้อความ",
            sender = "คุณสำคัญ",
            preview = if (RuleStore.config.value.settings.showPreview) "ข้อความตัวอย่างสำหรับทดสอบการเตือนซ้ำ" else "",
            intervalMinutes = intervalMinutes,
            maxRepeats = 2,
            nextAtMillis = System.currentTimeMillis(),
            demo = true,
        )
        RuleStore.putPending(context, reminder)
        deliver(context, key)
    }

    fun deliver(context: Context, key: String) {
        val config = RuleStore.config.value
        val reminder = config.pending.firstOrNull { it.key == key } ?: return
        Log.i(TAG, "Reminder alarm fired: $key")

        // Verify the source notification is still active before doing any work.
        // Demo reminders are self-contained and skip this check.
        if (!reminder.demo) {
            val sourceActive = NotificationAccessService.isOriginalActive(key)
            if (sourceActive == false) {
                Log.i(TAG, "Source notification is no longer active; stopping reminder: $key")
                cancel(context, key)
                return
            }
        }

        if (!ReminderPolicy.shouldRepeat(reminder.sentRepeats, reminder.maxRepeats)) {
            cancel(context, key)
            return
        }

        val canReadSource = reminder.demo || hasNotificationAccess(context)
        val canPost = hasPostPermission(context) && canDisplayNotifications(context)
        if (!canReadSource || !canPost) {
            // Re-verify source is still active before deferring; the source may
            // have been dismissed while permissions were unavailable.
            if (!reminder.demo && NotificationAccessService.isOriginalActive(key) == false) {
                Log.i(TAG, "Source notification dismissed while permissions unavailable; stopping: $key")
                cancel(context, key)
                return
            }
            Log.w(TAG, "Reminder permissions are unavailable; retrying later: $key")
            RuleStore.recordDeliveryProblem(context, "สิทธิ์อ่านหรือส่งการแจ้งเตือนไม่พร้อม", System.currentTimeMillis())
            deferAndRetry(context, reminder)
            return
        }

        val now = System.currentTimeMillis()
        val rule = config.rules[reminder.packageName]
        val bypassesQuietHours = rule?.let { RuleMatcher.bypassesQuietHours(it, reminder.sender) } == true
        val quietUntil = quietUntil(context, config.settings, now).takeUnless { bypassesQuietHours }
        if (quietUntil != null) {
            // Re-verify source is still active before deferring into quiet hours.
            if (!reminder.demo && NotificationAccessService.isOriginalActive(key) == false) {
                Log.i(TAG, "Source notification dismissed during quiet hours; stopping: $key")
                cancel(context, key)
                return
            }
            // This repeat fired but was suppressed by quiet hours; consume a repeat count.
            val nextCount = reminder.sentRepeats + 1
            if (!ReminderPolicy.shouldRepeat(nextCount, reminder.maxRepeats)) {
                cancel(context, key)
                return
            }
            val deferred = reminder.copy(
                sentRepeats = nextCount,
                nextAtMillis = quietUntil,
            )
            RuleStore.putPending(context, deferred)
            schedule(context, deferred, quietUntil)
            return
        }

        createChannels(context)
        val nextCount = reminder.sentRepeats + 1
        if (!postReminder(context, reminder.copy(sentRepeats = nextCount), config.settings)) {
            // Re-verify source is still active before retrying after a delivery failure.
            if (!reminder.demo && NotificationAccessService.isOriginalActive(key) == false) {
                Log.i(TAG, "Source notification dismissed after delivery failure; stopping: $key")
                cancel(context, key)
                return
            }
            Log.e(TAG, "Failed to post reminder; retrying later: $key")
            RuleStore.recordDeliveryProblem(context, "Android ไม่ยอมแสดงการแจ้งเตือนซ้ำ", System.currentTimeMillis())
            deferAndRetry(context, reminder)
            return
        }
        RuleStore.recordReminderPosted(context, reminder.packageName, System.currentTimeMillis())
        val updated = reminder.copy(
            sentRepeats = nextCount,
            nextAtMillis = if (ReminderPolicy.shouldRepeat(nextCount, reminder.maxRepeats)) {
                now + reminder.intervalMinutes * 60_000L
            } else {
                0L
            },
        )
        if (updated.demo && updated.nextAtMillis == 0L) {
            RuleStore.removePending(context, key)
        } else {
            RuleStore.putPending(context, updated)
            if (updated.nextAtMillis > 0L) schedule(context, updated, updated.nextAtMillis)
        }
    }

    fun cancel(context: Context, key: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(alarmPendingIntent(context, key))
        NotificationManagerCompat.from(context).cancel(notificationId(key))
        RuleStore.removePending(context, key)
    }

    fun cancelPackage(context: Context, packageName: String) {
        RuleStore.removePendingForPackage(context, packageName).forEach { cancel(context, it.key) }
    }

    fun snooze(context: Context, key: String, minutes: Int) {
        val reminder = RuleStore.config.value.pending.firstOrNull { it.key == key } ?: return
        val snoozeAt = System.currentTimeMillis() + minutes * 60_000L
        val snoozed = reminder.copy(nextAtMillis = snoozeAt)
        RuleStore.putPending(context, snoozed)
        schedule(context, snoozed, snoozeAt)
        Log.i(TAG, "Snoozed reminder $key by $minutes minutes")
    }

    fun applyRule(context: Context, rule: AppRule) {
        RuleStore.saveRule(context, rule)
        createChannels(context)
        val existing = RuleStore.config.value.pending.filter { it.packageName == rule.packageName && !it.demo }
        if (!rule.enabled) {
            existing.forEach { cancel(context, it.key) }
            return
        }
        existing.forEach { reminder ->
            val matches = RuleMatcher.matchesSender(rule, reminder.sender)
            if (!matches) {
                cancel(context, reminder.key)
            } else {
                val updated = reminder.copy(
                    intervalMinutes = rule.intervalMinutes.coerceIn(1, 1440),
                    maxRepeats = rule.maxRepeats?.coerceIn(1, 20),
                )
                if (ReminderPolicy.shouldRepeat(updated.sentRepeats, updated.maxRepeats)) {
                    val dueAt = System.currentTimeMillis() + updated.intervalMinutes * 60_000L
                    RuleStore.putPending(context, updated.copy(nextAtMillis = dueAt))
                    schedule(context, updated, dueAt)
                } else {
                    context.getSystemService(AlarmManager::class.java).cancel(alarmPendingIntent(context, reminder.key))
                    RuleStore.putPending(context, updated.copy(nextAtMillis = 0L))
                }
            }
        }
    }

    fun applySettings(context: Context, settings: ReminderSettings) {
        RuleStore.saveSettings(context, settings)
        if (!settings.showPreview) {
            RuleStore.config.value.pending.forEach { reminder ->
                if (reminder.preview.isNotEmpty()) {
                    val scrubbed = reminder.copy(preview = "")
                    RuleStore.putPending(context, scrubbed)
                    if (scrubbed.sentRepeats > 0) postReminder(context, scrubbed, settings, onlyAlertOnce = true)
                }
            }
        }
    }

    fun restoreAfterListenerConnect(context: Context, activeKeys: Set<String>) {
        val records = RuleStore.config.value.pending
        records.filter { !it.demo && it.key !in activeKeys }.forEach { cancel(context, it.key) }
        RuleStore.config.value.pending
            .filter { it.demo || (it.key in activeKeys && it.nextAtMillis > 0L) }
            .forEach { record ->
                val nextAt = record.nextAtMillis.takeIf { it > System.currentTimeMillis() }
                    ?: System.currentTimeMillis() + 1_000L
                schedule(context, record, nextAt)
            }
    }

    fun reschedulePending(context: Context) {
        RuleStore.config.value.pending
            .filter { it.nextAtMillis > 0L }
            .forEach { reminder ->
                val at = reminder.nextAtMillis.takeIf { it > System.currentTimeMillis() }
                    ?: System.currentTimeMillis() + 1_000L
                schedule(context, reminder, at)
            }
    }

    fun schedule(context: Context, reminder: PendingReminder, atMillis: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REPEAT
            data = Uri.parse("renotify://repeat/${stableId(reminder.key)}")
            putExtra(ReminderReceiver.EXTRA_KEY, reminder.key)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            stableId(reminder.key),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = maxOf(atMillis, System.currentTimeMillis() + 500L)
        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        val scheduled = runCatching {
            if (canUseExact) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            true
        }.onFailure { error ->
            Log.w(TAG, "Exact reminder alarm could not be scheduled; trying an inexact alarm: ${reminder.key}", error)
        }.getOrElse {
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                true
            }.onFailure { fallbackError ->
                Log.e(TAG, "Unable to schedule reminder alarm: ${reminder.key}", fallbackError)
            }.getOrDefault(false)
        }
        if (scheduled) {
            RuleStore.recordAlarmScheduled(context, reminder.packageName, System.currentTimeMillis(), triggerAt)
            Log.i(TAG, "Reminder alarm scheduled for $triggerAt (exact=$canUseExact): ${reminder.key}")
        } else {
            RuleStore.recordDeliveryProblem(context, "ตั้งเวลาปลุกซ้ำไม่สำเร็จ", System.currentTimeMillis())
        }
    }

    private fun deferAndRetry(context: Context, reminder: PendingReminder) {
        val retryAt = ReminderPolicy.retryAt(System.currentTimeMillis())
        val deferred = reminder.copy(nextAtMillis = retryAt)
        RuleStore.putPending(context, deferred)
        schedule(context, deferred, retryAt)
    }

    fun openOrStop(context: Context, key: String) {
        val sourceIntent = NotificationAccessService.contentIntentFor(key)
        if (sourceIntent != null) {
            runCatching { sourceIntent.send() }
        } else {
            val reminder = RuleStore.config.value.pending.firstOrNull { it.key == key }
            reminder?.packageName?.let { packageName ->
                context.packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(launch) }
                }
            }
        }
        cancel(context, key)
    }

    private fun postReminder(context: Context, reminder: PendingReminder, settings: ReminderSettings, onlyAlertOnce: Boolean = false): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!canDisplayNotifications(context)) return false
        val title = reminder.sender.ifBlank { reminder.appLabel }
        val repeatCount = reminder.maxRepeats?.let { "${reminder.sentRepeats}/$it" } ?: "${reminder.sentRepeats}/∞"
        val status = "จาก ${reminder.appLabel} · เตือนครั้งที่ $repeatCount"
        val body = reminder.preview.takeIf(String::isNotBlank)?.let { "$status · $it" } ?: status
        val openIntent = Intent(context, OpenReminderReceiver::class.java).apply {
            action = ACTION_OPEN
            data = Uri.parse("renotify://open/${stableId(reminder.key)}")
            putExtra(ReminderReceiver.EXTRA_KEY, reminder.key)
        }
        val stopIntent = Intent(context, StopReminderReceiver::class.java).apply {
            action = ACTION_STOP
            data = Uri.parse("renotify://stop/${stableId(reminder.key)}")
            putExtra(ReminderReceiver.EXTRA_KEY, reminder.key)
        }
        val openPending = PendingIntent.getBroadcast(
            context, stableId(reminder.key), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPending = PendingIntent.getBroadcast(
            context, stableId(reminder.key), stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozePendings = SNOOZE_DELAYS_MINUTES.map { minutes ->
            val snoozeIntent = Intent(context, SnoozeReceiver::class.java).apply {
                action = ACTION_SNOOZE
                data = Uri.parse("renotify://snooze/${stableId(reminder.key)}/$minutes")
                putExtra(ReminderReceiver.EXTRA_KEY, reminder.key)
                putExtra(EXTRA_SNOOZE_MINUTES, minutes)
            }
            PendingIntent.getBroadcast(
                context,
                stableId(reminder.key) + 1000 + minutes,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val rule = RuleStore.config.value.rules[reminder.packageName]
        val preset = rule?.soundPreset ?: "default"
        val preferredChannel = if (settings.soundEnabled) soundChannelId(reminder.packageName, preset) else CHANNEL_SILENT
        val fallbackChannel = if (settings.soundEnabled) CHANNEL_SILENT else soundChannelId(reminder.packageName, preset)
        val channel = if (isChannelEnabled(context, preferredChannel)) preferredChannel else fallbackChannel
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(reminder.appLabel)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .setDeleteIntent(stopPending)
            .addAction(0, "หยุดเตือน", stopPending)
            .apply {
                SNOOZE_DELAYS_MINUTES.forEachIndexed { index, minutes ->
                    addAction(0, context.getString(SNOOZE_STRING_IDS[index]), snoozePendings[index])
                }
            }
            .setOnlyAlertOnce(onlyAlertOnce)
            .build()
        return runCatching {
            manager.notify(notificationId(reminder.key), notification)
            true
        }.onFailure {
            Log.e(TAG, "Unable to post reminder notification: ${reminder.key}", it)
        }.getOrDefault(false)
    }

    private fun isChannelEnabled(context: Context, channelId: String): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE

    private fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SOUND, "เตือนซ้ำ · มีเสียง", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "การแจ้งเตือนซ้ำจากแอปที่คุณเลือก"
            enableVibration(true)
        })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SILENT, "เตือนซ้ำ · ไม่มีเสียง", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "การแจ้งเตือนซ้ำแบบเงียบ"
            setSound(null, null)
            enableVibration(false)
        })
        RuleStore.config.value.rules.values.forEach { rule ->
            val channelId = soundChannelId(rule.packageName, rule.soundPreset)
            manager.createNotificationChannel(NotificationChannel(channelId, "เตือน ${rule.packageName} · ${soundPresetLabel(rule.soundPreset)}", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "เสียงเตือนซ้ำสำหรับแอปนี้"
                setSound(soundUri(rule.soundPreset), soundAttributes())
                enableVibration(true)
            })
        }
    }

    private fun soundChannelId(packageName: String, preset: String): String =
        "repeat_${stableId(packageName)}_$preset"

    private fun soundPresetLabel(preset: String): String = when (preset) {
        "soft" -> "นุ่ม"
        "urgent" -> "เร่งด่วน"
        else -> "มาตรฐาน"
    }

    private fun soundUri(preset: String): Uri = when (preset) {
        "soft" -> Settings.System.DEFAULT_RINGTONE_URI
        "urgent" -> Settings.System.DEFAULT_ALARM_ALERT_URI
        else -> Settings.System.DEFAULT_NOTIFICATION_URI
    }

    private fun soundAttributes(): android.media.AudioAttributes =
        android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private fun quietUntil(context: Context, settings: ReminderSettings, now: Long): Long? {
        if (!settings.quietHoursEnabled || settings.quietStartMinute == settings.quietEndMinute) return null
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val isQuiet = if (settings.quietStartMinute < settings.quietEndMinute) {
            minute in settings.quietStartMinute until settings.quietEndMinute
        } else {
            minute >= settings.quietStartMinute || minute < settings.quietEndMinute
        }
        if (!isQuiet) return null

        val end = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, settings.quietEndMinute / 60)
            set(Calendar.MINUTE, settings.quietEndMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return end.timeInMillis
    }

    private fun hasPostPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationAccess(context: Context): Boolean {
        val expected = android.content.ComponentName(context, NotificationAccessService::class.java)
        val enabled = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.split(':').orEmpty()
        return enabled.any { android.content.ComponentName.unflattenFromString(it) == expected }
    }

    private fun alarmPendingIntent(context: Context, key: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        stableId(key),
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REPEAT
            data = Uri.parse("renotify://repeat/${stableId(key)}")
        },
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) ?: PendingIntent.getBroadcast(
        context,
        stableId(key),
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REPEAT
            data = Uri.parse("renotify://repeat/${stableId(key)}")
        },
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notificationId(key: String): Int = stableId(key).coerceAtLeast(1)

    private fun stableId(value: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return (((digest[0].toInt() and 0x7f) shl 24) or
            ((digest[1].toInt() and 0xff) shl 16) or
            ((digest[2].toInt() and 0xff) shl 8) or
            (digest[3].toInt() and 0xff)).coerceAtLeast(1)
    }
}
