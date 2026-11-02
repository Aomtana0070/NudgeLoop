package app.renotify.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.renotify.MainActivity
import app.renotify.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ListenerKeepAliveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val retryListener = object : Runnable {
        override fun run() {
            if (!NotificationAccessService.hasNotificationAccess(this@ListenerKeepAliveService)) {
                stopSelf()
                return
            }
            if (!NotificationAccessService.isConnected()) {
                NotificationAccessService.requestRefresh(this@ListenerKeepAliveService)
            }
            handler.postDelayed(this, REBIND_RETRY_INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
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

        if (!NotificationAccessService.hasNotificationAccess(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        NotificationAccessService.requestRefresh(this)
        handler.removeCallbacks(retryListener)
        handler.postDelayed(retryListener, REBIND_RETRY_INTERVAL_MILLIS)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(retryListener)
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
            .setContentText("กำลังพยุงการเชื่อมต่อแจ้งเตือนในเบื้องหลัง")
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "listener_keep_alive"
        private const val NOTIFICATION_ID = 1108
        private const val REBIND_RETRY_INTERVAL_MILLIS = 60_000L

        private val mutableRunningState = MutableStateFlow(false)
        val runningState = mutableRunningState.asStateFlow()

        fun isRunning(): Boolean = mutableRunningState.value

        fun startIfAllowed(context: Context) {
            if (!NotificationAccessService.hasNotificationAccess(context)) return
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return

            createNotificationChannel(context)
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

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
