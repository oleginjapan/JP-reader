# Dependencies needed for the Settings / API-key screens

Add to app/build.gradle.kts:

    implementation("androidx.security:security-crypto:1.1.0")     // EncryptedSharedPreferences
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.0") // Visibility icons
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
    implementation("androidx.navigation:navigation-compose:2.8.0")

And in the plugins block:

    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"

## Wiring it up

    val keyStore = ApiKeyStore(context)
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(keyStore) }
    ApiKeySettingsScreen(viewModel)

Add to app/src/main/AndroidManifest.xml (needed for AnkiDroid + network calls):

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="com.ichi2.anki.permission.READ_WRITE_DATABASE" />
