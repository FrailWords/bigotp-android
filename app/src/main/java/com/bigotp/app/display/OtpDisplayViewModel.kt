package com.bigotp.app.display

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bigotp.app.onboarding.OnboardingStore
import com.bigotp.app.parser.OtpResult
import com.bigotp.app.parser.OtpType
import com.bigotp.app.parser.UrgencyLevel
import com.bigotp.app.tts.OtpSpeaker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class OtpDisplayUiState(
    val result: OtpResult,
    val secondsRemaining: Int,
    val totalSeconds: Int,
    val isSpeaking: Boolean = false,
    val copied: Boolean = false,
    val copiedSecondsLeft: Int = 0
)

private const val PAYMENT_SECONDS       = 90
private const val LOGIN_SECONDS_DEFAULT = 60

class OtpDisplayViewModel(
    app: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(app) {

    // Fires when the countdown reaches zero — Activity observes this to call finish().
    private val _dismissEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dismissEvent: SharedFlow<Unit> = _dismissEvent.asSharedFlow()

    val speaker = OtpSpeaker(app).also { s ->
        s.onSpeakingChanged = { speaking ->
            _uiState.value = _uiState.value.copy(isSpeaking = speaking)
        }
    }

    // Null-safe retrieval — can be null on process-death recreation where the
    // system discards the Intent extras before restoring the ViewModel.
    private val initialResult: OtpResult? = savedStateHandle.get<OtpResult>(EXTRA_OTP_RESULT)

    @Volatile private var loginExpirySeconds = LOGIN_SECONDS_DEFAULT
    @Volatile private var ttsEnabled = true

    private val _uiState = MutableStateFlow(
        OtpDisplayUiState(
            result           = initialResult
                ?: OtpResult("", OtpType.LOGIN, "", rawMessage = "", confidence = 0f),
            secondsRemaining = if (initialResult?.type == OtpType.PAYMENT) PAYMENT_SECONDS
                               else LOGIN_SECONDS_DEFAULT,
            totalSeconds     = if (initialResult?.type == OtpType.PAYMENT) PAYMENT_SECONDS
                               else LOGIN_SECONDS_DEFAULT
        )
    )
    val uiState: StateFlow<OtpDisplayUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var clipClearJob: Job? = null

    init {
        if (initialResult == null) {
            // Null result on process-death recreation — nothing to display, close immediately.
            _dismissEvent.tryEmit(Unit)
        } else {
            viewModelScope.launch {
                val store = OnboardingStore(getApplication())
                loginExpirySeconds = store.loginExpiryMinutes.first() * 60
                ttsEnabled = store.isTtsEnabled.first()

                val total = totalSecondsFor(initialResult.type)
                _uiState.value = _uiState.value.copy(secondsRemaining = total, totalSeconds = total)
                startCountdown(total)

                if (!speaker.isTalkBackActive() && ttsEnabled) {
                    speaker.speakOtp(initialResult)
                }
            }
        }
    }

    private fun totalSecondsFor(type: OtpType) =
        if (type == OtpType.PAYMENT) PAYMENT_SECONDS else loginExpirySeconds

    private fun startCountdown(total: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = total
            while (remaining >= 0) {
                val urgency = when {
                    remaining > 45 -> UrgencyLevel.HEALTHY
                    remaining > 15 -> UrgencyLevel.WARNING
                    remaining > 0  -> UrgencyLevel.CRITICAL
                    else           -> UrgencyLevel.EXPIRED
                }
                val updatedResult = _uiState.value.result.copy(urgencyLevel = urgency)
                _uiState.value = _uiState.value.copy(
                    result           = updatedResult,
                    secondsRemaining = remaining
                )
                if (remaining == 0) {
                    _dismissEvent.tryEmit(Unit)
                    break
                }
                delay(1_000L)
                remaining--
            }
        }
    }

    fun updateResult(result: OtpResult) {
        countdownJob?.cancel()
        val newTotal = totalSecondsFor(result.type)
        _uiState.value = OtpDisplayUiState(
            result           = result,
            secondsRemaining = newTotal,
            totalSeconds     = newTotal
        )
        startCountdown(newTotal)
        if (!speaker.isTalkBackActive() && ttsEnabled) {
            speaker.speakOtp(result)
        }
    }

    fun stopTTS() {
        speaker.stop()
    }

    fun reSpeak() {
        speaker.speakDigitsOnly(_uiState.value.result.code)
    }

    fun markUsed() {
        countdownJob?.cancel()
        val expired = _uiState.value.result.copy(urgencyLevel = UrgencyLevel.EXPIRED)
        _uiState.value = _uiState.value.copy(result = expired, secondsRemaining = 0)
    }

    fun copyToClipboard() {
        val code = _uiState.value.result.code
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("OTP", code))
        _uiState.value = _uiState.value.copy(copied = true, copiedSecondsLeft = 60)

        clipClearJob?.cancel()
        clipClearJob = viewModelScope.launch {
            var secs = 60
            while (secs > 0) {
                _uiState.value = _uiState.value.copy(copiedSecondsLeft = secs)
                delay(1_000L)
                secs--
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cm.clearPrimaryClip()
            }
            _uiState.value = _uiState.value.copy(copied = false, copiedSecondsLeft = 0)
        }
    }

    override fun onCleared() {
        speaker.shutdown()
        super.onCleared()
    }

    companion object {
        const val EXTRA_OTP_RESULT = "otp_result"
    }
}
