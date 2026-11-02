package app.renotify.ui

import android.app.AlarmManager
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.renotify.data.AppEntry
import app.renotify.data.AppRule
import app.renotify.data.InstalledApps
import app.renotify.data.ReminderSettings
import app.renotify.data.RuleStore
import app.renotify.data.StoredConfig
import app.renotify.service.NotificationAccessService
import app.renotify.service.ListenerKeepAliveService
import app.renotify.service.ReminderEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PermissionState(
    val notificationAccess: Boolean = false,
    val notificationListenerConnected: Boolean = false,
    val listenerKeepAliveRunning: Boolean = false,
    val postNotifications: Boolean = false,
    val appNotificationsEnabled: Boolean = false,
    val exactAlarms: Boolean = false,
)

data class ReNotifyUiState(
    val apps: List<AppEntry> = emptyList(),
    val config: StoredConfig = StoredConfig(),
    val permissions: PermissionState = PermissionState(),
    val loadingApps: Boolean = true,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ReNotifyUiState())
    val state: StateFlow<ReNotifyUiState> = mutableState.asStateFlow()
    private var listenerRecoveryJob: Job? = null

    init {
        RuleStore.initialize(application)
        viewModelScope.launch {
            RuleStore.config.collect { config ->
                mutableState.value = mutableState.value.copy(config = config)
            }
        }
        viewModelScope.launch {
            NotificationAccessService.connectionState.collect(::updateListenerConnectionState)
        }
        viewModelScope.launch {
            ListenerKeepAliveService.runningState.collect(::updateListenerKeepAliveState)
        }
        refresh()
    }

    fun refresh() {
        val context = getApplication<Application>()
        val postNotifications = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val permissions = PermissionState(
            notificationAccess = hasNotificationAccess(context),
            notificationListenerConnected = NotificationAccessService.isConnected(),
            listenerKeepAliveRunning = ListenerKeepAliveService.isRunning(),
            postNotifications = postNotifications,
            appNotificationsEnabled = !postNotifications || ReminderEngine.canDisplayNotifications(context),
            exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        )
        mutableState.value = mutableState.value.copy(loadingApps = true, permissions = permissions)
        if (permissions.notificationAccess) refreshNotificationListener()
        else {
            listenerRecoveryJob?.cancel()
            listenerRecoveryJob = null
        }
        if (permissions.notificationAccess && permissions.postNotifications && permissions.appNotificationsEnabled) {
            ReminderEngine.reschedulePending(context)
        }
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { InstalledApps.load(context) }
            mutableState.value = mutableState.value.copy(
                apps = apps,
                config = RuleStore.config.value,
                loadingApps = false,
            )
        }
    }

    fun refreshNotificationListener() {
        val context = getApplication<Application>()
        listenerRecoveryJob?.cancel()
        listenerRecoveryJob = null
        if (!hasNotificationAccess(context)) {
            updateListenerConnectionState(false)
            return
        }

        NotificationAccessService.requestRefresh(context)
        if (NotificationAccessService.isConnected()) {
            updateListenerConnectionState(true)
            return
        }

        updateListenerConnectionState(false)
        listenerRecoveryJob = viewModelScope.launch {
            for (retryDelay in LISTENER_RETRY_DELAYS_MILLIS) {
                delay(retryDelay)
                if (!hasNotificationAccess(context)) return@launch
                NotificationAccessService.requestRefresh(context)
                val connected = NotificationAccessService.isConnected()
                updateListenerConnectionState(connected)
                if (connected) return@launch
            }
        }
    }

    fun ruleFor(packageName: String): AppRule = state.value.config.rules[packageName] ?: AppRule(packageName)

    fun senderSuggestionsFor(packageName: String): List<String> =
        state.value.config.senderSuggestions[packageName].orEmpty()

    fun saveRule(rule: AppRule) {
        val context = getApplication<Application>()
        ReminderEngine.applyRule(context, rule)
        if (rule.enabled) {
            NotificationAccessService.requestRefresh(context)
        }
    }

    fun saveSettings(settings: ReminderSettings) {
        ReminderEngine.applySettings(getApplication(), settings)
    }

    fun stopReminder(key: String) {
        ReminderEngine.cancel(getApplication(), key)
    }

    fun demoReminder() {
        ReminderEngine.startDemo(getApplication())
    }

    fun remindersFor(packageName: String) = state.value.config.pending.filter {
        !it.demo && it.packageName == packageName
    }

    private fun hasNotificationAccess(context: Context): Boolean {
        val expected = ComponentName(context, NotificationAccessService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.split(':').orEmpty()
        return enabled.any { ComponentName.unflattenFromString(it) == expected }
    }

    private fun updateListenerConnectionState(connected: Boolean) {
        mutableState.value = mutableState.value.copy(
            permissions = mutableState.value.permissions.copy(notificationListenerConnected = connected),
        )
    }

    private fun updateListenerKeepAliveState(running: Boolean) {
        mutableState.value = mutableState.value.copy(
            permissions = mutableState.value.permissions.copy(listenerKeepAliveRunning = running),
        )
    }

    private companion object {
        val LISTENER_RETRY_DELAYS_MILLIS = listOf(1_000L, 3_000L, 8_000L, 15_000L)
    }
}
