package com.agentfold.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** 1x1 home-screen voice switch. Clicking it always creates explicit user intent. */
class VoiceToggleWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
    }
    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, VoiceToggleWidget::class.java)
            val click = PendingIntent.getActivity(context, 17,
                Intent(context, VoiceToggleActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            for (id in manager.getAppWidgetIds(component)) {
                val remote = RemoteViews(context.packageName, R.layout.voice_widget)
                remote.setTextViewText(R.id.voice_widget_label, if (VoiceChatService.isActive) "ON" else "OFF")
                remote.setTextViewText(R.id.voice_widget_icon, if (VoiceChatService.isActive) "●" else "◌")
                remote.setOnClickPendingIntent(R.id.voice_widget_root, click)
                manager.updateAppWidget(id, remote)
            }
        }
    }
}
