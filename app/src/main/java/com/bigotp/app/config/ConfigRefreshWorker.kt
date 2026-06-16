package com.bigotp.app.config

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ConfigRefreshWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = ConfigRepository(ctx)

        if (isMetered(ctx)) {
            val thirtyDaysMs = 30L * 24 * 60 * 60 * 1_000
            if (System.currentTimeMillis() - repo.getLastFetchTime() < thirtyDaysMs) {
                return Result.success()
            }
        }

        repo.refreshInBackground()
        return Result.success()
    }

    private fun isMetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
