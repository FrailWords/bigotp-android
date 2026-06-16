package com.bigotp.app.display

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bigotp.app.OtpSourceIcon
import com.bigotp.app.parser.OtpType
import com.bigotp.app.parser.UrgencyLevel

@Composable
fun OtpDisplayScreen(
    viewModel: OtpDisplayViewModel,
    onDone: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    OtpDisplayContent(
        state       = uiState,
        onCopy      = { viewModel.copyToClipboard() },
        onTtsToggle = { if (uiState.isSpeaking) viewModel.stopTTS() else viewModel.reSpeak() },
        onDone      = {
            viewModel.markUsed()
            onDone()
        }
    )
}

@Composable
private fun OtpDisplayContent(
    state: OtpDisplayUiState,
    onCopy: () -> Unit,
    onTtsToggle: () -> Unit,
    onDone: () -> Unit
) {
    val result  = state.result
    val urgency = result.urgencyLevel
    val expired = urgency == UrgencyLevel.EXPIRED
    val reduced = reduceMotionEnabled()
    val haptic  = LocalHapticFeedback.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top row: icon + source name + TTS toggle ────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OtpSourceIcon(result = result)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = result.sourceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text  = "One-time code",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!expired) {
                    IconButton(onClick = onTtsToggle) {
                        Icon(
                            imageVector        = if (state.isSpeaking) Icons.Rounded.StopCircle
                                                 else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = if (state.isSpeaking) "Stop reading"
                                                 else "Read code aloud",
                            tint               = if (state.isSpeaking) MaterialTheme.colorScheme.error
                                                 else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ── Hero code card ───────────────────────────────────────────────
            val isPayment = result.type == OtpType.PAYMENT
            ElevatedCard(
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                shape     = RoundedCornerShape(24.dp),
                colors    = CardDefaults.elevatedCardColors(
                    containerColor = if (isPayment)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                // Top accent stripe
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(urgencyColor(urgency))
                )

                Column(
                    modifier            = Modifier.padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (isPayment) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onErrorContainer,
                                modifier           = Modifier.size(20.dp)
                            )
                            if (result.amountString != null) {
                                Text(
                                    text       = result.amountString,
                                    style      = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                text  = "· ${result.sourceName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                            )
                        }
                    }

                    val digitDesc = if (expired) "Code expired"
                        else "Your code is, " + result.code.map { it.toString() }.joinToString(", ")

                    if (expired) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Code expired" },
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                        ) {
                            repeat(4) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(vertical = 14.dp)
                                ) {
                                    Text(
                                        text     = "•",
                                        style    = MaterialTheme.typography.displaySmall,
                                        color    = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.clearAndSetSemantics { }
                                    )
                                }
                            }
                        }
                    } else {
                        val digitStyle = if (result.code.length > 6)
                            MaterialTheme.typography.headlineLarge
                        else
                            MaterialTheme.typography.displayLarge

                        val digitBoxColor = if (isPayment)
                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.primaryContainer

                        val digitTextColor = if (isPayment)
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer

                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = digitDesc },
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            result.code.forEach { digit ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(digitBoxColor)
                                        .padding(vertical = 12.dp)
                                        .clearAndSetSemantics { }
                                ) {
                                    Text(
                                        text       = digit.toString(),
                                        style      = digitStyle,
                                        fontWeight = FontWeight.Bold,
                                        color      = digitTextColor
                                    )
                                }
                            }
                        }
                    }

                    CountdownRow(
                        urgency          = urgency,
                        secondsRemaining = state.secondsRemaining,
                        totalSeconds     = state.totalSeconds,
                        reduced          = reduced,
                        isPayment        = isPayment
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Copy button ──────────────────────────────────────────────────
            Button(
                onClick  = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCopy()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 24.dp),
                shape    = RoundedCornerShape(24.dp),
                enabled  = !expired && !state.copied
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    text  = if (state.copied) "✓ Copied — clears in ${state.copiedSecondsLeft}s"
                            else "Copy code",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Done button — long press to dismiss ──────────────────────────
            // FilledTonalButton's internal clickable consumes pointer events before
            // an external pointerInput can see them. Surface + combinedClickable owns
            // gesture detection at the right level and correctly fires onLongClick.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 24.dp)
                    .combinedClickable(
                        onClick     = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDone()
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDone()
                        }
                    ),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier              = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Hold to dismiss",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // ── Never-share warning (payment OTPs only) ──────────────────────
            if (result.type == OtpType.PAYMENT) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Rounded.Warning,
                            contentDescription = null,
                            modifier           = Modifier.size(18.dp),
                            tint               = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text  = "Never share this code with anyone — not even bank staff",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CountdownRow(
    urgency: UrgencyLevel,
    secondsRemaining: Int,
    totalSeconds: Int,
    reduced: Boolean,
    isPayment: Boolean = false
) {
    val fraction = if (totalSeconds > 0) {
        (secondsRemaining.toFloat() / totalSeconds).coerceIn(0f, 1f)
    } else 0f

    val urgencyColor = urgencyColor(urgency)

    val animatedColor by animateColorAsState(
        targetValue   = urgencyColor,
        animationSpec = if (reduced) tween(0) else tween(600),
        label         = "urgencyColor"
    )
    val animatedFraction by animateFloatAsState(
        targetValue   = fraction,
        animationSpec = if (reduced) tween(0) else tween(800),
        label         = "countdown"
    )

    val icon: ImageVector = when (urgency) {
        UrgencyLevel.HEALTHY, UrgencyLevel.WARNING -> Icons.Rounded.AccessTime
        UrgencyLevel.CRITICAL                      -> Icons.Rounded.Warning
        UrgencyLevel.EXPIRED                       -> Icons.Rounded.Close
    }

    val timeLabel = when (urgency) {
        UrgencyLevel.EXPIRED  -> "Expired"
        UrgencyLevel.CRITICAL -> "Expires soon"
        else                  -> {
            val mins = secondsRemaining / 60
            val secs = secondsRemaining % 60
            if (mins > 0) "$mins min left" else "$secs sec left"
        }
    }

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = timeLabel },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = animatedColor,
                modifier           = Modifier.size(16.dp).clearAndSetSemantics { }
            )
            Text(
                text     = timeLabel,
                style    = MaterialTheme.typography.bodyMedium,
                color    = animatedColor,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
        LinearProgressIndicator(
            progress   = { animatedFraction },
            modifier   = Modifier.fillMaxWidth(),
            color      = animatedColor,
            trackColor = if (isPayment)
                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun urgencyColor(urgency: UrgencyLevel): Color = when (urgency) {
    UrgencyLevel.HEALTHY  -> MaterialTheme.colorScheme.primary
    UrgencyLevel.WARNING  -> MaterialTheme.colorScheme.tertiary
    UrgencyLevel.CRITICAL -> MaterialTheme.colorScheme.error
    UrgencyLevel.EXPIRED  -> MaterialTheme.colorScheme.outline
}

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
