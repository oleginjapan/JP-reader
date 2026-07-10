package com.example.jpreader.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Settings screen for entering AI provider API keys.
 * Each provider gets: a password-style key field, a Test connection button,
 * and a status indicator. A radio selection sets which provider is used by
 * default when generating a Reading story.
 */
@Composable
fun ApiKeySettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    var defaultProvider by remember { mutableStateOf(viewModel.getDefaultProvider()) }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("AI Story Generation", style = MaterialTheme.typography.titleLarge)
        Text(
            "Paste an API key from each service you want to use. Keys are stored encrypted " +
                "on this device and are only sent directly to that provider's own API.",
            style = MaterialTheme.typography.bodySmall
        )

        Provider.entries.forEach { provider ->
            ProviderKeyCard(
                provider = provider,
                value = viewModel.keyFieldValue[provider].orEmpty(),
                onValueChange = { viewModel.onKeyChanged(provider, it) },
                state = viewModel.connectionState[provider] ?: ConnectionState.UNTESTED,
                error = viewModel.connectionError[provider],
                isDefault = defaultProvider == provider,
                onSetDefault = {
                    defaultProvider = provider
                    viewModel.setDefaultProvider(provider)
                },
                onSave = { viewModel.saveKey(provider) },
                onTest = { viewModel.testConnection(provider) }
            )
        }
    }
}

@Composable
private fun ProviderKeyCard(
    provider: Provider,
    value: String,
    onValueChange: (String) -> Unit,
    state: ConnectionState,
    error: String?,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit
) {
    var showKey by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }

    ElevatedCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                RadioButton(selected = isDefault, onClick = onSetDefault)
                Text("Use by default", style = MaterialTheme.typography.labelSmall)
            }

            TextButton(onClick = { showGuide = true }, contentPadding = PaddingValues(0.dp)) {
                Text("How do I get a key?")
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("API key") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState -> if (!focusState.isFocused) onSave() }
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onTest, enabled = state != ConnectionState.TESTING) {
                    Text(if (state == ConnectionState.TESTING) "Testing…" else "Test connection")
                }
                when (state) {
                    ConnectionState.SUCCESS -> Text("✅ Connected", color = MaterialTheme.colorScheme.primary)
                    ConnectionState.FAILED -> Text("❌ ${error ?: "Failed"}", color = MaterialTheme.colorScheme.error)
                    ConnectionState.TESTING -> CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    ConnectionState.UNTESTED -> {}
                }
            }
        }
    }

    if (showGuide) {
        ApiKeyGuideDialog(guide = apiKeyGuides.getValue(provider), onDismiss = { showGuide = false })
    }
}

@Composable
private fun ApiKeyGuideDialog(guide: ApiKeyGuide, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Get a ${guide.provider.displayName} API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                guide.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
                Text(guide.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guide.consoleUrl)))
                onDismiss()
            }) { Text("Open ${guide.provider.displayName.substringBefore(" (")} site") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
