package com.agentfold.app.nerv

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.agentfold.app.NervActivity

class NotificationHelper(context: Context) {

    companion object {
        private const val CHANNEL_ID = "nerv_alerts"
        private const val CHANNEL_NAME = "NERV ALERTS"
        private const val ID_APPROVAL = 2101
        private const val ID_TASK_FINISHED = 2102
        private const val ID_TIMER_FINISHED = 2103
        private const val ID_TEST = 2104
        private const val ID_ROUTINE = 2105

        private const val ID_TASK_WAITING = 2106
        private const val ID_TIMER_ALMOST_DONE = 2107
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("nerv_notifications", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun canNotify(): Boolean = enabled && hasPermission()

    fun notifyApprovalNeeded(objective: String) {
        post(
            ID_APPROVAL,
            "MAGI NEEDS APPROVAL",
            "A phone task is waiting: ${objective.take(100)}",
        )
    }

    fun notifyTaskFinished(objective: String) {
        post(
            ID_TASK_FINISHED,
            "PHONE TASK FINISHED",
            "MAGI finished: ${objective.take(100)}",
        )
    }

    fun notifyTimerFinished(name: String) {
        post(
            ID_TIMER_FINISHED,
            "TIMER FINISHED",
            "\"$name\" reached 00:00.",
        )
    }

    fun notifyRoutineDone(name: String) {
        post(
            ID_ROUTINE,
            "ROUTINE FINISHED",
            "MAGI ran your routine \"$name\" — the answer is in CHAT.",
        )
    }

    fun notifyTaskWaiting(objective: String) {
        post(
            ID_TASK_WAITING,
            "PHONE TASK WAITING",
            "Still waiting: ${objective.take(100)}",
        )
    }

    fun notifyTimerAlmostDone(name: String) {
        post(
            ID_TIMER_ALMOST_DONE,
            "TIMER ALMOST DONE",
            "\"$name\" has 1 minute left.",
        )
    }

    fun notifyTest() {
        post(
            ID_TEST,
            "NERV NOTIFICATIONS",
            "Notifications are working — MAGI will alert you here when a task finishes or needs approval.",
        )
    }

    private fun post(id: Int, title: String, text: String) {
        if (!canNotify()) return
        try {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "MAGI alerts: a phone task finishing, a task needing approval, or a timer finishing."
                }
                manager.createNotificationChannel(channel)
            }
            val openNerv = Intent(appContext, NervActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val tap = PendingIntent.getActivity(
                appContext,
                id,
                openNerv,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(appContext, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(appContext).apply { setPriority(Notification.PRIORITY_DEFAULT) }
            }
            val notification = builder
                .setSmallIcon(appContext.applicationInfo.icon)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            manager.notify(id, notification)
        } catch (e: Exception) {

        }
    }
}
