package com.bigotp.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private const val PRIVACY_URL = "https://bigotp.in/privacy_policy"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenParserTest: () -> Unit
) {
    val context       = LocalContext.current
    val ttsEnabled    by viewModel.isTtsEnabled.collectAsState()
    val loginExpiry   by viewModel.loginExpiryMinutes.collectAsState()
    val compactMode   by viewModel.isCompactMode.collectAsState()

    // 5-tap easter egg state — resets if 2 seconds elapse between taps.
    var tapCount  by remember { mutableIntStateOf(0) }
    var lastTapMs by remember { mutableLongStateOf(0L) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = innerPadding
        ) {

            // ── Accessibility section ──────────────────────────────────────────

            item {
                SectionHeader("Accessibility")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    ListItem(
                        headlineContent   = { Text("Read codes aloud") },
                        supportingContent = { Text("Speaks each digit individually") },
                        leadingContent    = {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null)
                        },
                        trailingContent   = {
                            Switch(
                                checked         = ttsEnabled,
                                onCheckedChange = { viewModel.setTtsEnabled(it) }
                            )
                        },
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "Read codes aloud. ${if (ttsEnabled) "On" else "Off"}."
                            role               = Role.Switch
                        }
                    )
                }
            }

            // ── Display section ────────────────────────────────────────────────

            item {
                SectionHeader("Display")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    ListItem(
                        headlineContent   = { Text("Compact mode") },
                        supportingContent = {
                            Text(
                                if (compactMode)
                                    "Bubble only — app stays out of your way"
                                else
                                    "Full-screen takeover on arrival (accessibility mode)"
                            )
                        },
                        leadingContent    = {
                            Icon(Icons.Rounded.Layers, contentDescription = null)
                        },
                        trailingContent   = {
                            Switch(
                                checked         = compactMode,
                                onCheckedChange = { viewModel.setCompactMode(it) }
                            )
                        },
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "Compact mode. ${if (compactMode) "On. Bubble only." else "Off. Full screen on arrival."}."
                            role               = Role.Switch
                        }
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    ListItem(
                        headlineContent   = { Text("Full-screen display time") },
                        supportingContent = { Text("Only applies when compact mode is off") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ExpiryOption(
                        label    = "1 minute",
                        selected = loginExpiry == 1,
                        onSelect = { viewModel.setLoginExpiryMinutes(1) }
                    )
                    ExpiryOption(
                        label    = "2 minutes",
                        selected = loginExpiry == 2,
                        onSelect = { viewModel.setLoginExpiryMinutes(2) }
                    )
                    ExpiryOption(
                        label    = "3 minutes",
                        selected = loginExpiry == 3,
                        onSelect = { viewModel.setLoginExpiryMinutes(3) }
                    )
                }
            }

            // ── About section ──────────────────────────────────────────────────

            item {
                SectionHeader("About")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    ListItem(
                        headlineContent = { Text("Version") },
                        leadingContent  = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        trailingContent = {
                            Text(
                                text  = versionName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .clickable {
                                if (BuildConfig.DEBUG) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapMs > 2_000) tapCount = 0
                                    tapCount++
                                    lastTapMs = now
                                    if (tapCount >= 5) {
                                        tapCount = 0
                                        onOpenParserTest()
                                    }
                                }
                            }
                            .semantics { contentDescription = "Version $versionName" }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent   = { Text("Zero data collection") },
                        supportingContent = {
                            Text(
                                text  = "No analytics, no crash reporting, no user identifiers",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = {
                            Text(
                                text  = "Privacy policy",
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier
                            .clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))
                                )
                            }
                            .semantics { contentDescription = "Privacy policy, opens in browser" }
                    )
                }
            }

            item { SectionHeader("") }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelLarge,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ExpiryOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text  = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        leadingContent = {
            RadioButton(selected = selected, onClick = onSelect)
        },
        modifier = Modifier
            .height(56.dp)
            .clickable(onClick = onSelect)
            .semantics(mergeDescendants = true) {
                role               = Role.RadioButton
                contentDescription = "$label. ${if (selected) "Selected." else "Not selected."}"
            }
    )
}
