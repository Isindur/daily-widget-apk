package com.example.dailywidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.*

class DailyActionWidget : AppWidgetProvider() {

    companion object {
        const val PREFS = "daily_action"
        const val ACTION = "TOGGLE_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("done", true)
                .putLong("date", System.currentTimeMillis())
                .apply()
            update(context)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context)
    }

    private fun update(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val doneToday = isDoneToday(prefs)

        val views = RemoteViews(context.packageName, R.layout.widget_daily_action)
        views.setBackgroundResource(
            R.id.btn,
            if (doneToday) R.drawable.bg_checked else R.drawable.bg_unchecked
        )

        val intent = Intent(context, DailyActionWidget::class.java).apply {
            action = ACTION
        }

        val pi = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.btn, pi)

        AppWidgetManager.getInstance(context)
            .updateAppWidget(
                ComponentName(context, DailyActionWidget::class.java),
                views
            )
    }

    private fun isDoneToday(prefs: SharedPreferences): Boolean {
        val done = prefs.getBoolean("done", false)
        val last = prefs.getLong("date", 0)
        val f = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return done && f.format(Date(last)) == f.format(Date())
    }
}