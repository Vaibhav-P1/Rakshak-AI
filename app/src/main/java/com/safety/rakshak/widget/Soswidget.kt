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
            triggerSOSFromWidget(context)
        }
    }

    private fun triggerSOSFromWidget(context: Context) {
        // Fix: Use a full activity trampoline to start the foreground service
        // Direct startForegroundService from BroadcastReceiver is restricted
        // on Android 14 — going through activity context fixes this
        val sosIntent = Intent(context, SOSService::class.java).apply {
            action = SOSService.ACTION_TRIGGER_SOS
            // FLAG_FROM_BACKGROUND tells system this is intentional
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(sosIntent)
            } else {
                context.startService(sosIntent)
            }
        } catch (e: Exception) {
            // Fallback — try regular startService
            try { context.startService(sosIntent) } catch (ex: Exception) { }
        }
    }
}

fun updateWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    widgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.widget_sos)

    // Fix: use unique request code per widget to avoid PendingIntent collision
    val sosIntent = Intent(context, SOSWidget::class.java).apply {
        action = SOSWidget.ACTION_WIDGET_SOS
    }
    val sosPendingIntent = PendingIntent.getBroadcast(
        context,
        widgetId, // unique per widget instance
        sosIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    views.setOnClickPendingIntent(R.id.widget_sos_button, sosPendingIntent)

    // Fetch contact count
    CoroutineScope(Dispatchers.IO).launch {
        val count = try {
            RakshakDatabase.getDatabase(context)
                .emergencyContactDao()
                .getAllContactsList()
                .size
        } catch (e: Exception) { 0 }

        withContext(Dispatchers.Main) {
            views.setTextViewText(
                R.id.widget_contact_count,
                if (count == 0) "No contacts added"
                else "$count contact${if (count > 1) "s" else ""} will receive alert"
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    appWidgetManager.updateAppWidget(widgetId, views)
}