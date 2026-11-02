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
import app.renotify.service.ReminderEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PermissionState(
    val notificationAccess: Boolean = false,
    val postNotifications: Boolean = false,
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

    init {
        RuleStore.initialize(application)
        viewModelScope.launch {
            RuleStore.config.collect { config ->
                mutableState.value = mutableState.value.copy(config = config)
            }
        }
        refresh()
    }

    fun refresh() {
        val context = getApplication<Application>()
        val previousPermissions = mutableState.value.permissions
        val permissions = PermissionState(
                notificationAccess = hasNotificationAccess(context),
                postNotifications = Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            )
        if (permissions.notificationAccess &&
            (!previousPermissions.notificationAccess || (!previousPermissions.postNotifications && permissions.postNotifications))) {
            NotificationAccessService.requestRefresh(context)
        }
        if (permissions.notificationAccess && permissions.postNotifications) {
            ReminderEngine.reschedulePending(context)
        }
        mutableState.value = mutableState.value.copy(loadingApps = true, permissions = permissions)
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { InstalledApps.load(context) }
            mutableState.value = mutableState.value.copy(
                apps = apps,
                config = RuleStore.config.value,
                permissions = permissions,
                loadingApps = false,
            )
        }
    }

    fun ruleFor(packageName: String): AppRule = state.value.config.rules[packageName] ?: AppRule(packageName)

    fun saveRule(rule: AppRule) {
        ReminderEngine.applyRule(getApplication(), rule)
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
}
