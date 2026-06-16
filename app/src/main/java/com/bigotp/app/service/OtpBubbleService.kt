package com.bigotp.app.service

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.bigotp.app.display.OtpDisplayActivity
import com.bigotp.app.display.OtpDisplayViewModel
import com.bigotp.app.onboarding.OnboardingStore
import com.bigotp.app.parser.OtpResult
import com.bigotp.app.parser.OtpType
import com.bigotp.app.tts.OtpSpeaker
import com.bigotp.app.ui.theme.BigOTPTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

// Shared UI state for the bubble composable
data class BubbleUiState(
    val result: OtpResult,
    val secondsRemaining: Int,
    val totalSeconds: Int,
    val isSpeaking: Boolean = false,
    val ttsEnabled: Boolean = true
)

class OtpBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var speaker: OtpSpeaker
    private var outerView: DraggableFrameLayout? = null
    private val lifecycleOwner = OverlayLifecycleOwner()

    private val _bubbleState = MutableStateFlow<BubbleUiState?>(null)
    val bubbleState: StateFlow<BubbleUiState?> = _bubbleState.asStateFlow()

    private var currentResult: OtpResult? = null
    private var countdownJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleOwner.start()
        speaker = OtpSpeaker(this).also { s ->
            s.onSpeakingChanged = { speaking ->
                _bubbleState.value = _bubbleState.value?.copy(isSpeaking = speaking)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val result = getResult(intent)
                if (result != null) showBubble(result)
            }
            ACTION_DISMISS -> dismissBubble()
            ACTION_UPDATE -> {
                val result = getResult(intent)
                if (result != null && outerView != null) showBubble(result)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        dismissBubble(andStop = false)  // already being destroyed; no need to call stopSelf
        serviceScope.cancel()
        lifecycleOwner.stop()
        speaker.shutdown()
        super.onDestroy()
    }

    private fun getResult(intent: Intent): OtpResult? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_OTP_RESULT, OtpResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_OTP_RESULT)
        }

    private fun showBubble(result: OtpResult) {
        if (!Settings.canDrawOverlays(this)) return

        currentResult = result
        val totalSeconds = if (result.type == OtpType.PAYMENT) 90 else 60

        if (outerView != null) {
            // Dismiss and recreate — more reliable than updating a service-hosted
            // ComposeView in place, which may not recompose when the OTP changes.
            // andStop=false: we're immediately showing a new bubble, keep the service alive.
            dismissBubble(andStop = false)
        }

        _bubbleState.value = BubbleUiState(result, totalSeconds, totalSeconds)

        // Read TTS setting once, update UI state and conditionally auto-speak.
        serviceScope.launch {
            val ttsEnabled = OnboardingStore(this@OtpBubbleService).isTtsEnabled.first()
            _bubbleState.value = _bubbleState.value?.copy(ttsEnabled = ttsEnabled)
            if (ttsEnabled && !speaker.isTalkBackActive() && !OtpDisplayActivity.isVisible) {
                speaker.speakOtp(result)
            }
        }

        val params = createWindowParams()
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                BigOTPTheme {
                    val state by _bubbleState.collectAsState()
                    state?.let { s ->
                        BubbleCard(
                            state       = s,
                            onDismiss   = { dismissBubble() },
                            onTap       = { openFullDisplay() },
                            onCopy      = { copyCode() },
                            onTtsToggle = { toggleTTS() }
                        )
                    }
                }
            }
        }

        // Lifecycle owners must be set on the WindowManager root view (wrapper),
        // not on the ComposeView child. WindowRecomposer walks UP the tree from
        // the root to find them; tags on a child are invisible to that search.
        val wrapper = DraggableFrameLayout(this, windowManager, params)
        wrapper.setViewTreeLifecycleOwner(lifecycleOwner)
        wrapper.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        wrapper.addView(composeView)
        outerView = wrapper
        windowManager.addView(wrapper, params)
        startCountdown(totalSeconds)
    }

    // andStop=true (default): dismiss and stop the service.
    // andStop=false: dismiss so a new bubble can be shown immediately (service keeps running).
    private fun dismissBubble(andStop: Boolean = true) {
        speaker.stop()
        countdownJob?.cancel()
        outerView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        outerView = null
        _bubbleState.value = null
        if (andStop) stopSelf()
    }

    private fun copyCode() {
        val code = currentResult?.code ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("OTP", code))
    }

    private fun toggleTTS() {
        val state = _bubbleState.value ?: return
        if (state.isSpeaking) {
            speaker.stop()
        } else {
            speaker.speakDigitsOnly(state.result.code)
        }
    }

    private fun startCountdown(totalSeconds: Int) {
        countdownJob?.cancel()
        val startedAt = System.currentTimeMillis()
        countdownJob = serviceScope.launch {
            while (true) {
                val elapsed    = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                val remaining  = (totalSeconds - elapsed).coerceAtLeast(0)
                _bubbleState.value = _bubbleState.value?.copy(secondsRemaining = remaining)
                if (remaining <= 0) { dismissBubble(); break }
                delay(1_000)
            }
        }
    }

    private fun openFullDisplay() {
        val result = currentResult ?: return
        val intent = Intent(this, OtpDisplayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(OtpDisplayViewModel.EXTRA_OTP_RESULT, result)
        }
        startActivity(intent)
    }

    @Suppress("DEPRECATION")
    private fun createWindowParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // FLAG_SHOW_WHEN_LOCKED is deprecated for activities (use setShowWhenLocked instead),
        // but for TYPE_APPLICATION_OVERLAY windows it remains the only mechanism available.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.END
        x = 16
        y = 120
    }

    companion object {
        const val ACTION_SHOW      = "com.bigotp.app.BUBBLE_SHOW"
        const val ACTION_DISMISS   = "com.bigotp.app.BUBBLE_DISMISS"
        const val ACTION_UPDATE    = "com.bigotp.app.BUBBLE_UPDATE"
        const val EXTRA_OTP_RESULT = "otp_result"
    }
}

// ── Draggable wrapper ─────────────────────────────────────────────────────────
// Intercepts touch events only when the user is dragging. Taps pass through
// to the ComposeView inside so the close button and card onClick work normally.

private class DraggableFrameLayout(
    context: Context,
    private val wm: WindowManager,
    private val params: WindowManager.LayoutParams
) : FrameLayout(context) {

    private var isDragging   = false
    private var initialX     = 0
    private var initialY     = 0
    private var initialRawX  = 0f
    private var initialRawY  = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX    = params.x
                initialY    = params.y
                initialRawX = ev.rawX
                initialRawY = ev.rawY
                isDragging  = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    val dx = abs(ev.rawX - initialRawX)
                    val dy = abs(ev.rawY - initialRawY)
                    if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) isDragging = true
                }
                if (isDragging) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging = false
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    params.x = (initialX - (ev.rawX - initialRawX).toInt()).coerceAtLeast(0)
                    params.y = (initialY - (ev.rawY - initialRawY).toInt()).coerceAtLeast(0)
                    try { wm.updateViewLayout(this, params) } catch (_: Exception) {}
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return false
    }

    companion object {
        private const val DRAG_THRESHOLD = 10f
    }
}

// ── Lifecycle owner for ComposeView hosted in a Service ───────────────────────

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry              = LifecycleRegistry(this)
    private val savedStateRegistryController   = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun start() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

// ── Bubble composable ─────────────────────────────────────────────────────────

@Composable
private fun BubbleCard(
    state: BubbleUiState,
    onDismiss: () -> Unit,
    onTap: () -> Unit,
    onCopy: () -> Unit,
    onTtsToggle: () -> Unit
) {
    val urgencyColor = when {
        state.secondsRemaining > 45 -> MaterialTheme.colorScheme.primary
        state.secondsRemaining > 15 -> MaterialTheme.colorScheme.tertiary
        else                        -> MaterialTheme.colorScheme.error
    }
    val fraction = if (state.totalSeconds > 0)
        (state.secondsRemaining.toFloat() / state.totalSeconds).coerceIn(0f, 1f)
    else 0f

    ElevatedCard(
        onClick   = onTap,
        modifier  = Modifier.widthIn(min = 220.dp, max = 280.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        shape     = RoundedCornerShape(16.dp)
    ) {
        // Urgency stripe along the top
        LinearProgressIndicator(
            progress   = { fraction },
            modifier   = Modifier.fillMaxWidth().height(3.dp),
            color      = urgencyColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {

            // Source name + dismiss button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text     = state.result.sourceName,
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick  = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        modifier           = Modifier.size(18.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // OTP code — spaced digits, always visible
            val codeDesc = "Code: ${state.result.code.map { it.toString() }.joinToString(", ")}"
            Text(
                text       = state.result.code.chunked(1).joinToString("  "),
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                color      = urgencyColor,
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = codeDesc }
            )

            // Action row: TTS toggle + copy
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Time remaining label
                Text(
                    text     = if (state.secondsRemaining > 0) "${state.secondsRemaining}s" else "Expired",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = urgencyColor,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                IconButton(
                    onClick  = onTtsToggle,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector        = if (state.isSpeaking) Icons.Rounded.StopCircle
                                             else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = if (state.isSpeaking) "Stop reading" else "Read code aloud",
                        modifier           = Modifier.size(20.dp),
                        tint               = if (state.isSpeaking) MaterialTheme.colorScheme.error
                                             else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick  = onCopy,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        modifier           = Modifier.size(20.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
