package com.bigotp.app

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigotp.app.config.ConfigRepository
import com.bigotp.app.display.OtpDisplayActivity
import com.bigotp.app.display.OtpDisplayViewModel
import com.bigotp.app.parser.OtpParser
import com.bigotp.app.parser.OtpResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserTestScreen(onBack: () -> Unit) {
    // Guard: this composable must only be called when BuildConfig.DEBUG is true.
    // The call site in MainActivity already checks BuildConfig.DEBUG before showing this.

    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()

    var input     by remember { mutableStateOf("") }
    var result    by remember { mutableStateOf<OtpResult?>(null) }
    var noMatch   by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parser Test", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(
                            text     = "←",
                            style    = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { contentDescription = "Back" }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text  = "Paste a raw SMS message below:",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value         = input,
                onValueChange = { input = it },
                modifier      = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                placeholder   = { Text("Paste SMS text here…") },
                textStyle     = MaterialTheme.typography.bodyLarge
            )

            Button(
                onClick  = {
                    scope.launch {
                        noMatch = false
                        result  = null
                        val patterns = ConfigRepository(context).getPatterns()
                        val parsed = OtpParser.parse(
                            notificationText = input,
                            patterns         = patterns
                        )
                        if (parsed != null) result = parsed else noMatch = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                enabled  = input.isNotBlank()
            ) {
                Text("Parse", style = MaterialTheme.typography.titleMedium)
            }

            if (noMatch) {
                Text(
                    text  = "No OTP found in this message.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            result?.let { r ->
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Result:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ResultRow("code",       r.code)
                ResultRow("type",       r.type.name)
                ResultRow("confidence", "%.2f".format(r.confidence))
                ResultRow("amount",     r.amountString ?: "—")
                ResultRow("source",     r.sourceName)

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val intent = Intent(context, OtpDisplayActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra(OtpDisplayViewModel.EXTRA_OTP_RESULT, r)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Text(
                        text  = "Show display screen with this result",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Text(
        text  = "$label: $value",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
