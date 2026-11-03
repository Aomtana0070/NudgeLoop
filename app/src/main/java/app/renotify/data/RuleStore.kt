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
    private const val SENDER_SUGGESTIONS = "sender_suggestions"
    private const val DIAGNOSTICS = "diagnostics"
    private const val MAX_SUGGESTIONS_PER_APP = 100
    private const val MAX_APPS_WITH_SUGGESTIONS = 40

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

    fun recordSourceNotification(context: Context, packageName: String, atMillis: Long) {
        updateDiagnostics(context) {
            it.copy(lastSourceAtMillis = atMillis, lastSourcePackage = packageName)
        }
    }

    fun recordListenerConnected(context: Context, atMillis: Long) {
        updateDiagnostics(context) {
            it.copy(lastListenerConnectedAtMillis = atMillis)
        }
    }

    fun recordListenerDisconnected(context: Context, atMillis: Long) {
        updateDiagnostics(context) {
            it.copy(lastListenerDisconnectedAtMillis = atMillis)
        }
    }

    fun recordListenerRebindRequest(
        context: Context,
        atMillis: Long,
        source: String,
        error: String = "",
    ) {
        updateDiagnostics(context) {
            it.copy(
                lastListenerRebindRequestedAtMillis = atMillis,
                lastListenerRebindSource = source,
                lastListenerRebindError = error,
            )
        }
    }

    fun recordRuleCheck(context: Context, packageName: String, result: String, atMillis: Long) {
        updateDiagnostics(context) {
            it.copy(
                lastRuleCheckedAtMillis = atMillis,
                lastRuleCheckedPackage = packageName,
                lastRuleCheckResult = result,
            )
        }
    }

    fun recordRuleMatched(context: Context, packageName: String, atMillis: Long) {
        updateDiagnostics(context) {
            it.copy(lastMatchedAtMillis = atMillis, lastMatchedPackage = packageName)
        }
    }

    fun recordAlarmScheduled(context: Context, packageName: String, atMillis: Long, triggerAtMillis: Long) {
        updateDiagnostics(context) {
            it.copy(
                lastAlarmScheduledAtMillis = atMillis,
                nextAlarmAtMillis = triggerAtMillis,
                lastAlarmPackage = packageName,
            )
        }
    }

    fun recordAlarmReceived(context: Context, packageName: String, atMillis: Long) {
        updateDiagnostics(context) {
            it.copy(
                lastAlarmReceivedAtMillis = atMillis,
                lastAlarmReceivedPackage = packageName,
                nextAlarmAtMillis = 0L,
            )
        }
    }

    fun recordReminderPosted(context: Context, packageName: String, atMillis: Long) {
        updateDiagnostics(context) {
            it.copy(lastReminderPostedAtMillis = atMillis, lastReminderPostedPackage = packageName)
        }
    }

    fun recordDeliveryProblem(context: Context, problem: String, atMillis: Long) {
        updateDiagnostics(context) {
            it.copy(lastDeliveryProblemAtMillis = atMillis, lastDeliveryProblem = problem)
        }
    }

    fun rememberSenderSuggestion(context: Context, packageName: String, title: String) {
        val suggestion = title.trim().take(80)
        if (packageName.isBlank() || suggestion.isBlank()) return

        val current = mutableConfig.value.senderSuggestions
        val existing = current[packageName].orEmpty()
        if (existing.firstOrNull()?.equals(suggestion, ignoreCase = true) == true) return

        val suggestions = current.toMutableMap().apply {
            remove(packageName)
            put(packageName, (listOf(suggestion) + existing.filterNot { it.equals(suggestion, ignoreCase = true) }).take(MAX_SUGGESTIONS_PER_APP))
            while (size > MAX_APPS_WITH_SUGGESTIONS) {
                remove(keys.first())
            }
        }
        prefs(context).edit()
            .putString(SENDER_SUGGESTIONS, suggestions.toSuggestionJson().toString())
            .apply()
        mutableConfig.value = mutableConfig.value.copy(senderSuggestions = suggestions)
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

    private fun updateDiagnostics(context: Context, update: (ReminderDiagnostics) -> ReminderDiagnostics) {
        val prefs = prefs(context)
        synchronized(lock) {
            val diagnostics = update(mutableConfig.value.diagnostics)
            prefs.edit().putString(DIAGNOSTICS, diagnostics.toJson().toString()).apply()
            mutableConfig.value = mutableConfig.value.copy(diagnostics = diagnostics)
        }
    }

    private fun prefs(context: Context): SharedPreferences {
        initialize(context)
        return preferences!!
    }

    private fun readConfig(prefs: SharedPreferences): StoredConfig = StoredConfig(
        rules = prefs.getString(RULES, null).fromJsonArray()?.toRules().orEmpty(),
        pending = prefs.getString(PENDING, null).fromJsonArray()?.toPendingList().orEmpty(),
        settings = prefs.getString(SETTINGS, null).fromJsonObject()?.toSettings() ?: ReminderSettings(),
        senderSuggestions = prefs.getString(SENDER_SUGGESTIONS, null)
            .fromJsonObject()?.toSenderSuggestions().orEmpty(),
        diagnostics = prefs.getString(DIAGNOSTICS, null)
            .fromJsonObject()?.toDiagnostics() ?: ReminderDiagnostics(),
    )

    private fun ReminderDiagnostics.toJson() = JSONObject().apply {
        put("lastListenerConnectedAtMillis", lastListenerConnectedAtMillis)
        put("lastListenerDisconnectedAtMillis", lastListenerDisconnectedAtMillis)
        put("lastListenerRebindRequestedAtMillis", lastListenerRebindRequestedAtMillis)
        put("lastListenerRebindSource", lastListenerRebindSource)
        put("lastListenerRebindError", lastListenerRebindError)
        put("lastSourceAtMillis", lastSourceAtMillis)
        put("lastSourcePackage", lastSourcePackage)
        put("lastRuleCheckedAtMillis", lastRuleCheckedAtMillis)
        put("lastRuleCheckedPackage", lastRuleCheckedPackage)
        put("lastRuleCheckResult", lastRuleCheckResult)
        put("lastMatchedAtMillis", lastMatchedAtMillis)
        put("lastMatchedPackage", lastMatchedPackage)
        put("lastAlarmScheduledAtMillis", lastAlarmScheduledAtMillis)
        put("nextAlarmAtMillis", nextAlarmAtMillis)
        put("lastAlarmPackage", lastAlarmPackage)
        put("lastAlarmReceivedAtMillis", lastAlarmReceivedAtMillis)
        put("lastAlarmReceivedPackage", lastAlarmReceivedPackage)
        put("lastReminderPostedAtMillis", lastReminderPostedAtMillis)
        put("lastReminderPostedPackage", lastReminderPostedPackage)
        put("lastDeliveryProblemAtMillis", lastDeliveryProblemAtMillis)
        put("lastDeliveryProblem", lastDeliveryProblem)
    }

    private fun JSONObject.toDiagnostics() = ReminderDiagnostics(
        lastListenerConnectedAtMillis = optLong("lastListenerConnectedAtMillis", 0L),
        lastListenerDisconnectedAtMillis = optLong("lastListenerDisconnectedAtMillis", 0L),
        lastListenerRebindRequestedAtMillis = optLong("lastListenerRebindRequestedAtMillis", 0L),
        lastListenerRebindSource = optString("lastListenerRebindSource"),
        lastListenerRebindError = optString("lastListenerRebindError"),
        lastSourceAtMillis = optLong("lastSourceAtMillis", 0L),
        lastSourcePackage = optString("lastSourcePackage"),
        lastRuleCheckedAtMillis = optLong("lastRuleCheckedAtMillis", 0L),
        lastRuleCheckedPackage = optString("lastRuleCheckedPackage"),
        lastRuleCheckResult = optString("lastRuleCheckResult"),
        lastMatchedAtMillis = optLong("lastMatchedAtMillis", 0L),
        lastMatchedPackage = optString("lastMatchedPackage"),
        lastAlarmScheduledAtMillis = optLong("lastAlarmScheduledAtMillis", 0L),
        nextAlarmAtMillis = optLong("nextAlarmAtMillis", 0L),
        lastAlarmPackage = optString("lastAlarmPackage"),
        lastAlarmReceivedAtMillis = optLong("lastAlarmReceivedAtMillis", 0L),
        lastAlarmReceivedPackage = optString("lastAlarmReceivedPackage"),
        lastReminderPostedAtMillis = optLong("lastReminderPostedAtMillis", 0L),
        lastReminderPostedPackage = optString("lastReminderPostedPackage"),
        lastDeliveryProblemAtMillis = optLong("lastDeliveryProblemAtMillis", 0L),
        lastDeliveryProblem = optString("lastDeliveryProblem"),
    )

    private fun Map<String, AppRule>.toJson() = JSONArray().also { array ->
        values.forEach { rule ->
            array.put(JSONObject().apply {
                put("packageName", rule.packageName)
                put("enabled", rule.enabled)
                put("intervalMinutes", rule.intervalMinutes)
                put("maxRepeats", rule.maxRepeats ?: JSONObject.NULL)
                put("senderFilters", JSONArray(rule.senderFilters))
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
                maxRepeats = item.optionalRepeatLimit(),
                senderFilters = item.optJSONArray("senderFilters")?.toStringList()
                    ?: item.optString("senderFilter").takeIf(String::isNotBlank)?.let(::listOf).orEmpty(),
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
                put("maxRepeats", reminder.maxRepeats ?: JSONObject.NULL)
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
                maxRepeats = item.optionalRepeatLimit(),
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

    private fun Map<String, List<String>>.toSuggestionJson() = JSONObject().also { json ->
        forEach { (packageName, names) ->
            json.put(packageName, JSONArray(names))
        }
    }

    private fun JSONObject.toSenderSuggestions(): Map<String, List<String>> = buildMap {
        val keys = keys()
        while (keys.hasNext()) {
            val packageName = keys.next()
            val names = optJSONArray(packageName)?.toStringList().orEmpty()
                .map { it.trim().take(80) }
                .filter(String::isNotBlank)
                .distinctBy(String::lowercase)
                .take(MAX_SUGGESTIONS_PER_APP)
            if (names.isNotEmpty()) put(packageName, names)
        }
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) {
            optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private fun JSONObject.optionalRepeatLimit(): Int? = when {
        !has("maxRepeats") -> 3
        isNull("maxRepeats") -> null
        else -> optInt("maxRepeats", 3).coerceIn(1, 20)
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
