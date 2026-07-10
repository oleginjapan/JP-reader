package com.example.jpreader.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class Provider(val key: String, val displayName: String) {
    OPENAI("openai", "ChatGPT (OpenAI)"),
    ANTHROPIC("anthropic", "Claude (Anthropic)"),
    XAI("xai", "Grok (xAI)")
}

/**
 * Stores API keys encrypted at rest, backed by the Android Keystore.
 * Keys never leave the device except in the Authorization header of the
 * direct HTTPS call to that provider's own API.
 */
class ApiKeyStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ai_provider_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getKey(provider: Provider): String? = prefs.getString(provider.key, null)

    fun setKey(provider: Provider, apiKey: String) {
        prefs.edit().putString(provider.key, apiKey.trim()).apply()
    }

    fun clearKey(provider: Provider) {
        prefs.edit().remove(provider.key).apply()
    }

    fun getDefaultProvider(): Provider =
        prefs.getString("default_provider", Provider.OPENAI.key)
            ?.let { saved -> Provider.entries.find { it.key == saved } } ?: Provider.OPENAI

    fun setDefaultProvider(provider: Provider) {
        prefs.edit().putString("default_provider", provider.key).apply()
    }

    fun hasAnyKeyConfigured(): Boolean = Provider.entries.any { getKey(it) != null }
}
