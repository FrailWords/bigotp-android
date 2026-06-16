package com.bigotp.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import com.bigotp.app.parser.OtpResult
import com.bigotp.app.parser.OtpType
import java.util.Locale

private const val DIGIT_PAUSE_MS = 400L

class OtpSpeaker(context: Context) {

    var onSpeakingChanged: (Boolean) -> Unit = {}

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
            if (ttsReady) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        if (utteranceId == "otp_start") onSpeakingChanged(true)
                    }
                    override fun onDone(utteranceId: String) {
                        if (utteranceId == "otp_end") onSpeakingChanged(false)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) {
                        onSpeakingChanged(false)
                    }
                    // Two-param override required for API 21+ to actually receive errors
                    override fun onError(utteranceId: String, errorCode: Int) {
                        onSpeakingChanged(false)
                    }
                })
            }
        }
    }

    fun isTalkBackActive(): Boolean =
        am.isEnabled && am.isTouchExplorationEnabled

    fun speakOtp(result: OtpResult) {
        if (!ttsReady) return
        if (isTalkBackActive()) return  // let TalkBack read the screen; don't fight it
        val engine = tts ?: return

        engine.stop()

        if (result.type == OtpType.PAYMENT) {
            val amountClause = if (result.amountString != null)
                "This approves ${result.amountString} from ${result.sourceName}."
            else
                "This is a payment from ${result.sourceName}."
            // Preamble → digits → warning (warning must come AFTER digits per spec)
            speak(engine, "Payment OTP. $amountClause Your code is", "otp_start")
            enqueueDigits(engine, result.code, finalId = "otp_digits_done")
            speak(engine, "Never share this code with anyone — not even bank staff", "otp_end")
        } else {
            speak(engine, "Your code is", "otp_start")
            enqueueDigits(engine, result.code)
        }
    }

    // Used for user-initiated re-reads and onboarding preview — TalkBack guard is intentionally
    // absent here so explicit user actions still work even when TalkBack is enabled.
    fun speakDigitsOnly(code: String) {
        if (!ttsReady) return
        val engine = tts ?: return
        engine.stop()
        speak(engine, "Your code is", "otp_start")
        enqueueDigits(engine, code)
    }

    fun stop() {
        tts?.stop()
        onSpeakingChanged(false)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun enqueueDigits(engine: TextToSpeech, code: String, finalId: String = "otp_end") {
        val chars = code.filter { it.isLetterOrDigit() }
        if (chars.isEmpty()) return
        chars.forEachIndexed { index, char ->
            val id = if (index == chars.lastIndex) finalId else "digit_$index"
            speak(engine, char.toString(), id)
            if (index < chars.lastIndex) {
                engine.playSilentUtterance(DIGIT_PAUSE_MS, TextToSpeech.QUEUE_ADD, "silence_$index")
            }
        }
    }

    private fun speak(engine: TextToSpeech, text: String, utteranceId: String) {
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }
}
