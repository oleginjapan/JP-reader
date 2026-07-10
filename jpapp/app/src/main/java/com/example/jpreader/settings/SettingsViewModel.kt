package com.example.jpreader.settings

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jpreader.ai.AnthropicProvider
import com.example.jpreader.ai.AiProvider
import com.example.jpreader.ai.OpenAiProvider
import com.example.jpreader.ai.XaiProvider
import kotlinx.coroutines.launch

enum class ConnectionState { UNTESTED, TESTING, SUCCESS, FAILED }

class SettingsViewModel(private val keyStore: ApiKeyStore) : ViewModel() {

    private val providerImpls: Map<Provider, AiProvider> = mapOf(
        Provider.OPENAI to OpenAiProvider(),
        Provider.ANTHROPIC to AnthropicProvider(),
        Provider.XAI to XaiProvider()
    )

    // UI-observable state per provider
    val keyFieldValue = mutableStateMapOf<Provider, String>().apply {
        Provider.entries.forEach { put(it, keyStore.getKey(it) ?: "") }
    }
    val connectionState = mutableStateMapOf<Provider, ConnectionState>().apply {
        Provider.entries.forEach { put(it, ConnectionState.UNTESTED) }
    }
    val connectionError = mutableStateMapOf<Provider, String?>()

    fun onKeyChanged(provider: Provider, newValue: String) {
        keyFieldValue[provider] = newValue
        connectionState[provider] = ConnectionState.UNTESTED
    }

    fun saveKey(provider: Provider) {
        val value = keyFieldValue[provider].orEmpty()
        if (value.isBlank()) keyStore.clearKey(provider) else keyStore.setKey(provider, value)
    }

    /** Saves the key (if changed) and fires a minimal request to confirm it works. */
    fun testConnection(provider: Provider) {
        saveKey(provider)
        val key = keyFieldValue[provider].orEmpty()
        if (key.isBlank()) {
            connectionState[provider] = ConnectionState.FAILED
            connectionError[provider] = "Enter an API key first"
            return
        }
        connectionState[provider] = ConnectionState.TESTING
        viewModelScope.launch {
            val impl = providerImpls.getValue(provider)
            val result = impl.testConnection(key)
            result.onSuccess {
                connectionState[provider] = ConnectionState.SUCCESS
                connectionError[provider] = null
            }.onFailure { e ->
                connectionState[provider] = ConnectionState.FAILED
                connectionError[provider] = e.message ?: "Unknown error"
            }
        }
    }

    fun setDefaultProvider(provider: Provider) = keyStore.setDefaultProvider(provider)
    fun getDefaultProvider(): Provider = keyStore.getDefaultProvider()
}
