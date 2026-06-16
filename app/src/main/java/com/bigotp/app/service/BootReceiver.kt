package com.bigotp.app.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d("BigOTP_Boot", "BootReceiver fired, action=${intent.action}, requesting rebind")

            ServiceHeartbeatWorker.schedule(context)

            // goAsync lets us do the 500 ms component-toggle on a background coroutine
            // instead of blocking the main thread (ANR risk).
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val componentName = ComponentName(context, OtpNotificationService::class.java)
                    val pm = context.packageManager
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    delay(500)
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    NotificationListenerService.requestRebind(componentName)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
