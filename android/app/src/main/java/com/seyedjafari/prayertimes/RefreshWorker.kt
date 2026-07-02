package com.seyedjafari.prayertimes

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Periodically re-renders every placed widget so the day's times stay current. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PrayerWidgetProvider.updateAllBlocking(applicationContext)
        return Result.success()
    }
}

object RefreshScheduler {
    private const val WORK_NAME = "prayer_refresh"

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
