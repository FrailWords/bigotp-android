package com.bigotp.app.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore("onboarding")

private val KEY_COMPLETE                = booleanPreferencesKey("onboarding_complete")
private val KEY_TTS_ENABLED             = booleanPreferencesKey("tts_enabled")
private val KEY_LOGIN_EXPIRY_MINS       = intPreferencesKey("login_expiry_minutes")
private val KEY_BUBBLE_PROMPT_DISMISSED = booleanPreferencesKey("bubble_prompt_dismissed")
private val KEY_COMPACT_MODE            = booleanPreferencesKey("compact_mode")

class OnboardingStore(private val context: Context) {

    val isComplete: Flow<Boolean> =
        context.onboardingDataStore.data.map { it[KEY_COMPLETE] ?: false }

    val isTtsEnabled: Flow<Boolean> =
        context.onboardingDataStore.data.map { it[KEY_TTS_ENABLED] ?: true }

    val loginExpiryMinutes: Flow<Int> =
        context.onboardingDataStore.data.map { it[KEY_LOGIN_EXPIRY_MINS] ?: 3 }

    val isBubblePromptDismissed: Flow<Boolean> =
        context.onboardingDataStore.data.map { it[KEY_BUBBLE_PROMPT_DISMISSED] ?: false }

    val isCompactMode: Flow<Boolean> =
        context.onboardingDataStore.data.map { it[KEY_COMPACT_MODE] ?: true }

    suspend fun setComplete() {
        context.onboardingDataStore.edit { it[KEY_COMPLETE] = true }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.onboardingDataStore.edit { it[KEY_TTS_ENABLED] = enabled }
    }

    suspend fun setLoginExpiryMinutes(minutes: Int) {
        context.onboardingDataStore.edit { it[KEY_LOGIN_EXPIRY_MINS] = minutes }
    }

    suspend fun setBubblePromptDismissed() {
        context.onboardingDataStore.edit { it[KEY_BUBBLE_PROMPT_DISMISSED] = true }
    }

    suspend fun setCompactMode(enabled: Boolean) {
        context.onboardingDataStore.edit { it[KEY_COMPACT_MODE] = enabled }
    }
}
