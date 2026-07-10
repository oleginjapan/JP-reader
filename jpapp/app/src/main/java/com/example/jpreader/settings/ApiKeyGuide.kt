package com.example.jpreader.settings

data class ApiKeyGuide(
    val provider: Provider,
    val steps: List<String>,
    val consoleUrl: String,
    val notes: String
)

val apiKeyGuides: Map<Provider, ApiKeyGuide> = mapOf(
    Provider.OPENAI to ApiKeyGuide(
        provider = Provider.OPENAI,
        steps = listOf(
            "Go to platform.openai.com and sign in (or create an account).",
            "Open Settings → API keys.",
            "Tap \"Create new secret key\", name it e.g. \"JP Reading App\".",
            "Copy the key immediately — OpenAI only shows it once.",
            "Paste it into the ChatGPT field below and tap Test connection.",
            "Note: this requires billing set up on your OpenAI account; it's a pay-as-you-go API, separate from a ChatGPT Plus subscription."
        ),
        consoleUrl = "https://platform.openai.com/api-keys",
        notes = "Typical cost for a short story: a fraction of a cent per generation on gpt-4o-mini."
    ),
    Provider.ANTHROPIC to ApiKeyGuide(
        provider = Provider.ANTHROPIC,
        steps = listOf(
            "Go to console.anthropic.com and sign in (or create an account).",
            "Open Settings → API Keys.",
            "Tap \"Create Key\", name it e.g. \"JP Reading App\".",
            "Copy the key immediately.",
            "Paste it into the Claude field below and tap Test connection.",
            "Note: this is a separate account/billing from a Claude.ai subscription."
        ),
        consoleUrl = "https://console.anthropic.com/settings/keys",
        notes = "New accounts often start with a small free credit; after that it's pay-as-you-go."
    ),
    Provider.XAI to ApiKeyGuide(
        provider = Provider.XAI,
        steps = listOf(
            "Go to console.x.ai and sign in (or create an account).",
            "Open the API Keys section.",
            "Tap \"Create API Key\", name it e.g. \"JP Reading App\".",
            "Copy the key immediately.",
            "Paste it into the Grok field below and tap Test connection."
        ),
        consoleUrl = "https://console.x.ai",
        notes = "Separate account/billing from an X Premium subscription."
    )
)
