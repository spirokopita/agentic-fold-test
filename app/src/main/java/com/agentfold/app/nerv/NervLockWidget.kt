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

class NervLockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) updateAll(context)
    }

    companion object {

        const val ACTION_OPEN = "com.agentfold.app.nerv.ACTION_LOCK_WIDGET_OPEN"
        const val ACTION_REFRESH = "com.agentfold.app.nerv.ACTION_LOCK_WIDGET_REFRESH"

        private const val PREFS = "nerv_lock_widget"
        private const val KEY_TIMER = "next_timer"
        private const val KEY_MAGI = "magi_status"

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun saveTimer(context: Context, text: String) {
            prefs(context).edit().putString(KEY_TIMER, text.trim().take(80)).apply()
        }

        fun saveMagi(context: Context, text: String) {
            prefs(context).edit().putString(KEY_MAGI, text.trim().take(80)).apply()
        }

        fun lastTimer(context: Context): String? =
            prefs(context).getString(KEY_TIMER, null)?.takeIf { it.isNotBlank() }

        fun lastMagi(context: Context): String? =
            prefs(context).getString(KEY_MAGI, null)?.takeIf { it.isNotBlank() }

        fun isPinned(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, NervLockWidget::class.java))
                .isNotEmpty()

        fun requestPin(activity: Activity): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val manager = AppWidgetManager.getInstance(activity)
            if (!manager.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(activity, NervLockWidget::class.java)
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
            val ids = manager.getAppWidgetIds(ComponentName(context, NervLockWidget::class.java))
            ids.forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.nerv_lock_widget)
            views.setTextViewText(
                R.id.nerv_lock_timer,
                lastTimer(context) ?: "NO TIMER RUNNING"
            )
            views.setTextViewText(
                R.id.nerv_lock_magi,
                lastMagi(context) ?: "MAGI — NODES UNKNOWN"
            )
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val openIntent = Intent(context, NervActivity::class.java).apply {
                action = ACTION_OPEN
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            views.setOnClickPendingIntent(
                R.id.nerv_lock_root,
                PendingIntent.getActivity(context, 0, openIntent, pendingFlags)
            )
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
