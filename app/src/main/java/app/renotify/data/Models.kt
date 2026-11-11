package app.renotify.data

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable,
)

data class AppRule(
    val packageName: String,
    val enabled: Boolean = false,
    val intervalMinutes: Int = 5,
    val maxRepeats: Int? = 3,
    val senderFilters: List<String> = emptyList(),
    val exactSenderMatch: Boolean = true,
    val quietHoursBypassSenders: List<String> = emptyList(),
)

data class PendingReminder(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val sender: String,
    val preview: String,
    val intervalMinutes: Int,
    val maxRepeats: Int?,
    val sentRepeats: Int = 0,
    val nextAtMillis: Long = 0L,
    val demo: Boolean = false,
)

data class ReminderSettings(
    val showPreview: Boolean = false,
    val soundEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 7 * 60,
)

data class ReminderDiagnostics(
    val lastListenerConnectedAtMillis: Long = 0L,
    val lastListenerDisconnectedAtMillis: Long = 0L,
    val lastListenerRebindRequestedAtMillis: Long = 0L,
    val lastListenerRebindSource: String = "",
    val lastListenerRebindError: String = "",
    val lastSourceAtMillis: Long = 0L,
    val lastSourcePackage: String = "",
    val lastRuleCheckedAtMillis: Long = 0L,
    val lastRuleCheckedPackage: String = "",
    val lastRuleCheckResult: String = "",
    val lastMatchedAtMillis: Long = 0L,
    val lastMatchedPackage: String = "",
    val lastAlarmScheduledAtMillis: Long = 0L,
    val nextAlarmAtMillis: Long = 0L,
    val lastAlarmPackage: String = "",
    val lastAlarmReceivedAtMillis: Long = 0L,
    val lastAlarmReceivedPackage: String = "",
    val lastReminderPostedAtMillis: Long = 0L,
    val lastReminderPostedPackage: String = "",
    val lastDeliveryProblemAtMillis: Long = 0L,
    val lastDeliveryProblem: String = "",
)

data class StoredConfig(
    val rules: Map<String, AppRule> = emptyMap(),
    val pending: List<PendingReminder> = emptyList(),
    val settings: ReminderSettings = ReminderSettings(),
    val senderSuggestions: Map<String, List<String>> = emptyMap(),
    val diagnostics: ReminderDiagnostics = ReminderDiagnostics(),
)
