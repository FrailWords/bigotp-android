package com.bigotp.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bigotp.app.config.ConfigRefreshWorker
import com.bigotp.app.service.ServiceHeartbeatWorker
import java.util.concurrent.TimeUnit

const val OTP_CHANNEL_ID = "otp_alerts"

class BigOtpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleConfigRefresh()
        ServiceHeartbeatWorker.schedule(this)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            OTP_CHANNEL_ID,
            getString(R.string.otp_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.otp_channel_description)
            enableVibration(true)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun scheduleConfigRefresh() {
        val request = PeriodicWorkRequestBuilder<ConfigRefreshWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "config_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
