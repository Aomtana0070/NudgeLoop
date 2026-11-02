package app.renotify

import android.app.Application
import app.renotify.data.RuleStore
import app.renotify.service.ReminderEngine

class ReNotifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RuleStore.initialize(this)
        ReminderEngine.createChannelsForApp(this)
    }
}
