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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

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

                // Si ha cambiado el día, resetea antes de aplicar toggle
                ensureTodayOrReset(prefs)

                val now = System.currentTimeMillis()
                val lastToggle = prefs.getLong(KEY_LAST_TOGGLE_MS, 0L)

                // 🔒 Bloqueo 2.5s para evitar doble toque accidental
                if (now - lastToggle < TOGGLE_LOCK_MS) return

                val wasDone = isDoneToday(prefs)

                prefs.edit()
                    .putBoolean(KEY_DONE, !wasDone)   // Toggle real
                    .putLong(KEY_DATE, now)
                    .putLong(KEY_LAST_TOGGLE_MS, now)
                    .apply()

                // 📳 Vibración al pulsar (solo si se acepta el toggle)
                vibrateClick(context)

                update(context)
                scheduleNextMidnightReset(context)
            }

            ACTION_RESET -> {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean(KEY_DONE, false)
                    .putLong(KEY_DATE, 0L)
                    .putLong(KEY_LAST_TOGGLE_MS, 0L)
                    .apply()

                update(context)
                scheduleNextMidnightReset(context)
            }

            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED -> {
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

        // RemoteViews no expone setBackgroundResource directo; se invoca vía setInt
        views.setInt(R.id.btn, "setBackgroundResource", bg)

        val intent = Intent(context, DailyActionWidget::class.java).apply { action = ACTION_TOGGLE }
        val pi = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn, pi)

        AppWidgetManager.getInstance(context).updateAppWidget(
            ComponentName(context, DailyActionWidget::class.java),
            views
        )
    }

    private fun ensureTodayOrReset(prefs: SharedPreferences) {
        val done = prefs.getBoolean(KEY_DONE, false)
        if (!done) return

        val last = prefs.getLong(KEY_DATE, 0L)
        if (last == 0L) return

        val f = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val lastDay = f.format(Date(last))
        val today = f.format(Date())

        if (lastDay != today) {
            prefs.edit()
                .putBoolean(KEY_DONE, false)
                .putLong(KEY_DATE, 0L)
                .putLong(KEY_LAST_TOGGLE_MS, 0L)
                .apply()
        }
    }

    private fun isDoneToday(prefs: SharedPreferences): Boolean {
        val done = prefs.getBoolean(KEY_DONE, false)
        val last = prefs.getLong(KEY_DATE, 0L)
        if (!done || last == 0L) return false

        val f = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return f.format(Date(last)) == f.format(Date())
    }

    private fun vibrateClick(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (!vibrator.hasVibrator()) return

        // API 26+ recomendado: VibrationEffect.createOneShot(...) [1](https://stackoverflow.com/questions/45605083/android-vibrate-is-deprecated-how-to-use-vibrationeffect-in-android-api-26)[2](https://developer.android.com/reference/android/os/Vibrator)
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    }

    private fun scheduleNextMidnightReset(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val cal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextMidnight = cal.timeInMillis

        val intent = Intent(context, DailyActionWidget::class.java).apply { action = ACTION_RESET }
        val pi = PendingIntent.getBroadcast(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intentar exacto; si el sistema lo limita, fallback a inexacto (evita crash)
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnight, pi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextMidnight, pi)
            }
        } catch (_: SecurityException) {
            // Puede ocurrir si el sistema exige permiso de alarmas exactas en Android 12+ [3](https://learn.microsoft.com/en-us/dotnet/api/android.app.alarmmanager.setexactandallowwhileidle?view=net-android-35.0)
            alarmManager.set(AlarmManager.RTC_WAKEUP, nextMidnight, pi)
        }
    }
}
