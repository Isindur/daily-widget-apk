package com.example.dailywidget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class DailyResetWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val p = applicationContext.getSharedPreferences(
            DailyActionWidget.PREFS,
            Context.MODE_PRIVATE
        )
        p.edit().putBoolean("done", false).apply()
        return Result.success()
    }
}
``package com.example.dailywidget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class DailyResetWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val p = applicationContext.getSharedPreferences(
            DailyActionWidget.PREFS,
            Context.MODE_PRIVATE
        )
        p.edit().putBoolean("done", false).apply()
        return Result.success()
    }
}
``