package com.safety.rakshak.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.safety.rakshak.R
import com.safety.rakshak.data.RakshakDatabase
import com.safety.rakshak.service.SOSService
import com.safety.rakshak.sos.SosSource
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
        // A widget tap counts as user interaction, so the foreground start is allowed.
        SOSService.trigger(context, SosSource.WIDGET)
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