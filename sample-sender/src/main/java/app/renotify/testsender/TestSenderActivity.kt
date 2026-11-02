package app.renotify.testsender

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class TestSenderActivity : Activity() {
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var senderAfterPermission = "Nok"
    private var idAfterPermission = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannel()

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(247, 246, 250))
        }
        root.addView(TextView(this).apply {
            text = "เครื่องมือทดสอบ NudgeLoop"
            textSize = 26f
            setTextColor(Color.rgb(28, 29, 34))
        }, match())
        root.addView(TextView(this).apply {
            text = "กดเพื่อสร้างการแจ้งเตือนจากอีกแอปหนึ่ง แล้วตรวจว่ากฎของ NudgeLoop จับชื่อผู้ส่งและเตือนซ้ำได้หรือไม่"
            textSize = 16f
            setTextColor(Color.rgb(80, 82, 90))
            setPadding(0, dp(12), 0, dp(20))
        }, match())
        root.addView(makeButton("ส่งจาก Nok · ควรตรงกับกฎที่ตั้งชื่อ Nok") { postSample("Nok", 1001) })
        root.addView(makeButton("ส่งจาก Mali · ควรถูกกรองออก") { postSample("Mali", 1002) })
        root.addView(makeButton("ล้างแจ้งเตือนตัวอย่าง") { notificationManager.cancelAll() })
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = (10 * resources.displayMetrics.density).toInt() }
    }

    private fun postSample(sender: String, id: Int) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            senderAfterPermission = sender
            idAfterPermission = id
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        val openIntent = PendingIntent.getActivity(
            this,
            id,
            Intent(this, TestSenderActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText("มีข้อความทดสอบใหม่จาก $sender")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(id, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ข้อความทดสอบ", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            postSample(senderAfterPermission, idAfterPermission)
        }
    }

    private fun match(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    companion object {
        private const val CHANNEL_ID = "test_messages"
        private const val REQUEST_NOTIFICATIONS = 120
    }
}
