package app.renotify.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.renotify.MainActivity
import app.renotify.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ListenerKeepAliveService : Service() {
    private var retryIndex = 0
    private var listenerConnected = false
    private val handler = Handler(Looper.getMainLooper())
    private val retryListener = object : Runnable {
        override fun run() {
            if (!NotificationAccessService.hasNotificationAccess(this@ListenerKeepAliveService)) {
                stopSelf()
                return
            }
            listenerConnected = NotificationAccessService.isConnected()
            if (listenerConnected) {
                retryIndex = 0
            } else {
                NotificationAccessService.requestListenerRebind(this@ListenerKeepAliveService, "keep_alive_retry")
                retryIndex = (retryIndex + 1).coerceAtMost(RETRY_DELAYS_MILLIS.lastIndex)
            }
            updateForegroundNotification()
            scheduleNextRetry()
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        mutableRunningState.value = true
        listenerConnected = NotificationAccessService.isConnected()
        updateForegroundNotification()

        if (!NotificationAccessService.hasNotificationAccess(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        NotificationAccessService.requestRefresh(this)
        handler.removeCallbacks(retryListener)
        scheduleNextRetry()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "App task removed; foreground listener keep-alive remains active")
        if (NotificationAccessService.hasNotificationAccess(this)) {
            handler.removeCallbacks(retryListener)
            handler.post(retryListener)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(retryListener)
        if (serviceInstance === this) serviceInstance = null
        mutableRunningState.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                if (listenerConnected) "Listener เชื่อมต่ออยู่ · กำลังทำงานเบื้องหลัง"
                else "Listener หลุด · กำลังลองเชื่อมต่อใหม่",
            )
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateForegroundNotification() {
        val notification = buildNotification()
        if (mutableRunningState.value) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun scheduleNextRetry() {
        val delayMillis = if (listenerConnected) {
            CONNECTED_CHECK_INTERVAL_MILLIS
        } else {
            RETRY_DELAYS_MILLIS[retryIndex]
        }
        handler.removeCallbacks(retryListener)
        handler.postDelayed(retryListener, delayMillis)
    }

    private fun setListenerConnected(connected: Boolean) {
        listenerConnected = connected
        retryIndex = 0
        updateForegroundNotification()
        handler.removeCallbacks(retryListener)
        if (!connected && NotificationAccessService.hasNotificationAccess(this)) {
            handler.postDelayed(retryListener, FIRST_RETRY_DELAY_MILLIS)
        } else {
            scheduleNextRetry()
        }
    }

    companion object {
        private const val TAG = "ListenerKeepAlive"
        private const val CHANNEL_ID = "listener_keep_alive"
        private const val NOTIFICATION_ID = 1108
        private const val FIRST_RETRY_DELAY_MILLIS = 5_000L
        private const val CONNECTED_CHECK_INTERVAL_MILLIS = 60_000L
        private val RETRY_DELAYS_MILLIS = listOf(5_000L, 15_000L, 30_000L, 60_000L)

        private val mutableRunningState = MutableStateFlow(false)
        val runningState = mutableRunningState.asStateFlow()
        @Volatile private var serviceInstance: ListenerKeepAliveService? = null

        fun isRunning(): Boolean = mutableRunningState.value

        fun onListenerConnectionChanged(connected: Boolean) {
            serviceInstance?.setListenerConnected(connected)
        }

        fun startIfAllowed(context: Context) {
            if (!NotificationAccessService.hasNotificationAccess(context)) return

            createNotificationChannel(context)
            ContextCompat.startForegroundService(
                context,
                Intent(context, ListenerKeepAliveService::class.java),
            )
        }

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "การทำงานเบื้องหลัง",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "แสดงสถานะบริการพยุงการเชื่อมต่อแจ้งเตือน"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
