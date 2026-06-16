package com.bigotp.app.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.bigotp.app.config.ConfigRepository
import com.bigotp.app.display.OtpDisplayActivity
import com.bigotp.app.history.OtpHistoryStore
import com.bigotp.app.onboarding.OnboardingStore
import com.bigotp.app.parser.OtpParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "OtpNotifService"

class OtpNotificationService : NotificationListenerService() {

    private val serviceJob   = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var configRepository: ConfigRepository
    private lateinit var historyStore: OtpHistoryStore

    private var heartbeatJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        configRepository = ConfigRepository(this)
        historyStore     = OtpHistoryStore(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        startHeartbeat()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        heartbeatJob?.cancel()
        // NonCancellable ensures the write completes even if serviceScope is being
        // cancelled concurrently (onDestroy racing with this callback).
        serviceScope.launch(NonCancellable) { writeHeartbeat(0L) }
        // Ask the system to rebind immediately rather than waiting for the heartbeat worker.
        NotificationListenerService.requestRebind(
            ComponentName(this, OtpNotificationService::class.java)
        )
    }

    override fun onDestroy() {
        heartbeatJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (true) {
                writeHeartbeat(System.currentTimeMillis())
                delay(30_000)
            }
        }
    }

    private suspend fun writeHeartbeat(timestamp: Long) {
        applicationContext.heartbeatDataStore.edit { prefs ->
            prefs[HEARTBEAT_KEY] = timestamp
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras  = sbn.notification.extras
        val title   = extras.getString(Notification.EXTRA_TITLE)
        val text    = extras.getString(Notification.EXTRA_TEXT)
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        val notificationText = listOfNotNull(bigText, text)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")

        if (notificationText.isBlank()) return

        serviceScope.launch {
            val patterns = configRepository.getPatterns()
            val result = OtpParser.parse(
                notificationText  = notificationText,
                notificationTitle = title,
                sourcePackage     = sbn.packageName,
                patterns          = patterns
            )

            if (result == null || result.confidence < MIN_CONFIDENCE) {
                Log.d(TAG, "parse=false pkg=${sbn.packageName}")
                return@launch
            }

            Log.d(TAG, "parse=true confidence=${result.confidence}")

            val compactMode = OnboardingStore(applicationContext).isCompactMode.first()

            if (!compactMode) {
                // Full-screen display — launched first, within the notification-processing window
                // that exempts NLS from Android 10+ background-activity-start restrictions.
                val displayIntent = Intent(applicationContext, OtpDisplayActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_OTP_RESULT, result)
                }
                startActivity(displayIntent)
                Log.d(TAG, "startActivity dispatched")
            }

            // Show the floating bubble (only if overlay permission is granted).
            if (Settings.canDrawOverlays(applicationContext)) {
                val bubbleIntent = Intent(applicationContext, OtpBubbleService::class.java).apply {
                    action = OtpBubbleService.ACTION_SHOW
                    putExtra(OtpBubbleService.EXTRA_OTP_RESULT, result)
                }
                startService(bubbleIntent)
            }

            // Persist to history — wrapped so a Keystore or DataStore failure never
            // silently aborts the display path above.
            try {
                historyStore.add(result)
            } catch (e: Exception) {
                Log.e(TAG, "historyStore.add failed", e)
            }
        }
    }

    companion object {
        const val EXTRA_OTP_RESULT  = "otp_result"
        private const val MIN_CONFIDENCE = 0.7f
    }
}
