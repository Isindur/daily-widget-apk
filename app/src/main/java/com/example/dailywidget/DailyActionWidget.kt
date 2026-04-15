package com.example.dailywidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.RemoteViews
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

class DailyActionWidget : AppWidgetProvider() {

    companion object {
        const val PREFS = "daily_action"
        const val ACTION_TOGGLE = "TOGGLE_ACTION"
        const val ACTION_RESET = "RESET_AT_MIDNIGHT"

        private const val KEY_DONE = "done"
        private const val KEY_DATE = "date"
        private const val KEY_LAST_TOGGLE_MS = "lastToggleMs"

        private const val TOGGLE_LOCK_MS = 2500L // 🔒 2.5s
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_TOGGLE -> {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

                // 📅 Si ha cambiado el día, fuerza estado "no hecho" antes de toggle
                ensureTodayOrReset(prefs)

                val now = System.currentTimeMillis()
                val lastToggle = prefs.getLong(KEY_LAST_TOGGLE_MS, 0L)

                // 🔒 Antidoble click / pulsación accidental
                if (now - lastToggle < TOGGLE_LOCK_MS) return

                val wasDone = isDoneToday(prefs)

                prefs.edit()
                    .putBoolean(KEY_DONE, !wasDone)      // ✅ Toggle real
                    .putLong(KEY_DATE, now)
                    .putLong(KEY_LAST_TOGGLE_MS, now)
                    .apply()

                // 📳 Vibración solo si el toggle se acepta
                vibrateClick(context)

                update(context)
                scheduleNextMidnightReset(context) // 📅 asegura reset exacto del próximo día
            }

            ACTION_RESET -> {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean(KEY_DONE, false)
                    .putLong(KEY_DATE, 0L)
                    .putLong(KEY_LAST_TOGGLE_MS, 0L)
                    .apply()

                update(context)
                scheduleNextMidnightReset(context) // reprograma la siguiente medianoche
            }

            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED -> {
                // Reprograma la medianoche si el sistema cambia hora/zona, o tras reinicio/actualización
                scheduleNextMidnightReset(context)
                update(context)
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context)
        scheduleNextMidnightReset(context)
    }

    private fun update(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ensureTodayOrReset(prefs)

        val doneToday = isDoneToday(prefs)
        val views = RemoteViews(context.packageName, R.layout.widget_daily_action)

        val bg = if (doneToday) R.drawable.bg_checked else R.drawable.bg_unchecked
        // RemoteViews no tiene setBackgroundResource directo: usamos setInt
        views.setInt(R.id.btn, "setBackgroundResource", bg)

        val intent = Intent(context, DailyActionWidget::class.java).apply { action = ACTION_TOGGLE }
        val pi = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn, pi)

        AppWidgetManager.getInstance(context).updateAppWidget(
