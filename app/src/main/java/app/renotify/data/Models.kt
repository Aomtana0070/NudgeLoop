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
    val maxRepeats: Int = 3,
    val senderFilter: String = "",
    val exactSenderMatch: Boolean = true,
)

data class PendingReminder(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val sender: String,
    val preview: String,
    val intervalMinutes: Int,
    val maxRepeats: Int,
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

data class StoredConfig(
    val rules: Map<String, AppRule> = emptyMap(),
    val pending: List<PendingReminder> = emptyList(),
    val settings: ReminderSettings = ReminderSettings(),
)
