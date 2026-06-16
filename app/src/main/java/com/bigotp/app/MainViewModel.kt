package com.bigotp.app

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bigotp.app.history.LiveEntry
import com.bigotp.app.history.OtpHistoryStore
import com.bigotp.app.onboarding.OnboardingStore
import com.bigotp.app.service.HEARTBEAT_KEY
import com.bigotp.app.service.heartbeatDataStore
import com.bigotp.app.service.nudgeNotificationListener
import com.bigotp.app.tts.OtpSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ServiceAliveState {
    ACTIVE,       // permission granted + heartbeat fresh
    STALE,        // permission granted + heartbeat old (may be dead)
    DISCONNECTED, // permission granted + heartbeat = 0 (confirmed disconnected)
    NO_PERMISSION // permission not granted
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val historyStore  = OtpHistoryStore(application)
    private val settingsStore = OnboardingStore(application)
    private val speaker       = OtpSpeaker(application).also { s ->
        s.onSpeakingChanged = { speaking ->
            if (!speaking) _currentlyReadingId.value = null
        }
    }

    private val _liveHistory = MutableStateFlow<List<LiveEntry>>(emptyList())
    val liveHistory: StateFlow<List<LiveEntry>> = _liveHistory.asStateFlow()

    private val _serviceAliveState = MutableStateFlow(ServiceAliveState.NO_PERMISSION)
    val serviceAliveState: StateFlow<ServiceAliveState> = _serviceAliveState.asStateFlow()

    private val _currentlyReadingId = MutableStateFlow<Long?>(null)
    val currentlyReadingId: StateFlow<Long?> = _currentlyReadingId.asStateFlow()

    private val _bubblePermissionGranted = MutableStateFlow(false)
    val bubblePermissionGranted: StateFlow<Boolean> = _bubblePermissionGranted.asStateFlow()

    val isTtsEnabled: StateFlow<Boolean> = settingsStore.isTtsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val loginExpiryMinutes: StateFlow<Int> = settingsStore.loginExpiryMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)

    val isBubblePromptDismissed: StateFlow<Boolean> = settingsStore.isBubblePromptDismissed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isCompactMode: StateFlow<Boolean> = settingsStore.isCompactMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var pollJob: Job? = null

    fun startPollingService() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                val permGranted = isNotificationListenerActive(getApplication())
                val heartbeat = if (permGranted) {
                    getApplication<Application>().heartbeatDataStore.data.first()[HEARTBEAT_KEY] ?: 0L
                } else 0L
                _serviceAliveState.value = when {
                    !permGranted -> ServiceAliveState.NO_PERMISSION
                    heartbeat == 0L -> ServiceAliveState.DISCONNECTED
                    System.currentTimeMillis() - heartbeat < 60_000L -> ServiceAliveState.ACTIVE
                    else -> ServiceAliveState.STALE
                }
                _bubblePermissionGranted.value = Settings.canDrawOverlays(getApplication())
                _liveHistory.value             = historyStore.getLiveEntriesWithTimestamp()
                    .sortedByDescending { it.receivedAt }
                delay(2_000)
            }
        }
    }

    fun stopPollingService() {
        pollJob?.cancel()
        pollJob = null
    }

    fun attemptRebind() {
        viewModelScope.launch(Dispatchers.IO) {
            nudgeNotificationListener(getApplication())
        }
    }

    fun clearExpiredHistory() {
        viewModelScope.launch { historyStore.clearExpired() }
    }

    fun removeHistoryEntry(receivedAt: Long) {
        viewModelScope.launch { historyStore.removeEntry(receivedAt) }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setTtsEnabled(enabled) }
    }

    fun setLoginExpiryMinutes(minutes: Int) {
        viewModelScope.launch { settingsStore.setLoginExpiryMinutes(minutes) }
    }

    fun dismissBubblePrompt() {
        viewModelScope.launch { settingsStore.setBubblePromptDismissed() }
    }

    fun setCompactMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setCompactMode(enabled) }
    }

    fun speakEntry(entry: LiveEntry) {
        if (speaker.isTalkBackActive()) return
        if (_currentlyReadingId.value == entry.receivedAt) {
            speaker.stop()
            _currentlyReadingId.value = null
        } else {
            speaker.stop()
            _currentlyReadingId.value = entry.receivedAt
            speaker.speakOtp(entry.result)
        }
    }

    override fun onCleared() {
        speaker.shutdown()
        super.onCleared()
    }

    private fun isNotificationListenerActive(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return flat?.contains(context.packageName) == true
    }
}
