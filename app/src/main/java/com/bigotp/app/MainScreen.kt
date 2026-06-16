package com.bigotp.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bigotp.app.history.LiveEntry
import com.bigotp.app.parser.OtpResult
import com.bigotp.app.parser.OtpType
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val history                by viewModel.liveHistory.collectAsState()
    val serviceAliveState      by viewModel.serviceAliveState.collectAsState()
    val currentlyReadingId     by viewModel.currentlyReadingId.collectAsState()
    val bubbleGranted          by viewModel.bubblePermissionGranted.collectAsState()
    val bubblePromptDismissed  by viewModel.isBubblePromptDismissed.collectAsState()
    val reduced                = reduceMotionEnabled()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar   = {
            LargeTopAppBar(
                title = {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter            = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier           = Modifier.size(36.dp),
                            tint               = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text       = "BigOTP",
                            style      = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick  = onOpenSettings,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Settings", style = MaterialTheme.typography.labelLarge)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusRow(
                serviceAliveState = serviceAliveState,
                onAttemptRebind   = { viewModel.attemptRebind() }
            )

            // Soft bubble-permission prompt — shown once, dismissable
            if (!bubbleGranted && !bubblePromptDismissed) {
                BubblePromptRow(
                    onDismiss = { viewModel.dismissBubblePrompt() }
                )
            }

            if (history.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = "Recent codes",
                    style    = MaterialTheme.typography.titleMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                history.forEachIndexed { index, entry ->
                    if (index == 1) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                text  = "Earlier",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                    }
                    HistoryItem(
                        entry         = entry,
                        reduced       = reduced,
                        isNewer       = index == 0,
                        isReadingThis = currentlyReadingId == entry.receivedAt,
                        onReadAloud   = { viewModel.speakEntry(entry) },
                        onDelete      = { viewModel.removeHistoryEntry(entry.receivedAt) }
                    )
                }
            }
        }
    }
}

// ── Soft overlay-permission prompt ────────────────────────────────────────────

@Composable
private fun BubblePromptRow(onDismiss: () -> Unit) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = "See your code while typing. Tap to enable floating code." },
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Rounded.Layers,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { }
            ) {
                Text(
                    text  = "See your code while typing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text  = "Tap to enable floating code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector        = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint               = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

// ── Status row ────────────────────────────────────────────────────────────────

@Composable
private fun StatusRow(
    serviceAliveState: ServiceAliveState,
    onAttemptRebind: () -> Unit
) {
    val context = LocalContext.current

    when (serviceAliveState) {
        ServiceAliveState.ACTIVE -> Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { contentDescription = "Watching for your codes" },
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier              = Modifier.padding(12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text  = "Watching for your codes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        ServiceAliveState.STALE, ServiceAliveState.DISCONNECTED -> Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { contentDescription = "BigOTP may have stopped" },
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier              = Modifier.padding(12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text  = "BigOTP may have stopped",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                TextButton(onClick = onAttemptRebind) {
                    Text(
                        text  = "Fix",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        ServiceAliveState.NO_PERMISSION -> Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { contentDescription = "Notification access needed" },
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier              = Modifier.padding(12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text  = "Notification access needed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    }
                ) {
                    Text(
                        text  = "Fix",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ── History item ──────────────────────────────────────────────────────────────

@Composable
private fun HistoryItem(
    entry: LiveEntry,
    reduced: Boolean,
    isNewer: Boolean,
    isReadingThis: Boolean,
    onReadAloud: () -> Unit,
    onDelete: () -> Unit
) {
    val context    = LocalContext.current
    val haptic     = LocalHapticFeedback.current
    val now        = System.currentTimeMillis()
    val timeAgo    = formatPreciseTimeAgo(entry.receivedAt, now)
    val timeLeft   = formatTimeLeft(entry.expiresAtMs, now)
    val isNew      = (now - entry.receivedAt) < 60_000L

    var isRevealed          by remember(entry.result.code) { mutableStateOf(false) }
    var showOriginalMessage by remember(entry.result.code) { mutableStateOf(false) }
    var showDeleteConfirm   by remember(entry.result.code) { mutableStateOf(false) }

    LaunchedEffect(isRevealed) {
        if (isRevealed) {
            delay(5_000)
            isRevealed = false
        }
    }

    val accentColor = if (entry.result.type == OtpType.PAYMENT)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.primary

    val itemDesc = "${entry.result.sourceName}. Received $timeAgo. $timeLeft. ${
        if (isRevealed) "Code: ${entry.result.code.map { it.toString() }.joinToString(", ")}"
        else "Code hidden."
    }"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .alpha(if (isNewer) 1f else 0.72f)
            .semantics(mergeDescendants = true) { contentDescription = itemDesc },
        shape           = RoundedCornerShape(16.dp),
        tonalElevation  = if (isNewer) 3.dp else 1.dp,
        shadowElevation = if (isNewer) 2.dp else 0.dp,
        color           = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left accent bar — payment = error, login = primary
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        color = accentColor,
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OtpSourceIcon(result = entry.result)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text  = entry.result.sourceName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (isNew) {
                                Surface(
                                    color = accentColor,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text     = "NEW",
                                        style    = MaterialTheme.typography.labelSmall,
                                        color    = if (entry.result.type == OtpType.PAYMENT)
                                                       MaterialTheme.colorScheme.onError
                                                   else MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text  = "$timeAgo · $timeLeft",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(
                        targetState    = isRevealed,
                        transitionSpec = {
                            if (reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            else         fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                        },
                        label    = "code_reveal",
                        modifier = Modifier.weight(1f)
                    ) { revealed ->
                        if (revealed) {
                            Text(
                                text       = entry.result.code,
                                style      = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color      = accentColor
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                repeat(entry.result.code.length) {
                                    Icon(
                                        imageVector        = Icons.Rounded.Circle,
                                        contentDescription = null,
                                        modifier           = Modifier.size(10.dp),
                                        tint               = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onReadAloud) {
                            Icon(
                                imageVector        = if (isReadingThis) Icons.Rounded.StopCircle
                                                     else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = if (isReadingThis) "Stop reading"
                                                     else "Read code aloud",
                                tint               = if (isReadingThis) MaterialTheme.colorScheme.error
                                                     else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("OTP", entry.result.code))
                        }) {
                            Icon(
                                imageVector        = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy code",
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick  = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isRevealed = !isRevealed
                            },
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(if (isRevealed) "Hide" else "Reveal")
                        }
                    }
                }

                if (showOriginalMessage) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = entry.result.rawMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showOriginalMessage = !showOriginalMessage }) {
                        Text(
                            text  = if (showOriginalMessage) "Hide message" else "Show message",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (showDeleteConfirm) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text("Cancel", style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = onDelete) {
                                Text(
                                    "Remove",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector        = Icons.Rounded.Delete,
                                contentDescription = "Remove from history",
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier           = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Source icon ───────────────────────────────────────────────────────────────

@Composable
fun OtpSourceIcon(result: OtpResult) {
    val icon = when {
        result.type == OtpType.PAYMENT &&
            (result.sourceName.contains("PhonePe", ignoreCase = true) ||
             result.sourceName.contains("GPay", ignoreCase = true) ||
             result.sourceName.contains("Google Pay", ignoreCase = true) ||
             result.sourceName.contains("Paytm", ignoreCase = true) ||
             result.sourceName.contains("Amazon", ignoreCase = true)) ->
            Icons.Rounded.Payments
        result.sourceName.contains("Bank", ignoreCase = true) ||
            result.sourceName.contains("SBI", ignoreCase = true) ||
            result.sourceName.contains("HDFC", ignoreCase = true) ||
            result.sourceName.contains("ICICI", ignoreCase = true) ||
            result.type == OtpType.PAYMENT ->
            Icons.Rounded.AccountBalance
        result.sourceName.contains("IRCTC", ignoreCase = true) ->
            Icons.Rounded.Train
        result.sourceName.contains("Aadhaar", ignoreCase = true) ->
            Icons.Rounded.Badge
        else -> Icons.AutoMirrored.Rounded.Message
    }
    Icon(
        imageVector        = icon,
        contentDescription = result.sourceName,
        modifier           = Modifier.size(40.dp),
        tint               = MaterialTheme.colorScheme.primary
    )
}

// ── Time helpers ──────────────────────────────────────────────────────────────

fun formatPreciseTimeAgo(receivedAt: Long, now: Long): String {
    val seconds = (now - receivedAt) / 1_000
    return when {
        seconds < 30  -> "just now"
        seconds < 60  -> "${seconds}s ago"
        seconds < 120 -> "1 min ago"
        else          -> "${seconds / 60} min ago"
    }
}

fun formatTimeLeft(expiresAtMs: Long, now: Long): String {
    val seconds = ((expiresAtMs - now) / 1_000).coerceAtLeast(0)
    val minutes = ((seconds + 59) / 60).toInt()
    return if (minutes <= 1) "less than 1 min left" else "$minutes min left"
}

// ── Reduce motion ─────────────────────────────────────────────────────────────

@Composable
fun reduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    val scale   = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    return scale == 0f
}
