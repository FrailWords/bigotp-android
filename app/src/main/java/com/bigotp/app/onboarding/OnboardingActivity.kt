package com.bigotp.app.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.bigotp.app.MainActivity
import com.bigotp.app.ui.theme.BigOTPTheme
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            viewModel.exitSignal.collect { finish() }
        }

        setContent {
            BigOTPTheme {
                OnboardingScreen(
                    viewModel = viewModel,
                    onComplete = { navigateToMain() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissionOnResume()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}
