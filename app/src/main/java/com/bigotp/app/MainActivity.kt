package com.bigotp.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.bigotp.app.onboarding.OnboardingActivity
import com.bigotp.app.onboarding.OnboardingStore
import com.bigotp.app.ui.theme.BigOTPTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class AppScreen { Main, Settings, ParserTest }

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val complete = OnboardingStore(this@MainActivity).isComplete.first()
            if (!complete) {
                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                finish()
                return@launch
            }
            showContent()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.clearExpiredHistory()
        viewModel.startPollingService()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPollingService()
    }

    private fun showContent() {
        enableEdgeToEdge()
        setContent {
            BigOTPTheme {
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Main) }

                BackHandler(enabled = currentScreen != AppScreen.Main) {
                    currentScreen = AppScreen.Main
                }

                when (currentScreen) {
                    AppScreen.Main -> MainScreen(
                        viewModel     = viewModel,
                        onOpenSettings = { currentScreen = AppScreen.Settings }
                    )
                    AppScreen.Settings -> SettingsScreen(
                        viewModel        = viewModel,
                        onBack           = { currentScreen = AppScreen.Main },
                        onOpenParserTest = {
                            if (BuildConfig.DEBUG) currentScreen = AppScreen.ParserTest
                        }
                    )
                    AppScreen.ParserTest -> {
                        if (BuildConfig.DEBUG) {
                            ParserTestScreen(onBack = { currentScreen = AppScreen.Settings })
                        } else {
                            currentScreen = AppScreen.Main
                        }
                    }
                }
            }
        }
    }
}
