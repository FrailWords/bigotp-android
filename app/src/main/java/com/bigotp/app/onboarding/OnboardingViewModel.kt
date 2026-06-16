package com.bigotp.app.onboarding

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bigotp.app.tts.OtpSpeaker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val store = OnboardingStore(application)

    val speaker = OtpSpeaker(application)

    private val _currentScreen = MutableStateFlow(0)
    val currentScreen: StateFlow<Int> = _currentScreen.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    private val _hasReturnedFromSettings = MutableStateFlow(false)
    val hasReturnedFromSettings: StateFlow<Boolean> = _hasReturnedFromSettings.asStateFlow()

    private val _isOverlayGranted = MutableStateFlow(false)
    val isOverlayGranted: StateFlow<Boolean> = _isOverlayGranted.asStateFlow()

    private val _hasReturnedFromOverlaySettings = MutableStateFlow(false)
    val hasReturnedFromOverlaySettings: StateFlow<Boolean> = _hasReturnedFromOverlaySettings.asStateFlow()

    private val _isGoingForward = MutableStateFlow(true)
    val isGoingForward: StateFlow<Boolean> = _isGoingForward.asStateFlow()

    private val _exitSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitSignal = _exitSignal.asSharedFlow()

    val isOnboardingComplete: Flow<Boolean> = store.isComplete

    private var permissionJob: Job? = null

    // ── Navigation ─────────────────────────────────────────────────────────────

    fun advance() {
        _isGoingForward.value = true
        val next = _currentScreen.value + 1
        if (next > 5) return
        if (_currentScreen.value == 2) stopPermissionPolling()
        _currentScreen.value = next
        if (next == 4 && !speaker.isTalkBackActive()) {
            speaker.speakDigitsOnly("4721")
        }
    }

    fun goBack() {
        val prev = _currentScreen.value - 1
        if (prev < 0) {
            _exitSignal.tryEmit(Unit)
            return
        }
        _isGoingForward.value = false
        if (_currentScreen.value == 2) stopPermissionPolling()
        _currentScreen.value = prev
    }

    fun skipOverlayPermission() {
        advance()
    }

    // ── Permission checks — called from Activity.onResume ──────────────────────

    fun checkPermissionOnResume() {
        when (_currentScreen.value) {
            2 -> {
                _hasReturnedFromSettings.value = true
                if (isNotificationListenerEnabled()) {
                    _isPermissionGranted.value = true
                    advance()
                } else {
                    startPermissionPolling()
                }
            }
            3 -> {
                _hasReturnedFromOverlaySettings.value = true
                if (Settings.canDrawOverlays(getApplication())) {
                    _isOverlayGranted.value = true
                    advance()
                }
            }
        }
    }

    fun startPermissionPolling() {
        if (permissionJob?.isActive == true) return
        permissionJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                if (isNotificationListenerEnabled()) {
                    _isPermissionGranted.value = true
                    advance()
                    break
                }
            }
        }
    }

    fun stopPermissionPolling() {
        permissionJob?.cancel()
        permissionJob = null
    }

    // ── TTS preference ─────────────────────────────────────────────────────────

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setTtsEnabled(enabled) }
    }

    // ── Completion ─────────────────────────────────────────────────────────────

    fun completeOnboarding() {
        viewModelScope.launch { store.setComplete() }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            "enabled_notification_listeners"
        )
        return flat?.contains(getApplication<Application>().packageName) == true
    }

    override fun onCleared() {
        speaker.shutdown()
        super.onCleared()
    }
}
