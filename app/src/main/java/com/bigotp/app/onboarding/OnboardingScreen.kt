package com.bigotp.app.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit
) {
    val screen         by viewModel.currentScreen.collectAsState()
    val isGoingForward by viewModel.isGoingForward.collectAsState()
    val reduced        = reduceMotionEnabled()

    BackHandler {
        viewModel.goBack()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProgressDots(
                currentScreen = screen,
                modifier      = Modifier.padding(top = 56.dp, bottom = 8.dp)
            )

            AnimatedContent(
                targetState    = screen,
                transitionSpec = {
                    val forward = isGoingForward
                    if (reduced) {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    } else if (forward) {
                        slideInHorizontally(tween(300)) { it } togetherWith
                            slideOutHorizontally(tween(300)) { -it }
                    } else {
                        slideInHorizontally(tween(300)) { -it } togetherWith
                            slideOutHorizontally(tween(300)) { it }
                    }
                },
                label = "onboarding_screen"
            ) { targetScreen ->
                when (targetScreen) {
                    0 -> Screen0_Welcome(onNext = { viewModel.advance() })
                    1 -> Screen1_HowItWorks(reduced = reduced, onNext = { viewModel.advance() })
                    2 -> Screen2_Permission(viewModel = viewModel)
                    3 -> Screen3_OverlayPermission(viewModel = viewModel)
                    4 -> Screen4_SoundCheck(viewModel = viewModel)
                    5 -> Screen5_Complete(viewModel = viewModel, onComplete = onComplete)
                    else -> Unit
                }
            }
        }
    }
}

// ── Progress dots (6 steps) ───────────────────────────────────────────────────

@Composable
private fun ProgressDots(currentScreen: Int, modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier.semantics {
            contentDescription = "Step ${currentScreen + 1} of 6"
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(6) { index ->
            val active  = index == currentScreen
            val dotSize by animateDpAsState(
                targetValue   = if (active) 12.dp else 8.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label         = "dot_size_$index"
            )
            val color by animateColorAsState(
                targetValue   = if (active) MaterialTheme.colorScheme.primary
                                else        MaterialTheme.colorScheme.outlineVariant,
                animationSpec = tween(200),
                label         = "dot_color_$index"
            )
            Canvas(
                modifier = Modifier
                    .size(dotSize)
                    .semantics { contentDescription = "" }
            ) {
                drawCircle(color = color, radius = size.minDimension / 2f)
            }
        }
    }
}

// ── Shared layout wrapper ─────────────────────────────────────────────────────

@Composable
private fun ScreenLayout(
    illustrationDescription: String,
    illustration: @Composable () -> Unit,
    headline: String,
    body: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .semantics { contentDescription = illustrationDescription },
            contentAlignment = Alignment.Center
        ) { illustration() }

        Text(
            text      = headline,
            style     = MaterialTheme.typography.displaySmall,
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .fillMaxWidth()
                .semantics { heading() }
        )

        Text(
            text      = body,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )

        content()
    }
}

// ── Screen 0 — Welcome ────────────────────────────────────────────────────────

@Composable
private fun Screen0_Welcome(onNext: () -> Unit) {
    ScreenLayout(
        illustrationDescription = "Phone showing a large code",
        illustration            = { PhoneWithDigitsIllustration() },
        headline                = "See every code clearly",
        body                    = "BigOTP reads your one-time passwords aloud and shows them in big, easy-to-read letters"
    ) {
        Spacer(Modifier.height(8.dp))
        FullWidthButton(text = "Get started", onClick = onNext)
    }
}

@Composable
private fun PhoneWithDigitsIllustration() {
    val primary   = MaterialTheme.colorScheme.primary
    val surface   = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = Modifier.fillMaxSize()) {
        val phoneW = size.width * 0.45f
        val phoneH = size.height * 0.88f
        val phoneL = (size.width - phoneW) / 2f
        val phoneT = (size.height - phoneH) / 2f
        val corner = phoneW * 0.12f

        drawRoundRect(
            color        = surface,
            topLeft      = Offset(phoneL, phoneT),
            size         = Size(phoneW, phoneH),
            cornerRadius = CornerRadius(corner)
        )
        drawRoundRect(
            color        = primary,
            topLeft      = Offset(phoneL, phoneT),
            size         = Size(phoneW, phoneH),
            cornerRadius = CornerRadius(corner),
            style        = Stroke(width = 3.dp.toPx())
        )

        val blockW  = phoneW * 0.22f
        val blockH  = blockW * 1.4f
        val blockY  = phoneT + phoneH * 0.38f
        val gap     = phoneW * 0.06f
        val totalW  = 3 * blockW + 2 * gap
        val startX  = phoneL + (phoneW - totalW) / 2f

        repeat(3) { i ->
            val bx = startX + i * (blockW + gap)
            drawRoundRect(
                color        = primary,
                topLeft      = Offset(bx, blockY),
                size         = Size(blockW, blockH),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }
    }
}

// ── Screen 1 — How it works ───────────────────────────────────────────────────

@Composable
private fun Screen1_HowItWorks(reduced: Boolean, onNext: () -> Unit) {
    ScreenLayout(
        illustrationDescription = "SMS envelope arriving and displaying a code",
        illustration            = { SmsToDigitsIllustration(reduced = reduced) },
        headline                = "Codes appear automatically",
        body                    = "When a code arrives, BigOTP shows it to you instantly — you never have to search for it"
    ) {
        Spacer(Modifier.height(8.dp))
        FullWidthButton(text = "Next", onClick = onNext)
    }
}

@Composable
private fun SmsToDigitsIllustration(reduced: Boolean) {
    val primary    = MaterialTheme.colorScheme.primary
    val surfaceVar = MaterialTheme.colorScheme.surfaceVariant

    if (reduced) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDigitBlocks(primary, surfaceVar, alpha = 1f)
            drawEnvelope(primary, offsetFraction = 0f)
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "sms_anim")

    val envelopeOffset by transition.animateFloat(
        initialValue  = -1f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "envelope_slide"
    )

    val digitAlpha by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "digit_alpha"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawEnvelope(primary, offsetFraction = envelopeOffset)
        drawDigitBlocks(primary, surfaceVar, alpha = digitAlpha)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEnvelope(
    color: Color,
    offsetFraction: Float
) {
    val w  = size.width * 0.28f
    val h  = w * 0.65f
    val cx = size.width * 0.25f + offsetFraction * size.width * 0.3f
    val cy = size.height * 0.45f
    val l  = cx - w / 2f
    val t  = cy - h / 2f

    drawRoundRect(
        color        = color,
        topLeft      = Offset(l, t),
        size         = Size(w, h),
        cornerRadius = CornerRadius(4.dp.toPx()),
        style        = Stroke(width = 2.dp.toPx())
    )
    drawLine(color, Offset(l, t), Offset(cx, cy - h * 0.1f), strokeWidth = 2.dp.toPx())
    drawLine(color, Offset(l + w, t), Offset(cx, cy - h * 0.1f), strokeWidth = 2.dp.toPx())
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDigitBlocks(
    primary: Color,
    surfaceVar: Color,
    alpha: Float
) {
    val blockW = size.width * 0.10f
    val blockH = blockW * 1.4f
    val startX = size.width * 0.52f
    val y      = (size.height - blockH) / 2f
    val gap    = blockW * 0.3f

    repeat(4) { i ->
        drawRoundRect(
            color        = primary.copy(alpha = alpha),
            topLeft      = Offset(startX + i * (blockW + gap), y),
            size         = Size(blockW, blockH),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
    }
}

// ── Screen 2 — Notification permission (rewritten for trust) ─────────────────

@Composable
private fun Screen2_Permission(viewModel: OnboardingViewModel) {
    val context             = LocalContext.current
    val hasReturned         by viewModel.hasReturnedFromSettings.collectAsState()
    val isPermissionGranted by viewModel.isPermissionGranted.collectAsState()
    val showReturnPrompt    = hasReturned && !isPermissionGranted

    ScreenLayout(
        illustrationDescription = "Phone with a notification permission toggle highlighted",
        illustration            = { PermissionToggleIllustration() },
        headline                = "One important permission",
        body                    = "BigOTP needs to see your notifications to find OTP codes.\n\n" +
                                  "We only look for codes — we never read your messages, photos, or personal information. " +
                                  "Nothing from your phone is ever sent anywhere.\n\n" +
                                  "Tap below to open Settings and turn on BigOTP."
    ) {
        Spacer(Modifier.height(8.dp))
        FullWidthButton(
            text    = "Open Settings",
            onClick = {
                if (viewModel.speaker.isTalkBackActive()) {
                    viewModel.speaker.speakDigitsOnly(
                        "Opening Settings now. Find BigOTP in the list and turn it on, then come back here."
                    )
                }
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )

        if (showReturnPrompt) {
            Spacer(Modifier.height(4.dp))
            Text(
                text      = "BigOTP cannot work without this permission. Please open Settings and turn on BigOTP under Notification access.",
                style     = MaterialTheme.typography.bodyLarge,
                color     = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PermissionToggleIllustration() {
    val primary  = MaterialTheme.colorScheme.primary
    val surface  = MaterialTheme.colorScheme.surfaceVariant
    val outline  = MaterialTheme.colorScheme.outline

    Canvas(modifier = Modifier.fillMaxSize()) {
        val phoneW = size.width * 0.40f
        val phoneH = size.height * 0.85f
        val phoneL = (size.width - phoneW) / 2f
        val phoneT = (size.height - phoneH) / 2f
        val corner = phoneW * 0.12f

        drawRoundRect(
            color        = surface,
            topLeft      = Offset(phoneL, phoneT),
            size         = Size(phoneW, phoneH),
            cornerRadius = CornerRadius(corner)
        )
        drawRoundRect(
            color        = outline,
            topLeft      = Offset(phoneL, phoneT),
            size         = Size(phoneW, phoneH),
            cornerRadius = CornerRadius(corner),
            style        = Stroke(width = 2.dp.toPx())
        )

        val toggleW = phoneW * 0.52f
        val toggleH = toggleW * 0.34f
        val toggleL = phoneL + (phoneW - toggleW) / 2f
        val toggleT = phoneT + phoneH * 0.46f
        val toggleR = toggleH / 2f

        drawRoundRect(
            color        = primary,
            topLeft      = Offset(toggleL, toggleT),
            size         = Size(toggleW, toggleH),
            cornerRadius = CornerRadius(toggleR)
        )
        drawCircle(
            color  = Color.White,
            radius = toggleH * 0.38f,
            center = Offset(toggleL + toggleW - toggleR, toggleT + toggleH / 2f)
        )
    }
}

// ── Screen 3 — Overlay permission (new) ──────────────────────────────────────

@Composable
private fun Screen3_OverlayPermission(viewModel: OnboardingViewModel) {
    val context          = LocalContext.current
    val hasReturned      by viewModel.hasReturnedFromOverlaySettings.collectAsState()
    val isOverlayGranted by viewModel.isOverlayGranted.collectAsState()
    val showReturnPrompt = hasReturned && !isOverlayGranted

    var showSkipExplanation by remember { mutableStateOf(false) }

    // If permission is already granted when this screen appears, skip it silently.
    LaunchedEffect(Unit) {
        if (Settings.canDrawOverlays(context)) viewModel.advance()
    }

    ScreenLayout(
        illustrationDescription = "Phone showing a code card floating on top of another app",
        illustration            = { OverlayIllustration() },
        headline                = "One more permission",
        body                    = "BigOTP can show your code on top of other apps, so you can see it " +
                                  "while typing into your bank or any website.\n\n" +
                                  "We cannot see what you are typing. We cannot see which apps you use. " +
                                  "We only show your code — nothing else.\n\n" +
                                  "Tap below to open Settings and allow this."
    ) {
        Spacer(Modifier.height(8.dp))
        FullWidthButton(
            text    = "Open Settings",
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )

        if (showReturnPrompt) {
            Spacer(Modifier.height(4.dp))
            Text(
                text      = "Without this, you'll need to switch back to BigOTP to see your code while typing. We strongly recommend enabling it.",
                style     = MaterialTheme.typography.bodyLarge,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(4.dp))

        if (showSkipExplanation) {
            Text(
                text      = "You can enable this later in Settings → Apps → Special app access → Appear on top → BigOTP",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            FullWidthButton(
                text    = "Got it, continue",
                onClick = { viewModel.skipOverlayPermission() }
            )
        } else {
            TextButton(
                onClick  = { showSkipExplanation = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text("Continue without this", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun OverlayIllustration() {
    val primary  = MaterialTheme.colorScheme.primary
    val surface  = MaterialTheme.colorScheme.surfaceVariant
    val outline  = MaterialTheme.colorScheme.outline

    Canvas(modifier = Modifier.fillMaxSize()) {
        val phoneW = size.width * 0.40f
        val phoneH = size.height * 0.80f
        val phoneL = (size.width - phoneW) / 2f
        val phoneT = (size.height - phoneH) / 2f
        val corner = phoneW * 0.12f

        // Phone body
        drawRoundRect(color = surface, topLeft = Offset(phoneL, phoneT),
                      size = Size(phoneW, phoneH), cornerRadius = CornerRadius(corner))
        drawRoundRect(color = outline, topLeft = Offset(phoneL, phoneT),
                      size = Size(phoneW, phoneH), cornerRadius = CornerRadius(corner),
                      style = Stroke(width = 2.dp.toPx()))

        // Background app content (grey bars)
        repeat(3) { i ->
            val barW = phoneW * 0.70f
            val barH = phoneH * 0.07f
            val barX = phoneL + (phoneW - barW) / 2f
            val barY = phoneT + phoneH * 0.22f + i * (barH + phoneH * 0.08f)
            drawRoundRect(color = outline.copy(alpha = 0.25f),
                          topLeft = Offset(barX, barY), size = Size(barW, barH),
                          cornerRadius = CornerRadius(4.dp.toPx()))
        }

        // Floating bubble card (bottom-right of phone)
        val cardW  = phoneW * 0.64f
        val cardH  = phoneH * 0.24f
        val cardX  = phoneL + phoneW - cardW - phoneW * 0.04f
        val cardY  = phoneT + phoneH - cardH - phoneH * 0.08f
        drawRoundRect(color = primary, topLeft = Offset(cardX, cardY),
                      size = Size(cardW, cardH), cornerRadius = CornerRadius(8.dp.toPx()))

        // Code digit blocks on the card
        val digitW   = cardW * 0.14f
        val digitH   = cardH * 0.42f
        val digitY   = cardY + cardH * 0.35f
        val totalDW  = 4 * digitW + 3 * (digitW * 0.3f)
        val digitX   = cardX + (cardW - totalDW) / 2f
        repeat(4) { i ->
            drawRoundRect(
                color        = Color.White.copy(alpha = 0.90f),
                topLeft      = Offset(digitX + i * (digitW + digitW * 0.3f), digitY),
                size         = Size(digitW, digitH),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}

// ── Screen 4 — Sound check ────────────────────────────────────────────────────

@Composable
private fun Screen4_SoundCheck(viewModel: OnboardingViewModel) {
    ScreenLayout(
        illustrationDescription = "Sound wave illustration",
        illustration            = { SoundWaveIllustration() },
        headline                = "Shall we read codes aloud?",
        body                    = "BigOTP can announce your code as soon as it arrives"
    ) {
        Spacer(Modifier.height(8.dp))
        FullWidthButton(
            text = "Yes, read them out",
            onClick = {
                viewModel.setTtsEnabled(true)
                viewModel.advance()
            }
        )
        Spacer(Modifier.height(8.dp))
        FullWidthOutlinedButton(
            text = "No thanks",
            onClick = {
                viewModel.setTtsEnabled(false)
                viewModel.advance()
            }
        )
    }
}

@Composable
private fun SoundWaveIllustration() {
    val primary = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx    = size.width / 2f
        val cy    = size.height / 2f
        val bars  = listOf(0.25f, 0.55f, 1.0f, 0.55f, 0.25f)
        val barW  = size.width * 0.06f
        val maxH  = size.height * 0.7f
        val gap   = barW * 0.6f
        val total = bars.size * barW + (bars.size - 1) * gap
        val startX = cx - total / 2f

        bars.forEachIndexed { i, fraction ->
            val barH = maxH * fraction
            val x    = startX + i * (barW + gap)
            drawRoundRect(
                color        = primary,
                topLeft      = Offset(x, cy - barH / 2f),
                size         = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}

// ── Screen 5 — Complete ───────────────────────────────────────────────────────

@Composable
private fun Screen5_Complete(viewModel: OnboardingViewModel, onComplete: () -> Unit) {
    ScreenLayout(
        illustrationDescription = "Large green checkmark indicating completion",
        illustration            = { CheckmarkIllustration() },
        headline                = "You're all set",
        body                    = "Next time a code arrives, we'll show it to you straight away"
    ) {
        Spacer(Modifier.height(8.dp))
        FullWidthButton(
            text = "Got it",
            onClick = {
                viewModel.completeOnboarding()
                onComplete()
            }
        )
    }
}

@Composable
private fun CheckmarkIllustration() {
    val primary = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = minOf(size.width, size.height) * 0.36f
        val cx     = size.width / 2f
        val cy     = size.height / 2f

        drawCircle(color = primary.copy(alpha = 0.12f), radius = radius, center = Offset(cx, cy))
        drawCircle(color = primary, radius = radius, center = Offset(cx, cy),
                   style = Stroke(width = 3.dp.toPx()))

        val path = Path().apply {
            val startX = cx - radius * 0.38f
            val startY = cy + radius * 0.02f
            val midX   = cx - radius * 0.05f
            val midY   = cy + radius * 0.32f
            val endX   = cx + radius * 0.42f
            val endY   = cy - radius * 0.28f
            moveTo(startX, startY)
            lineTo(midX, midY)
            lineTo(endX, endY)
        }
        drawPath(path = path, color = primary,
                 style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ── Shared button helpers ─────────────────────────────────────────────────────

@Composable
private fun FullWidthButton(text: String, onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun FullWidthOutlinedButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

// ── Reduce motion helper ──────────────────────────────────────────────────────

@Composable
private fun reduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    val scale   = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    return scale == 0f
}
