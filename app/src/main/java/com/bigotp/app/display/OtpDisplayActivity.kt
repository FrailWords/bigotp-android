package com.bigotp.app.display

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bigotp.app.parser.OtpResult
import com.bigotp.app.service.OtpBubbleService
import com.bigotp.app.ui.theme.BigOTPTheme
import kotlinx.coroutines.launch

class OtpDisplayActivity : ComponentActivity() {

    private val viewModel: OtpDisplayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyLockScreenFlags()

        // Auto-dismiss when the countdown expires (or when there is no result to show).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dismissEvent.collect { dismissWithBubble() }
            }
        }

        setContent {
            BigOTPTheme {
                OtpDisplayScreen(
                    viewModel = viewModel,
                    onDone    = { dismissWithBubble() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
    }

    override fun onPause() {
        super.onPause()
        isVisible = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val key = OtpDisplayViewModel.EXTRA_OTP_RESULT
        val result: OtpResult? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, OtpResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
        result?.let { viewModel.updateResult(it) }
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private fun dismissWithBubble() {
        // Dismiss the floating bubble before finishing this activity.
        stopService(Intent(this, OtpBubbleService::class.java))
        finish()
    }

    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    companion object {
        // True while this activity is in the foreground — checked by OtpNotificationService
        // to decide whether startActivity (foreground path) or a notification should be used.
        var isVisible = false
    }
}
