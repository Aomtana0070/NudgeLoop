package app.renotify.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

object RuleStore : SharedPreferences.OnSharedPreferenceChangeListener {
    private const val PREFS = "renotify_private_settings"
    private const val RULES = "rules"
    private const val PENDING = "pending"
    private const val SETTINGS = "settings"

    private val lock = Any()
    private var preferences: SharedPreferences? = null
    private val mutableConfig = MutableStateFlow(StoredConfig())
    val config: StateFlow<StoredConfig> = mutableConfig

    fun initialize(context: Context) {
        synchronized(lock) {
            if (preferences != null) return
            preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .also { it.registerOnSharedPreferenceChangeListener(this) }
            mutableConfig.value = readConfig(preferences!!)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        mutableConfig.value = readConfig(sharedPreferences)
    }

    fun saveRule(context: Context, rule: AppRule) {
        val prefs = prefs(context)
        val rules = mutableConfig.value.rules.toMutableMap()
        rules[rule.packageName] = rule
        prefs.edit().putString(RULES, rules.toJson().toString()).apply()
        mutableConfig.value = mutableConfig.value.copy(rules = rules)
    }

    fun setRuleEnabled(context: Context, packageName: String, enabled: Boolean) {
        val current = mutableConfig.value.rules[packageName] ?: AppRule(packageName)
        saveRule(context, current.copy(enabled = enabled))
    }

    fun removeRule(context: Context, packageName: String) {
        val rules = mutableConfig.value.rules.toMutableMap().apply { remove(packageName) }
        prefs(context).edit().putString(RULES, rules.toJson().toString()).apply()
        mutableConfig.value = mutableConfig.value.copy(rules = rules)
    }

    fun saveSettings(context: Context, settings: ReminderSettings) {
        prefs(context).edit().putString(SETTINGS, settings.toJson().toString()).apply()
        mutableConfig.value = mutableConfig.value.copy(settings = settings)
    }

    fun putPending(context: Context, reminder: PendingReminder) {
        val pending = mutableConfig.value.pending.associateBy { it.key }.toMutableMap()
        pending[reminder.key] = reminder
        writePending(context, pending.values.toList())
    }

    fun removePending(context: Context, key: String) {
        val pending = mutableConfig.value.pending.filterNot { it.key == key }
        writePending(context, pending)
    }

    fun removePendingForPackage(context: Context, packageName: String): List<PendingReminder> {
        val removed = mutableConfig.value.pending.filter { it.packageName == packageName && !it.demo }
        writePending(context, mutableConfig.value.pending - removed.toSet())
        return removed
    }

    fun replacePending(context: Context, reminders: List<PendingReminder>) {
        writePending(context, reminders)
    }

    private fun writePending(context: Context, reminders: List<PendingReminder>) {
        prefs(context).edit().putString(PENDING, reminders.toJson().toString()).apply()
        mutableConfig.value = mutableConfig.value.copy(pending = reminders)
    }

    private fun prefs(context: Context): SharedPreferences {
        initialize(context)
        return preferences!!
    }

    private fun readConfig(prefs: SharedPreferences): StoredConfig = StoredConfig(
        rules = prefs.getString(RULES, null).fromJsonArray()?.toRules().orEmpty(),
        pending = prefs.getString(PENDING, null).fromJsonArray()?.toPendingList().orEmpty(),
        settings = prefs.getString(SETTINGS, null).fromJsonObject()?.toSettings() ?: ReminderSettings(),
    )

    private fun Map<String, AppRule>.toJson() = JSONArray().also { array ->
        values.forEach { rule ->
            array.put(JSONObject().apply {
                put("packageName", rule.packageName)
                put("enabled", rule.enabled)
                put("intervalMinutes", rule.intervalMinutes)
                put("maxRepeats", rule.maxRepeats)
                put("senderFilter", rule.senderFilter)
                put("exactSenderMatch", rule.exactSenderMatch)
            })
        }
    }

    private fun JSONArray.toRules(): Map<String, AppRule> = buildMap {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val packageName = item.optString("packageName").takeIf(String::isNotBlank) ?: continue
            put(packageName, AppRule(
                packageName = packageName,
                enabled = item.optBoolean("enabled", false),
                intervalMinutes = item.optInt("intervalMinutes", 5).coerceIn(1, 1440),
                maxRepeats = item.optInt("maxRepeats", 3).coerceIn(1, 20),
                senderFilter = item.optString("senderFilter"),
                exactSenderMatch = item.optBoolean("exactSenderMatch", true),
            ))
        }
    }

    private fun List<PendingReminder>.toJson() = JSONArray().also { array ->
        forEach { reminder ->
            array.put(JSONObject().apply {
                put("key", reminder.key)
                put("packageName", reminder.packageName)
                put("appLabel", reminder.appLabel)
                put("sender", reminder.sender)
                put("preview", reminder.preview)
                put("intervalMinutes", reminder.intervalMinutes)
                put("maxRepeats", reminder.maxRepeats)
                put("sentRepeats", reminder.sentRepeats)
                put("nextAtMillis", reminder.nextAtMillis)
                put("demo", reminder.demo)
            })
        }
    }

    private fun JSONArray.toPendingList(): List<PendingReminder> = buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val key = item.optString("key").takeIf(String::isNotBlank) ?: continue
            add(PendingReminder(
                key = key,
                packageName = item.optString("packageName"),
                appLabel = item.optString("appLabel"),
                sender = item.optString("sender"),
                preview = item.optString("preview"),
                intervalMinutes = item.optInt("intervalMinutes", 5).coerceIn(1, 1440),
                maxRepeats = item.optInt("maxRepeats", 3).coerceIn(1, 20),
                sentRepeats = item.optInt("sentRepeats", 0).coerceAtLeast(0),
                nextAtMillis = item.optLong("nextAtMillis", 0L),
                demo = item.optBoolean("demo", false),
            ))
        }
    }

    private fun ReminderSettings.toJson() = JSONObject().apply {
        put("showPreview", showPreview)
        put("soundEnabled", soundEnabled)
        put("quietHoursEnabled", quietHoursEnabled)
        put("quietStartMinute", quietStartMinute)
        put("quietEndMinute", quietEndMinute)
    }

    private fun JSONObject.toSettings() = ReminderSettings(
        showPreview = optBoolean("showPreview", false),
        soundEnabled = optBoolean("soundEnabled", true),
        quietHoursEnabled = optBoolean("quietHoursEnabled", false),
        quietStartMinute = optInt("quietStartMinute", 22 * 60).coerceIn(0, 1439),
        quietEndMinute = optInt("quietEndMinute", 7 * 60).coerceIn(0, 1439),
    )

    private fun String?.fromJsonObject(): JSONObject? = runCatching {
        this?.let(::JSONObject)
    }.getOrNull()

    private fun String?.fromJsonArray(): JSONArray? = runCatching {
        this?.let(::JSONArray)
    }.getOrNull()
}
