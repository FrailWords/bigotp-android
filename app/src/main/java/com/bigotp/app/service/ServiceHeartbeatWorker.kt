package com.bigotp.app.service

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class ServiceHeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!isNotificationListenerEnabled(applicationContext)) return Result.success()

        val lastHeartbeat = applicationContext.heartbeatDataStore
            .data.first()[HEARTBEAT_KEY] ?: 0L

        val isAlive = lastHeartbeat > 0L &&
            System.currentTimeMillis() - lastHeartbeat < 60_000L

        if (!isAlive) {
            nudgeNotificationListener(applicationContext)
        }

        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceHeartbeatWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(Constraints.NONE)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "service_heartbeat",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
