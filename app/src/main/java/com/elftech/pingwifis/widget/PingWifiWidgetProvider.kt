package com.elftech.pingwifis.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.elftech.pingwifis.R

class PingWifiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetId))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            updateAllWidgets(context)
        }
    }

    companion object {
        private const val PREFS_NAME = "pingwifi_prefs"
        private const val ACTION_REFRESH = "com.elftech.pingwifis.action.WIDGET_REFRESH"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PingWifiWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds.isNotEmpty()) {
                widgetIds.forEach { appWidgetId ->
                    appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetId))
                }
            }
        }

        private fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val download = prefs.getFloat("last_download", -1f)
            val upload = prefs.getFloat("last_upload", 0f)
            val ping = prefs.getInt("last_ping", 0)
            val server = prefs.getString("last_server", "") ?: ""
            val timestamp = prefs.getLong("last_timestamp", 0L)

            return RemoteViews(context.packageName, R.layout.pingwifi_widget).apply {
                setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))

                val refreshIntent = Intent(context, PingWifiWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId + 10_000,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

                if (download < 0f) {
                    setViewVisibility(R.id.widget_status, View.GONE)
                    setTextViewText(R.id.widget_download_value, "--")
                    setTextViewText(R.id.widget_upload_value, "--")
                    setTextViewText(R.id.widget_ping_value, "--")
                    setViewVisibility(R.id.widget_server, View.GONE)
                    setViewVisibility(R.id.widget_updated_at, View.GONE)
                } else {
                    setViewVisibility(R.id.widget_status, View.VISIBLE)
                    setViewVisibility(R.id.widget_server, View.VISIBLE)
                    setViewVisibility(R.id.widget_updated_at, View.VISIBLE)
                    setTextViewText(R.id.widget_status, context.getString(R.string.widget_last_result))
                    setTextViewText(R.id.widget_download_value, String.format("%.1f", download))
                    setTextViewText(R.id.widget_upload_value, String.format("%.1f", upload))
                    setTextViewText(R.id.widget_ping_value, ping.toString())
                    setTextViewText(
                        R.id.widget_server,
                        if (server.isBlank()) context.getString(R.string.widget_unknown_server) else server
                    )
                    setTextViewText(
                        R.id.widget_updated_at,
                        context.getString(R.string.widget_updated_prefix, formatTimeAgo(context, timestamp))
                    )
                }
            }
        }

        private fun formatTimeAgo(context: Context, timestamp: Long): String {
            if (timestamp <= 0L) return context.getString(R.string.widget_just_now)
            val diff = System.currentTimeMillis() - timestamp
            return when {
                diff < 60_000L -> context.getString(R.string.widget_just_now)
                diff < 3_600_000L -> context.getString(R.string.widget_minutes_ago, diff / 60_000L)
                diff < 86_400_000L -> context.getString(R.string.widget_hours_ago, diff / 3_600_000L)
                else -> context.getString(R.string.widget_days_ago, diff / 86_400_000L)
            }
        }
    }
}
