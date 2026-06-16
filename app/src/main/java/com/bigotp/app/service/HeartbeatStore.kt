package com.bigotp.app.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay

val Context.heartbeatDataStore by preferencesDataStore("heartbeat")
val HEARTBEAT_KEY = longPreferencesKey("service_heartbeat")

suspend fun nudgeNotificationListener(context: Context) {
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
}
