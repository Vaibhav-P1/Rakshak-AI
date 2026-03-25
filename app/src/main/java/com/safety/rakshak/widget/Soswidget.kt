package com.safety.rakshak.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.safety.rakshak.R
import com.safety.rakshak.data.RakshakDatabase
import com.safety.rakshak.service.SOSService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SOSWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_SOS = "com.safety.rakshak.WIDGET_SOS"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_SOS) {
            // Trigger SOS directly from widget
            val sosIntent = Intent(context, SOSService::class.java).apply {
                action = SOSService.ACTION_TRIGGER_SOS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(sosIntent)
            } else {
                context.startService(sosIntent)
            }
        }
    }
}

fun updateWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    widgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.widget_sos)

    // SOS button click — triggers SOS directly
    val sosIntent = Intent(context, SOSWidget::class.java).apply {
        action = SOSWidget.ACTION_WIDGET_SOS
    }
    val sosPendingIntent = PendingIntent.getBroadcast(
        context, 0, sosIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    views.setOnClickPendingIntent(R.id.widget_sos_button, sosPendingIntent)

    // Fetch contact count async and update widget
    CoroutineScope(Dispatchers.IO).launch {
        val count = try {
            RakshakDatabase.getDatabase(context)
                .emergencyContactDao()
                .getAllContactsList()
                .size
        } catch (e: Exception) { 0 }

        withContext(Dispatchers.Main) {
            if (count == 0) {
                views.setTextViewText(
                    R.id.widget_contact_count,
                    "No contacts added"
                )
            } else {
                views.setTextViewText(
                    R.id.widget_contact_count,
                    "$count contact${if (count > 1) "s" else ""} will receive alert"
                )
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    appWidgetManager.updateAppWidget(widgetId, views)
}