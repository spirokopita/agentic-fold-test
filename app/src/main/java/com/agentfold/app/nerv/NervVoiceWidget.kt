package com.agentfold.app.nerv

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.agentfold.app.NervActivity
import com.agentfold.app.R

class NervVoiceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) updateAll(context)
    }

    companion object {

        const val ACTION_LISTEN = "com.agentfold.app.nerv.ACTION_WIDGET_LISTEN"
        const val ACTION_OPEN = "com.agentfold.app.nerv.ACTION_WIDGET_OPEN"
        const val ACTION_REFRESH = "com.agentfold.app.nerv.ACTION_WIDGET_REFRESH"

        const val EXTRA_LISTEN = "nerv_widget_listen"

        private const val PREFS = "nerv_widget"
        private const val KEY_STATUS = "status"
        private const val KEY_REPLY = "last_reply"
        private const val MAX_REPLY_CHARS = 140

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun saveStatus(context: Context, status: NervStatus) {
            prefs(context).edit().putString(KEY_STATUS, status.name).apply()
        }

        fun saveReply(context: Context, reply: String) {
            prefs(context).edit().putString(KEY_REPLY, reply.trim().take(MAX_REPLY_CHARS)).apply()
        }

        fun lastReply(context: Context): String? =
            prefs(context).getString(KEY_REPLY, null)?.takeIf { it.isNotBlank() }

        fun isPinned(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, NervVoiceWidget::class.java))
                .isNotEmpty()

        fun requestPin(activity: Activity): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val manager = AppWidgetManager.getInstance(activity)
            if (!manager.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(activity, NervVoiceWidget::class.java)
            val success = PendingIntent.getActivity(
                activity, 0,
                Intent(activity, NervActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            return try {
                manager.requestPinAppWidget(provider, null, success)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NervVoiceWidget::class.java))
            ids.forEach { updateOne(context, manager, it) }
        }

        private fun statusText(context: Context): String {
            val saved = prefs(context).getString(KEY_STATUS, NervStatus.IDLE.name)
            val status = try {
                NervStatus.valueOf(saved ?: NervStatus.IDLE.name)
            } catch (_: IllegalArgumentException) {
                NervStatus.IDLE
            }
            return when (status) {
                NervStatus.IDLE -> "MAGI · IDLE"
                NervStatus.LISTENING -> "MAGI · LISTENING…"
                NervStatus.THINKING -> "MAGI · THINKING…"
                NervStatus.ACTING -> "MAGI · ACTING"
                NervStatus.WAITING -> "MAGI · WAITING"
                NervStatus.ERROR -> "MAGI · ERROR"
                NervStatus.OFFLINE -> "MAGI · OFFLINE"
            }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.nerv_voice_widget)
            views.setTextViewText(R.id.nerv_widget_status, statusText(context))
            val reply = lastReply(context)
            views.setTextViewText(
                R.id.nerv_widget_reply,
                if (reply != null) "“$reply”" else "Tap TALK and ask MAGI something."
            )
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val openIntent = Intent(context, NervActivity::class.java).apply {
                action = ACTION_OPEN
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            views.setOnClickPendingIntent(
                R.id.nerv_widget_root,
                PendingIntent.getActivity(context, 0, openIntent, pendingFlags)
            )
            val listenIntent = Intent(context, NervActivity::class.java).apply {
                action = ACTION_LISTEN
                putExtra(EXTRA_LISTEN, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            views.setOnClickPendingIntent(
                R.id.nerv_widget_talk,
                PendingIntent.getActivity(context, 1, listenIntent, pendingFlags)
            )
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
