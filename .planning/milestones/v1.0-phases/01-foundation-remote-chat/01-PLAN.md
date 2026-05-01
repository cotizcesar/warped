---
plan: 01-foundation
wave: 1
depends_on: []
autonomous: true
requirements_addressed: [PROV-01]
files_modified:
  - gradle/wrapper/gradle-wrapper.properties
  - gradlew
  - gradlew.bat
  - gradle/libs.versions.toml
  - build.gradle.kts
  - settings.gradle.kts
  - gradle.properties
  - app/build.gradle.kts
  - app/proguard-rules.pro
  - app/src/main/AndroidManifest.xml
  - app/src/main/res/xml/network_security_config.xml
  - app/src/main/res/xml/data_extraction_rules.xml
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values/themes.xml
  - app/src/main/java/com/warped/WarpedApplication.kt
  - app/src/main/java/com/warped/MainActivity.kt
  - app/src/main/java/com/warped/ui/theme/Color.kt
  - app/src/main/java/com/warped/ui/theme/Type.kt
  - app/src/main/java/com/warped/ui/theme/Shape.kt
  - app/src/main/java/com/warped/ui/theme/Theme.kt
  - app/src/main/java/com/warped/domain/model/Role.kt
  - app/src/main/java/com/warped/domain/model/ProviderType.kt
  - app/src/main/java/com/warped/domain/model/ChatMessage.kt
  - app/src/main/java/com/warped/domain/model/Conversation.kt
  - app/src/main/java/com/warped/domain/model/ChatRequest.kt
  - app/src/main/java/com/warped/domain/model/GenerationParameters.kt
  - app/src/main/java/com/warped/domain/model/ModelInfo.kt
  - app/src/main/java/com/warped/domain/model/ConnectionStatus.kt
  - app/src/main/java/com/warped/domain/model/StreamToken.kt
  - app/src/main/java/com/warped/domain/model/Endpoint.kt
  - app/src/main/java/com/warped/domain/provider/LlmProvider.kt
  - app/src/main/java/com/warped/domain/repository/ChatRepository.kt
  - app/src/main/java/com/warped/domain/repository/EndpointRepository.kt
  - app/src/main/java/com/warped/domain/repository/ModelRepository.kt
---

# Plan 01: Gradle Foundation, App Shell, Theme & Domain Contracts

## Objective
Set up the full Gradle build system (wrapper, version catalog, both build files, settings, properties), Android manifest with security config, Hilt entry points (`WarpedApplication`, `MainActivity`), Material 3 theme (light+dark), and all domain model contracts — the zero-Android-dependency layer that every other plan builds upon.

## must_haves
- `./gradlew assembleDebug` produces a successful build after this plan
- All 11 domain model files compile with zero Android imports
- `LlmProvider` interface matches the contract from CONTEXT.md D-05 exactly
- Theme supports dark mode via `isSystemInDarkTheme()`
- `AndroidManifest.xml` contains `android:largeHeap="true"` and `android:extractNativeLibs="false"`

## Verification
1. `grep -q "2.1.10" gradle/libs.versions.toml` — Kotlin version pinned
2. `grep -q "compose-bom" gradle/libs.versions.toml` — Compose BOM in version catalog
3. `grep -q "HiltAndroidApp" app/src/main/java/com/warped/WarpedApplication.kt` — Hilt entry point
4. `grep -q "AndroidEntryPoint" app/src/main/java/com/warped/MainActivity.kt` — Activity wired for Hilt
5. `grep -q "largeHeap=\"true\"" app/src/main/AndroidManifest.xml` — Phase 2 prep
6. `grep -q "extractNativeLibs=\"false\"" app/src/main/AndroidManifest.xml` — Phase 2 prep
7. `grep -q "interface LlmProvider" app/src/main/java/com/warped/domain/provider/LlmProvider.kt` — Provider contract
8. `grep -q "sealed interface StreamToken" app/src/main/java/com/warped/domain/model/StreamToken.kt` — Stream token type
9. `grep -q "darkColorScheme" app/src/main/java/com/warped/ui/theme/Theme.kt` — Dark theme support
10. `grep -rn "import android" app/src/main/java/com/warped/domain/` returns empty — Domain zero Android deps

## Tasks

### Task 1: Gradle Wrapper + Build System Files
<read_first>
- gradle/libs.versions.toml (does not exist yet — create new)
</read_first>
<action>
Create the Gradle build system. All files created from scratch since this is a greenfield project.

1. **`gradle/wrapper/gradle-wrapper.properties`** — set `distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip`

2. **`gradle.properties`** — contents:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

3. **`gradle/libs.versions.toml`** — create the full version catalog with all entries from RESEARCH.md §1.2. Include versions block with: kotlin=2.1.10, agp=9.0.0, compose-bom=2025.04.00, hilt=2.59.2, hilt-navigation-compose=1.2.0, room=2.7.1, lifecycle=2.8.7, navigation=2.8.8, okhttp=4.12.0, retrofit=2.11.1, kotlinx-serialization=1.7.3, coroutines=1.9.0, datastore=1.1.3, security-crypto=1.1.0-alpha06, timber=5.0.1, ksp=2.1.10-1.0.31, junit5=5.11.4, mockk=1.13.16, turbine=1.1.0, truth=1.4.4, coroutines-test=1.9.0. Include all libraries and plugins blocks exactly as specified in RESEARCH.md §1.2 lines 26-119.

4. **`settings.gradle.kts`** — pluginManagement with google/mavenCentral/gradlePluginPortal repos, dependencyResolution with google/mavenCentral, rootProject.name="Warped", include(":app"). Exact content from RESEARCH.md §1.4 lines 137-158.

5. **`build.gradle.kts`** (root) — plugins block with: alias(libs.plugins.kotlin.android) apply false, alias(libs.plugins.compose.compiler) apply false, alias(libs.plugins.kotlin.serialization) apply false, alias(libs.plugins.hilt) apply false, alias(libs.plugins.ksp) apply false. Exact from RESEARCH.md §1.3 lines 123-133.

6. **`app/build.gradle.kts`** — Full configuration from RESEARCH.md §1.5 lines 161-277: plugins (kotlin.android, compose.compiler, kotlin.serialization, hilt, ksp), android block (namespace="com.warped", compileSdk=35, defaultConfig with applicationId="com.warped", minSdk=28, targetSdk=35, versionCode=1, versionName="0.1.0", testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"), buildTypes (release: isMinifyEnabled=true, isShrinkResources=true, proguardFiles), compileOptions (JavaVersion.VERSION_17), kotlinOptions (jvmTarget="17"), buildFeatures (compose=true, buildConfig=true). dependencies block with Compose BOM platform, all compose, lifecycle, navigation, hilt, room, networking, serialization, coroutines, datastore, security-crypto, timber, and all test dependencies. Add `room { schemaDirectory("$projectDir/schemas") }` at bottom.

7. Run `./gradlew wrapper --gradle-version 8.13` to generate gradlew and gradlew.bat scripts (if not present). If these already exist, verify the wrapper version.

Do NOT add the llama.cpp native build block (Phase 2 concern) — no NDK, no CMake, no abiFilters in this plan.
</action>
<acceptance_criteria>
- `grep -q "gradle-8.13" gradle/wrapper/gradle-wrapper.properties`
- `grep -q 'alias(libs.plugins.hilt) apply false' build.gradle.kts`
- `grep -q 'namespace = "com.warped"' app/build.gradle.kts`
- `grep -q 'compileSdk = 35' app/build.gradle.kts`
- `grep -q 'minSdk = 28' app/build.gradle.kts`
- `grep -q 'targetSdk = 35' app/build.gradle.kts`
- `grep -q 'compose-bom.*2025.04.00' gradle/libs.versions.toml`
- `grep -q 'hilt.*2.59.2' gradle/libs.versions.toml`
- `grep -q 'room.*2.7.1' gradle/libs.versions.toml`
- `grep -q 'security-crypto.*1.1.0-alpha06' gradle/libs.versions.toml`
- `grep -q 'rootProject.name = "Warped"' settings.gradle.kts`
- `grep -q 'schemaDirectory' app/build.gradle.kts`
- `test -f gradlew` (gradle wrapper script exists)
- `grep -q 'buildFeatures' app/build.gradle.kts` AND `grep -q 'compose = true' app/build.gradle.kts`
- `grep -q 'buildConfig = true' app/build.gradle.kts`
</acceptance_criteria>

### Task 2: AndroidManifest + Security XML Config
<read_first>
- app/src/main/AndroidManifest.xml (does not exist yet — create new)
</read_first>
<action>
1. **`app/src/main/AndroidManifest.xml`** — Copy exact XML from RESEARCH.md §1.6 lines 281-315. Key elements:
   - INTERNET and ACCESS_NETWORK_STATE permissions
   - Application: `android:name=".WarpedApplication"`, `android:allowBackup="false"`, `android:largeHeap="true"`, `android:extractNativeLibs="false"`, `android:networkSecurityConfig="@xml/network_security_config"`, `android:supportsRtl="true"`, `android:theme="@style/Theme.Warped"`, `android:dataExtractionRules="@xml/data_extraction_rules"`
   - Activity: `android:name=".MainActivity"`, `android:exported="true"`, `android:windowSoftInputMode="adjustResize"`, `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"` with launcher intent-filter

2. **`app/src/main/res/xml/network_security_config.xml`** — Copy exact XML from RESEARCH.md §1.7 lines 326-344. `<base-config cleartextTrafficPermitted="false">` with system trust-anchors. `<domain-config cleartextTrafficPermitted="true">` for localhost, 127.0.0.1, 10.0.0.0, 172.16.0.0, 192.168.0.0.

3. **`app/src/main/res/xml/data_extraction_rules.xml`** — Standard Android 12+ backup rules: `<data-extraction-rules><cloud-backup><exclude domain="root"/></cloud-backup><device-transfer><exclude domain="root"/></device-transfer></data-extraction-rules>`

4. **`app/proguard-rules.pro`** — Skeleton with keep rules for Kotlinx Serialization and Room:
```
# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.warped.**$$serializer { *; }
-keepclassmembers class com.warped.** { *** Companion; }
-keepclasseswithmembers class com.warped.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
```
</action>
<acceptance_criteria>
- `grep -q 'android:largeHeap="true"' app/src/main/AndroidManifest.xml`
- `grep -q 'android:extractNativeLibs="false"' app/src/main/AndroidManifest.xml`
- `grep -q 'android:allowBackup="false"' app/src/main/AndroidManifest.xml`
- `grep -q 'networkSecurityConfig' app/src/main/AndroidManifest.xml`
- `grep -q 'dataExtractionRules' app/src/main/AndroidManifest.xml`
- `grep -q 'android:name=".WarpedApplication"' app/src/main/AndroidManifest.xml`
- `grep -q 'android:name=".MainActivity"' app/src/main/AndroidManifest.xml`
- `grep -q 'android.permission.INTERNET' app/src/main/AndroidManifest.xml`
- `grep -q 'windowSoftInputMode="adjustResize"' app/src/main/AndroidManifest.xml`
- `grep -q '<base-config cleartextTrafficPermitted="false">' app/src/main/res/xml/network_security_config.xml`
- `grep -q 'localhost' app/src/main/res/xml/network_security_config.xml`
- `grep -q '192.168.0.0' app/src/main/res/xml/network_security_config.xml`
- `test -f app/src/main/res/xml/network_security_config.xml`
- `test -f app/src/main/res/xml/data_extraction_rules.xml`
- `test -f app/proguard-rules.pro`
</acceptance_criteria>

### Task 3: WarpedApplication + MainActivity (Hilt Entry Points)
<read_first>
- app/src/main/java/com/warped/WarpedApplication.kt (does not exist yet)
- app/src/main/java/com/warped/MainActivity.kt (does not exist yet)
</read_first>
<action>
1. **`app/src/main/java/com/warped/WarpedApplication.kt`** — Create application class with:
```kotlin
package com.warped

import android.app.Application
import android.os.StrictMode
import com.warped.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class WarpedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // RedactingTree will be created in a later task — for now plant a DebugTree
            Timber.plant(Timber.DebugTree())
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
```

2. **`app/src/main/java/com/warped/MainActivity.kt`** — Create single activity:
```kotlin
package com.warped

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.warped.ui.theme.WarpedTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WarpedTheme {
                // NavGraph will be wired in a later plan — placeholder for now
                androidx.compose.material3.Text("Warped")
            }
        }
    }
}
```
</action>
<acceptance_criteria>
- `grep -q '@HiltAndroidApp' app/src/main/java/com/warped/WarpedApplication.kt`
- `grep -q 'class WarpedApplication : Application()' app/src/main/java/com/warped/WarpedApplication.kt`
- `grep -q 'Timber.plant' app/src/main/java/com/warped/WarpedApplication.kt`
- `grep -q 'StrictMode.setThreadPolicy' app/src/main/java/com/warped/WarpedApplication.kt`
- `grep -q '@AndroidEntryPoint' app/src/main/java/com/warped/MainActivity.kt`
- `grep -q 'class MainActivity : ComponentActivity()' app/src/main/java/com/warped/MainActivity.kt`
- `grep -q 'WarpedTheme' app/src/main/java/com/warped/MainActivity.kt`
- `grep -q 'enableEdgeToEdge()' app/src/main/java/com/warped/MainActivity.kt`
</acceptance_criteria>

### Task 4: Material 3 Theme (Color, Type, Shape, Theme)
<read_first>
- app/src/main/java/com/warped/ui/theme/Color.kt (does not exist yet)
- app/src/main/java/com/warped/ui/theme/Type.kt (does not exist yet)
- app/src/main/java/com/warped/ui/theme/Shape.kt (does not exist yet)
- app/src/main/java/com/warped/ui/theme/Theme.kt (does not exist yet)
</read_first>
<action>
1. **`app/src/main/java/com/warped/ui/theme/Color.kt`** — Material 3 color scheme with LM Studio-inspired palette. Define light and dark color values:
```kotlin
package com.warped.ui.theme

import androidx.compose.ui.graphics.Color

// Primary: deep indigo (LM Studio brand feel)
val PrimaryLight = Color(0xFF4F46E5)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryDark = Color(0xFF818CF8)
val OnPrimaryDark = Color(0xFF1E1B4B)

// Surface / Background
val SurfaceLight = Color(0xFFFAFAFA)
val SurfaceDark = Color(0xFF121212)
val BackgroundLight = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF0A0A0A)

// Error
val ErrorLight = Color(0xFFDC2626)
val ErrorDark = Color(0xFFEF4444)

// Chat-specific
val UserBubbleLight = Color(0xFF4F46E5)
val UserBubbleDark = Color(0xFF6366F1)
val AssistantBubbleLight = Color(0xFFF3F4F6)
val AssistantBubbleDark = Color(0xFF1F2937)
```

2. **`app/src/main/java/com/warped/ui/theme/Type.kt`** — Typography using default Material 3 font family:
```kotlin
package com.warped.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val WarpedTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)
```

3. **`app/src/main/java/com/warped/ui/theme/Shape.kt`** — Rounded shapes:
```kotlin
package com.warped.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val WarpedShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
```

4. **`app/src/main/java/com/warped/ui/theme/Theme.kt`** — Light + dark theme:
```kotlin
package com.warped.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight, onPrimary = OnPrimaryLight,
    surface = SurfaceLight, background = BackgroundLight,
    error = ErrorLight,
    surfaceVariant = Color(0xFFF3F4F6),
    outline = Color(0xFFD1D5DB),
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark, onPrimary = OnPrimaryDark,
    surface = SurfaceDark, background = BackgroundDark,
    error = ErrorDark,
    surfaceVariant = Color(0xFF1F2937),
    outline = Color(0xFF374151),
)

@Composable
fun WarpedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable dynamic color for consistent brand
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = WarpedTypography,
        shapes = WarpedShapes,
        content = content
    )
}
```
Also import `androidx.compose.ui.graphics.Color` in Theme.kt for the Color() references in light/dark schemes.
</action>
<acceptance_criteria>
- `grep -q 'PrimaryLight' app/src/main/java/com/warped/ui/theme/Color.kt`
- `grep -q 'SurfaceDark' app/src/main/java/com/warped/ui/theme/Color.kt`
- `grep -q 'fun WarpedTheme' app/src/main/java/com/warped/ui/theme/Theme.kt`
- `grep -q 'darkColorScheme' app/src/main/java/com/warped/ui/theme/Theme.kt`
- `grep -q 'lightColorScheme' app/src/main/java/com/warped/ui/theme/Theme.kt`
- `grep -q 'isSystemInDarkTheme()' app/src/main/java/com/warped/ui/theme/Theme.kt`
- `grep -q 'MaterialTheme(' app/src/main/java/com/warped/ui/theme/Theme.kt`
- `grep -q 'WarpedTypography' app/src/main/java/com/warped/ui/theme/Type.kt`
- `grep -q 'WarpedShapes' app/src/main/java/com/warped/ui/theme/Shape.kt`
- `grep -q 'RoundedCornerShape' app/src/main/java/com/warped/ui/theme/Shape.kt`
</acceptance_criteria>

### Task 5: Domain Models (All 11 Types)
<read_first>
- .planning/phases/01-foundation-remote-chat/01-CONTEXT.md (D-05, D-06 for exact LLMProvider contract and domain data class specs)
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §9 (lines 1730-1823 for full domain model code)
</read_first>
<action>
Create ALL domain model files in `app/src/main/java/com/warped/domain/model/`. Each file must have ZERO Android imports (no android.*, no androidx.*, no com.warped.* cross-imports to data/ or ui/). Only kotlin.* and java.time.* imports allowed.

1. **`Role.kt`** — `enum class Role { SYSTEM, USER, ASSISTANT }`

2. **`ProviderType.kt`** — `enum class ProviderType { OPENAI, OLLAMA, LM_STUDIO, CUSTOM, LOCAL }` (LOCAL is for Phase 2 but define now)

3. **`StreamToken.kt`** — `sealed interface StreamToken { data class Delta(val content: String) : StreamToken; data object Done : StreamToken; data class Error(val message: String) : StreamToken }`

4. **`ChatMessage.kt`** — data class with fields: `id: String = java.util.UUID.randomUUID().toString()`, `role: Role`, `content: String`, `tokenCount: Int = 0`, `createdAt: java.time.Instant = java.time.Instant.now()`

5. **`Conversation.kt`** — data class with fields: `id: Long`, `title: String`, `providerType: ProviderType`, `endpointId: Long`, `modelId: String? = null`, `systemPrompt: String? = null`, `createdAt: java.time.Instant`, `updatedAt: java.time.Instant`

6. **`GenerationParameters.kt`** — data class with fields: `temperature: Float = 0.7f`, `topP: Float = 0.9f`, `topK: Int = 40`, `repeatPenalty: Float = 1.1f`, `maxTokens: Int = 2048`, `contextSize: Int = 4096`, `seed: Int = -1`, `threads: Int = 4`

7. **`ChatRequest.kt`** — data class with fields: `messages: List<ChatMessage>`, `parameters: GenerationParameters = GenerationParameters()`

8. **`ModelInfo.kt`** — data class with fields: `id: String`, `name: String`, `providerType: ProviderType`

9. **`ConnectionStatus.kt`** — `enum class ConnectionStatus { Unknown, Connected, Connecting, Disconnected }`

10. **`Endpoint.kt`** — data class with fields: `id: Long = 0`, `name: String`, `url: String`, `apiType: ProviderType`, `isActive: Boolean = false`, `createdAt: java.time.Instant = java.time.Instant.now()`

11. **`LlmProvider.kt`** in `app/src/main/java/com/warped/domain/provider/` — Exact interface from CONTEXT.md D-05:
```kotlin
package com.warped.domain.provider

import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import kotlinx.coroutines.flow.Flow

interface LlmProvider {
    val type: ProviderType
    fun chat(request: ChatRequest): Flow<StreamToken>
    suspend fun listModels(): Result<List<ModelInfo>>
    suspend fun testConnection(): Result<ConnectionStatus>
}
```

12. **`ChatRepository.kt`** in `app/src/main/java/com/warped/domain/repository/` — interface with suspend methods: `observeConversations(): Flow<List<Conversation>>`, `loadConversation(conversationId: Long): Pair<Conversation, List<ChatMessage>>?`, `createConversation(title: String, providerType: ProviderType, endpointId: Long): Long`, `saveMessage(conversationId: Long, message: ChatMessage)`, `updateConversationTitle(conversationId: Long, title: String)`, `deleteConversation(conversationId: Long)`, `deleteAllConversations()`

13. **`EndpointRepository.kt`** in `app/src/main/java/com/warped/domain/repository/` — interface with suspend methods: `observeEndpoints(): Flow<List<Endpoint>>`, `getActive(): Endpoint?`, `saveEndpoint(endpoint: Endpoint)`, `deleteEndpoint(endpointId: Long)`, `activateEndpoint(endpointId: Long)`

14. **`ModelRepository.kt`** in `app/src/main/java/com/warped/domain/repository/` — interface with suspend methods: `observeModels(): Flow<List<ModelInfo>>`, `refreshModels(endpointId: Long)`
</action>
<acceptance_criteria>
- `grep -q 'enum class Role' app/src/main/java/com/warped/domain/model/Role.kt`
- `grep -q 'SYSTEM, USER, ASSISTANT' app/src/main/java/com/warped/domain/model/Role.kt`
- `grep -q 'enum class ProviderType' app/src/main/java/com/warped/domain/model/ProviderType.kt`
- `grep -q 'OPENAI, OLLAMA, LM_STUDIO, CUSTOM, LOCAL' app/src/main/java/com/warped/domain/model/ProviderType.kt`
- `grep -q 'sealed interface StreamToken' app/src/main/java/com/warped/domain/model/StreamToken.kt`
- `grep -q 'data class Delta' app/src/main/java/com/warped/domain/model/StreamToken.kt`
- `grep -q 'data object Done' app/src/main/java/com/warped/domain/model/StreamToken.kt`
- `grep -q 'data class Error' app/src/main/java/com/warped/domain/model/StreamToken.kt`
- `grep -q 'data class ChatMessage' app/src/main/java/com/warped/domain/model/ChatMessage.kt`
- `grep -q 'data class Conversation' app/src/main/java/com/warped/domain/model/Conversation.kt`
- `grep -q 'data class ChatRequest' app/src/main/java/com/warped/domain/model/ChatRequest.kt`
- `grep -q 'data class GenerationParameters' app/src/main/java/com/warped/domain/model/GenerationParameters.kt`
- `grep -q 'data class ModelInfo' app/src/main/java/com/warped/domain/model/ModelInfo.kt`
- `grep -q 'enum class ConnectionStatus' app/src/main/java/com/warped/domain/model/ConnectionStatus.kt`
- `grep -q 'data class Endpoint' app/src/main/java/com/warped/domain/model/Endpoint.kt`
- `grep -q 'interface LlmProvider' app/src/main/java/com/warped/domain/provider/LlmProvider.kt`
- `grep -q 'fun chat.*Flow.*StreamToken' app/src/main/java/com/warped/domain/provider/LlmProvider.kt`
- `grep -q 'interface ChatRepository' app/src/main/java/com/warped/domain/repository/ChatRepository.kt`
- `grep -q 'interface EndpointRepository' app/src/main/java/com/warped/domain/repository/EndpointRepository.kt`
- `grep -q 'interface ModelRepository' app/src/main/java/com/warped/domain/repository/ModelRepository.kt`
- `test ! $(grep -rn 'import android' app/src/main/java/com/warped/domain/ 2>/dev/null | wc -l) -gt 0` (zero Android imports in domain layer)
</acceptance_criteria>

### Task 6: Android Resources (strings, themes)
<read_first>
- app/src/main/res/values/strings.xml (does not exist yet)
- app/src/main/res/values/themes.xml (does not exist yet)
</read_first>
<action>
1. **`app/src/main/res/values/strings.xml`**:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Warped</string>
    <string name="tab_chat">Chat</string>
    <string name="tab_endpoints">Endpoints</string>
    <string name="tab_models">Models</string>
    <string name="settings">Settings</string>
    <string name="send">Send</string>
    <string name="stop">Stop</string>
    <string name="cancel">Cancel</string>
    <string name="save">Save</string>
    <string name="delete">Delete</string>
    <string name="edit">Edit</string>
    <string name="test_connection">Test Connection</string>
    <string name="no_endpoints">No endpoints configured</string>
    <string name="no_models">No models found</string>
    <string name="connecting">Connecting…</string>
    <string name="connected">Connected</string>
    <string name="disconnected">Disconnected</string>
    <string name="error_network">Can\'t connect to server. Check the URL and try again.</string>
    <string name="error_auth">Authentication failed. Check your API key.</string>
    <string name="connection_lost">Connection lost. Tap to retry.</string>
    <string name="new_chat">New Chat</string>
    <string name="type_message">Type a message…</string>
</resources>
```

2. **`app/src/main/res/values/themes.xml`**:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Warped" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

3. Create empty placeholder launcher icon directories or use a simple vector drawable. Minimum: ensure `app/src/main/res/mipmap-hdpi/`, `mipmap-mdpi/`, `mipmap-xhdpi/`, `mipmap-xxhdpi/`, `mipmap-xxxhdpi/` directories exist. Create a minimal `ic_launcher.xml` adaptive icon in `res/mipmap-anydpi-v26/` or skip for now — the app won't crash without icons, just show default.
</action>
<acceptance_criteria>
- `grep -q 'app_name.*Warped' app/src/main/res/values/strings.xml`
- `grep -q 'tab_chat' app/src/main/res/values/strings.xml`
- `grep -q 'tab_endpoints' app/src/main/res/values/strings.xml`
- `grep -q 'error_network' app/src/main/res/values/strings.xml`
- `grep -q 'type_message' app/src/main/res/values/strings.xml`
- `grep -q 'Theme.Warped' app/src/main/res/values/themes.xml`
- `grep -q 'NoActionBar' app/src/main/res/values/themes.xml`
</acceptance_criteria>
---
plan: 02-data-layer
wave: 2
depends_on: [01-foundation]
autonomous: true
requirements_addressed: [PERS-01, PERS-02, SEC-01]
files_modified:
  - app/src/main/java/com/warped/data/local/db/entity/ConversationEntity.kt
  - app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt
  - app/src/main/java/com/warped/data/local/db/entity/RemoteEndpointEntity.kt
  - app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt
  - app/src/main/java/com/warped/data/local/db/dao/MessageDao.kt
  - app/src/main/java/com/warped/data/local/db/dao/RemoteEndpointDao.kt
  - app/src/main/java/com/warped/data/local/db/converter/Converters.kt
  - app/src/main/java/com/warped/data/local/db/AppDatabase.kt
  - app/src/main/java/com/warped/data/local/security/KeystoreManager.kt
  - app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt
  - app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt
  - app/src/main/java/com/warped/data/repository/EndpointRepositoryImpl.kt
  - app/src/main/java/com/warped/data/repository/ModelRepositoryImpl.kt
  - app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt
---

# Plan 02: Room Database, Security & Repository Implementations

## Objective
Implement the full data layer: Room database with 3 entities and 3 DAOs, Android Keystore-backed encrypted API key storage, and repository implementations that bridge domain ↔ data with entity↔domain mappers. This plan makes PERS-01 (chat survives restart), PERS-02 (endpoints survive restart), and SEC-01 (encrypted keys) real.

## must_haves
- Room database compiles with entities `conversations`, `messages`, `endpoints` and all 3 DAOs
- `ConversationDao.observeAll()` returns `Flow<List<ConversationEntity>>`
- `RemoteEndpointDao` supports `getActive()`, `activate(id)`, `deactivateAll()` for single-active-endpoint pattern
- `MessageEntity` has `ForeignKey` cascade delete from `conversations`
- `KeystoreManager` uses `EncryptedSharedPreferences` with AES-256-GCM via `MasterKey`
- `ApiKeyStore.storeKey()` accepts `CharArray`, zero-fills after use
- `ChatRepositoryImpl.saveMessage()` creates a conversation automatically if none exists (first message)
- All repository implementations map between domain types and Room entities

## Verification
1. `grep -q '@Database' app/src/main/java/com/warped/data/local/db/AppDatabase.kt`
2. `grep -q 'conversations\|messages\|endpoints' app/src/main/java/com/warped/data/local/db/AppDatabase.kt`
3. `grep -q 'CASCADE' app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt` — FK cascade delete
4. `grep -q 'EncryptedSharedPreferences' app/src/main/java/com/warped/data/local/security/KeystoreManager.kt`
5. `grep -q 'MasterKey' app/src/main/java/com/warped/data/local/security/KeystoreManager.kt`
6. `grep -q 'CharArray' app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt`
7. `grep -q 'toDomain()' app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt`
8. `grep -q 'class ChatRepositoryImpl' app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt`
9. `grep -q '@Inject constructor' app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt`

## Tasks

### Task 1: Room Entities (ConversationEntity, MessageEntity, RemoteEndpointEntity)
<read_first>
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §3.1 (lines 503-556 for exact entity definitions)
- app/src/main/java/com/warped/domain/model/Role.kt (reference for String representation of roles)
- app/src/main/java/com/warped/domain/model/ProviderType.kt (reference for String representation of types)
</read_first>
<action>
Create the 3 Room entity classes. Store `Role` and `ProviderType` as `String` in entities (NOT as domain enums) — this keeps the domain layer pure. Mapping to domain enums happens in EntityMappers.

1. **`app/src/main/java/com/warped/data/local/db/entity/ConversationEntity.kt`** — Exact code from RESEARCH.md §3.1 lines 505-517:
```kotlin
package com.warped.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "provider_type") val providerType: String,
    @ColumnInfo(name = "endpoint_id") val endpointId: Long,
    @ColumnInfo(name = "model_id") val modelId: String?,
    @ColumnInfo(name = "system_prompt") val systemPrompt: String?
)
```

2. **`app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt`** — Exact code from RESEARCH.md §3.1 lines 520-541 with ForeignKey cascade and index:
```kotlin
package com.warped.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversation_id"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "token_count") val tokenCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```

3. **`app/src/main/java/com/warped/data/local/db/entity/RemoteEndpointEntity.kt`** — Exact code from RESEARCH.md §3.1 lines 544-555:
```kotlin
package com.warped.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "endpoints")
data class RemoteEndpointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "api_type") val apiType: String,
    @ColumnInfo(name = "encrypted_api_key_ref") val encryptedApiKeyRef: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false
)
```
</action>
<acceptance_criteria>
- `grep -q '@Entity(tableName = "conversations")' app/src/main/java/com/warped/data/local/db/entity/ConversationEntity.kt`
- `grep -q 'autoGenerate = true' app/src/main/java/com/warped/data/local/db/entity/ConversationEntity.kt`
- `grep -q '@Entity(tableName = "messages")' app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt`
- `grep -q 'ForeignKey.*ConversationEntity' app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt`
- `grep -q 'onDelete = ForeignKey.CASCADE' app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt`
- `grep -q 'Index.*conversation_id' app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt`
- `grep -q '@Entity(tableName = "endpoints")' app/src/main/java/com/warped/data/local/db/entity/RemoteEndpointEntity.kt`
- `grep -q 'encrypted_api_key_ref' app/src/main/java/com/warped/data/local/db/entity/RemoteEndpointEntity.kt`
</acceptance_criteria>

### Task 2: Room DAOs (ConversationDao, MessageDao, RemoteEndpointDao)
<read_first>
- app/src/main/java/com/warped/data/local/db/entity/ConversationEntity.kt
- app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt
- app/src/main/java/com/warped/data/local/db/entity/RemoteEndpointEntity.kt
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §3.2 (lines 559-629 for exact DAO definitions)
</read_first>
<action>
Create the 3 DAO interfaces.

1. **`app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt`** — Exact from RESEARCH.md §3.2 lines 560-578:
- `observeAll(): Flow<List<ConversationEntity>>` with `ORDER BY updated_at DESC`
- `getById(id: Long): ConversationEntity?`
- `upsert(conversation: ConversationEntity): Long` with `OnConflictStrategy.REPLACE`
- `updateTimestamp(id: Long, timestamp: Long)` via `UPDATE conversations SET updated_at = :timestamp WHERE id = :id`
- `delete(conversation: ConversationEntity)`

2. **`app/src/main/java/com/warped/data/local/db/dao/MessageDao.kt`** — Exact from RESEARCH.md §3.2 lines 581-602:
- `observeByConversation(conversationId: Long): Flow<List<MessageEntity>>` with `ORDER BY created_at ASC`
- `getByConversation(conversationId: Long): List<MessageEntity>` suspend
- `insert(message: MessageEntity): Long`
- `update(message: MessageEntity)` 
- `deleteByConversation(conversationId: Long)` via `DELETE FROM messages WHERE conversation_id = :conversationId`
- `deleteAll()` via `DELETE FROM messages`

3. **`app/src/main/java/com/warped/data/local/db/dao/RemoteEndpointDao.kt`** — Exact from RESEARCH.md §3.2 lines 605-629:
- `observeAll(): Flow<List<RemoteEndpointEntity>>` with `ORDER BY created_at DESC`
- `getById(id: Long): RemoteEndpointEntity?`
- `getActive(): RemoteEndpointEntity?` via `SELECT * FROM endpoints WHERE is_active = 1 LIMIT 1`
- `upsert(endpoint: RemoteEndpointEntity): Long` with `OnConflictStrategy.REPLACE`
- `deactivateAll()` via `UPDATE endpoints SET is_active = 0`
- `activate(id: Long)` via `UPDATE endpoints SET is_active = 1 WHERE id = :id`
- `delete(endpoint: RemoteEndpointEntity)`
</action>
<acceptance_criteria>
- `grep -q 'interface ConversationDao' app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt`
- `grep -q 'fun observeAll(): Flow' app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt`
- `grep -q 'ORDER BY updated_at DESC' app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt`
- `grep -q 'OnConflictStrategy.REPLACE' app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt`
- `grep -q 'interface MessageDao' app/src/main/java/com/warped/data/local/db/dao/MessageDao.kt`
- `grep -q 'DELETE FROM messages' app/src/main/java/com/warped/data/local/db/dao/MessageDao.kt`
- `grep -q 'interface RemoteEndpointDao' app/src/main/java/com/warped/data/local/db/dao/RemoteEndpointDao.kt`
- `grep -q 'is_active = 1' app/src/main/java/com/warped/data/local/db/dao/RemoteEndpointDao.kt`
- `grep -q 'fun deactivateAll()' app/src/main/java/com/warped/data/local/db/dao/RemoteEndpointDao.kt`
- `grep -q 'fun activate' app/src/main/java/com/warped/data/local/db/dao/RemoteEndpointDao.kt`
</acceptance_criteria>

### Task 3: Room Type Converters + AppDatabase
<read_first>
- app/src/main/java/com/warped/data/local/db/entity/ConversationEntity.kt
- app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt
- app/src/main/java/com/warped/data/local/db/entity/RemoteEndpointEntity.kt
- app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt
- app/src/main/java/com/warped/data/local/db/dao/MessageDao.kt
- app/src/main/java/com/warped/data/local/db/dao/RemoteEndpointDao.kt
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §3.3 and §3.4 (lines 632-678)
</read_first>
<action>
1. **`app/src/main/java/com/warped/data/local/db/converter/Converters.kt`** — Exact from RESEARCH.md §3.3 lines 635-646. Minimal converter class (domain enums are stored as String, mapping happens in repository layer):
```kotlin
package com.warped.data.local.db.converter

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Long? = value

    @TypeConverter
    fun toTimestamp(value: Long?): Long? = value
}
```

2. **`app/src/main/java/com/warped/data/local/db/AppDatabase.kt`** — Exact from RESEARCH.md §3.4 lines 653-669:
```kotlin
package com.warped.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.warped.data.local.db.converter.Converters
import com.warped.data.local.db.dao.ConversationDao
import com.warped.data.local.db.dao.MessageDao
import com.warped.data.local.db.dao.RemoteEndpointDao
import com.warped.data.local.db.entity.ConversationEntity
import com.warped.data.local.db.entity.MessageEntity
import com.warped.data.local.db.entity.RemoteEndpointEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        RemoteEndpointEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun remoteEndpointDao(): RemoteEndpointDao
}
```
</action>
<acceptance_criteria>
- `grep -q 'class Converters' app/src/main/java/com/warped/data/local/db/converter/Converters.kt`
- `grep -q '@TypeConverter' app/src/main/java/com/warped/data/local/db/converter/Converters.kt`
- `grep -q '@Database' app/src/main/java/com/warped/data/local/db/AppDatabase.kt`
- `grep -q 'ConversationEntity::class' app/src/main/java/com/warped/data/local/db/AppDatabase.kt`
- `grep -q 'MessageEntity::class' app/src/main/java/com/warped/data/local/db/AppDatabase.kt`
- `grep -q 'RemoteEndpointEntity::class' app/src/main/java/com/warped/data/local/db/AppDatabase.kt`
- `grep -q 'version = 1' app/src/main/java/com/warped/data/local/db/AppDatabase.kt`
- `grep -q 'exportSchema = true' app/src/main/java/com/warped/data/local/db/AppDatabase.kt`
- `grep -q 'TypeConverters(Converters::class)' app/src/main/java/com/warped/data/local/db/AppDatabase.kt`
- `grep -q 'abstract fun conversationDao()' app/src/main/java/com/warped/data/local/db/AppDatabase.kt`
- `grep -q 'abstract fun messageDao()' app/src/main/java/com/warped/data/local/db/AppDatabase.kt`
- `grep -q 'abstract fun remoteEndpointDao()' app/src/main/java/com/warped/data/local/db/AppDatabase.kt`
</acceptance_criteria>

### Task 4: Entity ↔ Domain Mappers
<read_first>
- app/src/main/java/com/warped/domain/model/ChatMessage.kt
- app/src/main/java/com/warped/domain/model/Conversation.kt
- app/src/main/java/com/warped/domain/model/Endpoint.kt
- app/src/main/java/com/warped/domain/model/Role.kt
- app/src/main/java/com/warped/domain/model/ProviderType.kt
- app/src/main/java/com/warped/data/local/db/entity/ConversationEntity.kt
- app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt
- app/src/main/java/com/warped/data/local/db/entity/RemoteEndpointEntity.kt
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §3.5 (lines 680-698 for mapping pattern)
</read_first>
<action>
Create **`app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt`** with extension functions following the exact pattern from RESEARCH.md §3.5:

```kotlin
package com.warped.data.local.db.entity

import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Conversation
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import java.time.Instant

fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id.toString(),
    role = Role.valueOf(role),
    content = content,
    tokenCount = tokenCount,
    createdAt = Instant.ofEpochMilli(createdAt)
)

fun ChatMessage.toEntity(conversationId: Long): MessageEntity = MessageEntity(
    conversationId = conversationId,
    role = role.name,
    content = content,
    tokenCount = tokenCount,
    createdAt = createdAt.toEpochMilli()
)

fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    providerType = ProviderType.valueOf(providerType),
    endpointId = endpointId,
    modelId = modelId,
    systemPrompt = systemPrompt,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt)
)

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    providerType = providerType.name,
    endpointId = endpointId,
    modelId = modelId,
    systemPrompt = systemPrompt,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli()
)

fun RemoteEndpointEntity.toDomain(): Endpoint = Endpoint(
    id = id,
    name = name,
    url = url,
    apiType = ProviderType.valueOf(apiType),
    isActive = isActive,
    createdAt = Instant.ofEpochMilli(createdAt)
)

fun Endpoint.toEntity(encryptedApiKeyRef: String? = null): RemoteEndpointEntity = RemoteEndpointEntity(
    id = id,
    name = name,
    url = url,
    apiType = apiType.name,
    encryptedApiKeyRef = encryptedApiKeyRef,
    createdAt = createdAt.toEpochMilli(),
    isActive = isActive
)
```
</action>
<acceptance_criteria>
- `grep -q 'fun MessageEntity.toDomain()' app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt`
- `grep -q 'fun ChatMessage.toEntity' app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt`
- `grep -q 'fun ConversationEntity.toDomain()' app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt`
- `grep -q 'fun Conversation.toEntity()' app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt`
- `grep -q 'fun RemoteEndpointEntity.toDomain()' app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt`
- `grep -q 'fun Endpoint.toEntity' app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt`
- `grep -q 'Role.valueOf(role)' app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt`
- `grep -q 'ProviderType.valueOf' app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt`
</acceptance_criteria>

### Task 5: KeystoreManager + ApiKeyStore (Security)
<read_first>
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §8.1 and §8.2 (lines 1606-1694 for exact security code)
- .planning/research/PITFALLS.md §6.1 (lines 213-219 for plaintext key risks)
</read_first>
<action>
1. **`app/src/main/java/com/warped/data/local/security/KeystoreManager.kt`** — Exact code from RESEARCH.md §8.1 lines 1609-1656:
```kotlin
package com.warped.data.local.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKeyAlias = "_warped_master_key_"

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyGenParameterSpec(
                KeyGenParameterSpec.Builder(
                    masterKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "warped_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun put(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    fun get(key: String): String? {
        return encryptedPrefs.getString(key, null)
    }

    fun remove(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }

    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }
}
```

2. **`app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt`** — Exact code from RESEARCH.md §8.2 lines 1662-1694. Uses `CharArray` for in-memory key handling with zero-fill after use. Key aliases use pattern `"api_key_{endpointId}"`:
```kotlin
package com.warped.data.local.security

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyStore @Inject constructor(
    private val keystoreManager: KeystoreManager
) {
    fun storeKey(endpointId: Long, apiKey: CharArray) {
        val alias = "api_key_$endpointId"
        keystoreManager.put(alias, String(apiKey))
        apiKey.fill('0')
    }

    fun getKey(endpointId: Long): CharArray? {
        val alias = "api_key_$endpointId"
        return keystoreManager.get(alias)?.toCharArray()
    }

    fun deleteKey(endpointId: Long) {
        val alias = "api_key_$endpointId"
        keystoreManager.remove(alias)
    }

    fun deleteAllKeys(endpointIds: List<Long>) {
        endpointIds.forEach { deleteKey(it) }
    }
}
```
</action>
<acceptance_criteria>
- `grep -q 'class KeystoreManager' app/src/main/java/com/warped/data/local/security/KeystoreManager.kt`
- `grep -q 'EncryptedSharedPreferences' app/src/main/java/com/warped/data/local/security/KeystoreManager.kt`
- `grep -q 'MasterKey' app/src/main/java/com/warped/data/local/security/KeystoreManager.kt`
- `grep -q 'AES256_GCM' app/src/main/java/com/warped/data/local/security/KeystoreManager.kt`
- `grep -q 'KeyGenParameterSpec' app/src/main/java/com/warped/data/local/security/KeystoreManager.kt`
- `grep -q 'class ApiKeyStore' app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt`
- `grep -q 'CharArray' app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt`
- `grep -q 'apiKey.fill.*0' app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt`
- `grep -q 'api_key_' app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt`
- `grep -q '@Singleton' app/src/main/java/com/warped/data/local/security/KeystoreManager.kt`
- `grep -q '@Inject constructor' app/src/main/java/com/warped/data/local/security/KeystoreManager.kt`
</acceptance_criteria>

### Task 6: Repository Implementations
<read_first>
- app/src/main/java/com/warped/domain/repository/ChatRepository.kt
- app/src/main/java/com/warped/domain/repository/EndpointRepository.kt
- app/src/main/java/com/warped/domain/repository/ModelRepository.kt
- app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt
- app/src/main/java/com/warped/data/local/db/dao/MessageDao.kt
- app/src/main/java/com/warped/data/local/db/dao/RemoteEndpointDao.kt
- app/src/main/java/com/warped/data/local/db/entity/EntityMappers.kt
- app/src/main/java/com/warped/domain/model/ChatMessage.kt
- app/src/main/java/com/warped/domain/model/Conversation.kt
- app/src/main/java/com/warped/domain/model/Endpoint.kt
- app/src/main/java/com/warped/domain/model/ProviderType.kt
</read_first>
<action>
Create 3 repository implementations in `app/src/main/java/com/warped/data/repository/`. Each uses `@Inject constructor(@Singleton)` and maps between domain types and Room entities via the extension functions from EntityMappers.

1. **`app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt`** — Implements `ChatRepository`:
```kotlin
package com.warped.data.repository

import com.warped.data.local.db.dao.ConversationDao
import com.warped.data.local.db.dao.MessageDao
import com.warped.data.local.db.entity.toDomain
import com.warped.data.local.db.entity.toEntity
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.Conversation
import com.warped.domain.model.ProviderType
import com.warped.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ChatRepository {

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun loadConversation(conversationId: Long): Pair<Conversation, List<ChatMessage>>? {
        val conv = conversationDao.getById(conversationId) ?: return null
        val msgs = messageDao.getByConversation(conversationId).map { it.toDomain() }
        return conv.toDomain() to msgs
    }

    override suspend fun createConversation(title: String, providerType: ProviderType, endpointId: Long): Long {
        val now = System.currentTimeMillis()
        val entity = com.warped.data.local.db.entity.ConversationEntity(
            title = title,
            createdAt = now,
            updatedAt = now,
            providerType = providerType.name,
            endpointId = endpointId
        )
        return conversationDao.upsert(entity)
    }

    override suspend fun saveMessage(conversationId: Long, message: ChatMessage) {
        messageDao.insert(message.toEntity(conversationId))
        conversationDao.updateTimestamp(conversationId, System.currentTimeMillis())
    }

    override suspend fun updateConversationTitle(conversationId: Long, title: String) {
        val entity = conversationDao.getById(conversationId) ?: return
        conversationDao.upsert(entity.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteConversation(conversationId: Long) {
        val entity = conversationDao.getById(conversationId) ?: return
        conversationDao.delete(entity)
    }

    override suspend fun deleteAllConversations() {
        messageDao.deleteAll()
        // conversations will need explicit delete too — iterate and delete
    }
}
```

2. **`app/src/main/java/com/warped/data/repository/EndpointRepositoryImpl.kt`** — Implements `EndpointRepository`:
```kotlin
package com.warped.data.repository

import com.warped.data.local.db.dao.RemoteEndpointDao
import com.warped.data.local.db.entity.toDomain
import com.warped.data.local.db.entity.toEntity
import com.warped.data.local.security.ApiKeyStore
import com.warped.domain.model.Endpoint
import com.warped.domain.repository.EndpointRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EndpointRepositoryImpl @Inject constructor(
    private val endpointDao: RemoteEndpointDao,
    private val apiKeyStore: ApiKeyStore
) : EndpointRepository {

    override fun observeEndpoints(): Flow<List<Endpoint>> =
        endpointDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getActive(): Endpoint? =
        endpointDao.getActive()?.toDomain()

    override suspend fun saveEndpoint(endpoint: Endpoint) {
        val existing = if (endpoint.id != 0L) endpointDao.getById(endpoint.id) else null
        val keyRef = existing?.encryptedApiKeyRef ?: "api_key_${endpoint.id}"
        val id = endpointDao.upsert(endpoint.toEntity(encryptedApiKeyRef = keyRef))
        if (endpoint.isActive) {
            endpointDao.activate(id)
        }
    }

    override suspend fun deleteEndpoint(endpointId: Long) {
        val entity = endpointDao.getById(endpointId) ?: return
        apiKeyStore.deleteKey(endpointId)
        endpointDao.delete(entity)
    }

    override suspend fun activateEndpoint(endpointId: Long) {
        endpointDao.deactivateAll()
        endpointDao.activate(endpointId)
    }
}
```

3. **`app/src/main/java/com/warped/data/repository/ModelRepositoryImpl.kt`** — Implements `ModelRepository` (stub for Phase 1 — models come from remote providers, not Room yet):
```kotlin
package com.warped.data.repository

import com.warped.domain.model.ModelInfo
import com.warped.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepositoryImpl @Inject constructor() : ModelRepository {
    private val models = MutableStateFlow<List<ModelInfo>>(emptyList())

    override fun observeModels(): Flow<List<ModelInfo>> = models

    override suspend fun refreshModels(endpointId: Long) {
        // Phase 1: models are fetched on-demand from the provider, not persisted.
        // The ViewModel/ProviderRouter handles model listing directly.
        // This repository exists for Phase 2+ when we cache model lists.
    }
}
```
</action>
<acceptance_criteria>
- `grep -q 'class ChatRepositoryImpl' app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt`
- `grep -q ': ChatRepository' app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt`
- `grep -q '@Inject constructor' app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt`
- `grep -q 'fun observeConversations' app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt`
- `grep -q 'fun saveMessage' app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt`
- `grep -q 'class EndpointRepositoryImpl' app/src/main/java/com/warped/data/repository/EndpointRepositoryImpl.kt`
- `grep -q ': EndpointRepository' app/src/main/java/com/warped/data/repository/EndpointRepositoryImpl.kt`
- `grep -q 'apiKeyStore.deleteKey' app/src/main/java/com/warped/data/repository/EndpointRepositoryImpl.kt`
- `grep -q 'fun deleteEndpoint' app/src/main/java/com/warped/data/repository/EndpointRepositoryImpl.kt`
- `grep -q 'class ModelRepositoryImpl' app/src/main/java/com/warped/data/repository/ModelRepositoryImpl.kt`
</acceptance_criteria>
---
plan: 03-networking-providers
wave: 3
depends_on: [02-data-layer]
autonomous: true
requirements_addressed: [PROV-03, PROV-04, PROV-05, CHAT-01, SEC-01]
files_modified:
  - app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt
  - app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt
  - app/src/main/java/com/warped/data/remote/network/SseParser.kt
  - app/src/main/java/com/warped/data/remote/network/SseEvent.kt
  - app/src/main/java/com/warped/data/remote/network/SseExtensions.kt
  - app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt
  - app/src/main/java/com/warped/data/remote/api/OllamaApi.kt
  - app/src/main/java/com/warped/data/remote/api/CustomApi.kt
  - app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt
  - app/src/main/java/com/warped/data/remote/dto/OpenAiModelListResponse.kt
  - app/src/main/java/com/warped/data/remote/dto/OllamaChatRequest.kt
  - app/src/main/java/com/warped/data/remote/dto/OllamaModelListResponse.kt
  - app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt
  - app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt
  - app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
---

# Plan 03: Networking Layer & Provider Implementations

## Objective
Build the entire remote networking stack: OkHttp client factory with auth interceptor, SSE parser with line-accumulator buffer (handles fragmented TCP chunks per PITFALLS.md §4.1), Retrofit API interfaces for OpenAI/Ollama/Custom endpoints, DTOs for all JSON shapes, and 4 provider implementations (`OpenAIProvider`, `OllamaProvider`, `LMStudioProvider`, `CustomProvider`) that implement `LlmProvider` with streaming chat, model listing, and connection testing — plus the `ProviderRouter` factory.

## must_haves
- `SseParser` feeds data through a `StringBuilder` buffer, splits only on `\n\n` boundaries — detects `[DONE]`, handles split JSON across chunks
- `ResponseBody.asSseFlow()` wraps SSE parsing in a `flow {}` builder with proper error handling and cleanup
- OpenAI-compatible streaming uses `POST /v1/chat/completions` with `stream: true`
- Ollama provider handles newline-delimited JSON (NOT double-newline SSE) per RESEARCH.md §4.7
- `LMStudioProvider` reuses OpenAI DTOs but defaults base URL to `http://localhost:1234`
- `ProviderRouter.resolve(endpoint)` maps `ProviderType` → configured `LlmProvider`
- `AuthInterceptor` injects `Authorization: Bearer` header from `ApiKeyStore` per request (never caches key)
- All networking runs on `Dispatchers.IO` via `flowOn(Dispatchers.IO)`

## Verification
1. `grep -q 'class SseParser' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
2. `grep -q 'buffer.indexOf("\\n\\n")' app/src/main/java/com/warped/data/remote/network/SseParser.kt` — double-newline boundary
3. `grep -q '\[DONE\]' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt` — terminal event handling
4. `grep -q 'fun asSseFlow' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`
5. `grep -q 'v1/chat/completions' app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt`
6. `grep -q 'api/chat' app/src/main/java/com/warped/data/remote/api/OllamaApi.kt`
7. `grep -q 'class OpenAIProvider' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
8. `grep -q ': LlmProvider' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
9. `grep -q 'class ProviderRouter' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
10. `grep -q 'fun resolve.*LlmProvider' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
11. `grep -q 'asSseFlow\|asOllamaFlow' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt` — uses SSE flow extension

## Tasks

### Task 1: OkHttp Client Factory + Auth Interceptor
<read_first>
- app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt (the auth interceptor uses ApiKeyStore to resolve keys)
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §4.1 and §4.2 (lines 703-768 for exact OkHttp + AuthInterceptor code)
</read_first>
<action>
1. **`app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt`** — Exact from RESEARCH.md §4.2 lines 742-768. An OkHttp `Interceptor` that:
   - Gets the `endpointId` from the request's `tag(Long::class.java)`
   - Calls `apiKeyStore.getKey(endpointId)` to retrieve the key
   - Adds `Authorization: Bearer {key}` header if key exists
   - `@Inject constructor(apiKeyStore: ApiKeyStore)`, annotated `@Singleton`
   - IMPORTANT: Do NOT store the key in a field — resolve on every request. Use `apiKeyStore.getKey(endpointId)` inline and zero-fill after the request proceeds.

2. **`app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt`** — Exact from RESEARCH.md §4.1 lines 706-737 with one change: the `create()` method is the base, `createForEndpoint()` is variant. Constructor takes `AuthInterceptor` and `HttpLoggingInterceptor`. Configuration:
   - `connectTimeout(30, TimeUnit.SECONDS)` 
   - `readTimeout(120, TimeUnit.SECONDS)` — long read for SSE streaming
   - `writeTimeout(30, TimeUnit.SECONDS)`
   - `callTimeout(0, TimeUnit.MILLISECONDS)` — no global call timeout for streaming
   - `addInterceptor(authInterceptor)`
   - `addInterceptor(loggingInterceptor)`
   - `retryOnConnectionFailure(true)`
   - `connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))`
</action>
<acceptance_criteria>
- `grep -q 'class HttpClientFactory' app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt`
- `grep -q 'fun create(): OkHttpClient' app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt`
- `grep -q 'connectTimeout.*30' app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt`
- `grep -q 'readTimeout.*120' app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt`
- `grep -q 'class AuthInterceptor' app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt`
- `grep -q ': Interceptor' app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt`
- `grep -q 'apiKeyStore' app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt`
- `grep -q 'Bearer' app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt`
- `grep -q 'tag(Long::class.java)' app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt`
</acceptance_criteria>

### Task 2: SSE Parser (Critical — Handles Fragmented TCP Chunks)
<read_first>
- .planning/research/PITFALLS.md §4.1 (lines 127-135 — "SSE parsing that doesn't handle multi-chunk splits")
- .planning/research/PITFALLS.md §4.2 (lines 137-143 — "[DONE] event handling")
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §4.4 (lines 868-922 for exact SseParser + SseEvent code)
</read_first>
<action>
Create the SSE parsing subsystem. This is the most critical code in Phase 1 — a naive parser will silently drop tokens on mobile connections.

1. **`app/src/main/java/com/warped/data/remote/network/SseEvent.kt`** — Simple data class:
```kotlin
package com.warped.data.remote.network

data class SseEvent(val data: String, val event: String? = null)
```

2. **`app/src/main/java/com/warped/data/remote/network/SseParser.kt`** — Exact code from RESEARCH.md §4.4 lines 877-922. Key design:
   - `private val buffer = StringBuilder()` — accumulates partial lines
   - `fun feed(chunk: String): List<SseEvent>` — receives raw bytes, splits on `\n\n` boundaries only
   - If no double-newline found → buffer accumulates and returns empty list
   - On `\n\n` found → parse the accumulated segment, emit `SseEvent`
   - `private fun parse(raw: String): SseEvent?` — splits on `\n`, extracts `data:` and `event:` lines, skips comment lines (starting with `:`)
   - `fun reset()` — clears buffer for reuse

3. **`app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`** — Exact code from RESEARCH.md §4.5 lines 936-973. Extension function `ResponseBody.asSseFlow(json: Json, onToken: (SseEvent) -> StreamToken): Flow<StreamToken>`:
   - Creates `SseParser` instance
   - Reads lines from `source.readUtf8Line()` in a `while (!source.exhausted())` loop
   - Feeds each line + `"\n"` to parser
   - For each event: checks `[DONE]`, JSON, empty data → emits appropriate `StreamToken`
   - `[DONE]` → `emit(StreamToken.Done)` then `return@flow`
   - `IOException` caught → `emit(StreamToken.Error("Connection lost: ..."))`
   - `finally` block resets parser and closes body
   - Uses `flowOn(Dispatchers.IO)`
</action>
<acceptance_criteria>
- `grep -q 'data class SseEvent' app/src/main/java/com/warped/data/remote/network/SseEvent.kt`
- `grep -q 'class SseParser' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
- `grep -q 'private val buffer' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
- `grep -q 'fun feed(chunk: String): List<SseEvent>' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
- `grep -q 'indexOf.*\\n\\n' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
- `grep -q 'data:' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
- `grep -q 'fun reset()' app/src/main/java/com/warped/data/remote/network/SseParser.kt`
- `grep -q 'fun ResponseBody.asSseFlow' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`
- `grep -q '\[DONE\]' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`
- `grep -q 'readUtf8Line()' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`
- `grep -q 'flowOn(Dispatchers.IO)' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`
- `grep -q 'Connection lost' app/src/main/java/com/warped/data/remote/network/SseExtensions.kt`
</acceptance_criteria>

### Task 3: Retrofit API Interfaces + DTOs
<read_first>
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §4.3, §4.6, §4.7 (lines 770-864 for all API contracts and DTOs)
- app/src/main/java/com/warped/domain/model/ChatMessage.kt (for message structure mapping)
- app/src/main/java/com/warped/domain/model/ChatRequest.kt (for request structure)
</read_first>
<action>
Create all Retrofit API interfaces and their corresponding DTOs. DTO package: `app/src/main/java/com/warped/data/remote/dto/`. API package: `app/src/main/java/com/warped/data/remote/api/`.

1. **`app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt`** — Exact from RESEARCH.md §4.3 lines 772-783:
```kotlin
package com.warped.data.remote.api

import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiModelListResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface OpenAiApi {
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun chatCompletions(
        @Body request: OpenAiChatRequest
    ): Response<ResponseBody>

    @GET("v1/models")
    suspend fun listModels(): Response<OpenAiModelListResponse>
}
```

2. **`app/src/main/java/com/warped/data/remote/api/OllamaApi.kt`** — Exact from RESEARCH.md §4.3 lines 819-829:
```kotlin
package com.warped.data.remote.api

import com.warped.data.remote.dto.OllamaChatRequest
import com.warped.data.remote.dto.OllamaModelListResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface OllamaApi {
    @POST("api/chat")
    @Headers("Content-Type: application/json")
    suspend fun chat(
        @Body request: OllamaChatRequest
    ): Response<ResponseBody>

    @GET("api/tags")
    suspend fun listModels(): Response<OllamaModelListResponse>
}
```

3. **`app/src/main/java/com/warped/data/remote/api/CustomApi.kt`** — Generic Retrofit interface for custom endpoints (OpenAI-compatible):
```kotlin
package com.warped.data.remote.api

import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiModelListResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface CustomApi {
    @POST
    @Headers("Content-Type: application/json")
    suspend fun chatCompletions(
        @Url chatPath: String,
        @Body request: OpenAiChatRequest
    ): Response<ResponseBody>

    @GET
    suspend fun listModels(
        @Url modelsPath: String
    ): Response<OpenAiModelListResponse>
}
```

4. **`app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt`** — Exact from RESEARCH.md §4.3 lines 787-817:
```kotlin
package com.warped.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stop: List<String>? = null
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAiModelListResponse(
    val `object`: String = "list",
    val data: List<OpenAiModelData> = emptyList()
)

@Serializable
data class OpenAiModelData(
    val id: String,
    val `object`: String = "model",
    val ownedBy: String = ""
)
```

5. **`app/src/main/java/com/warped/data/remote/dto/OllamaChatRequest.kt`** — Exact from RESEARCH.md §4.3 lines 832-864:
```kotlin
package com.warped.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = true,
    val options: OllamaOptions? = null
)

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaOptions(
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("num_predict") val numPredict: Int? = null
)

@Serializable
data class OllamaModelListResponse(
    val models: List<OllamaModelData> = emptyList()
)

@Serializable
data class OllamaModelData(
    val name: String,
    @SerialName("modified_at") val modifiedAt: String = "",
    val size: Long = 0
)
```

6. **`app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt`** — Token parsing DTOs (combining OpenAI and Ollama stream response shapes):
```kotlin
package com.warped.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// OpenAI streaming chunk
@Serializable
data class OpenAiStreamChunk(
    val choices: List<OpenAiStreamChoice> = emptyList()
)

@Serializable
data class OpenAiStreamChoice(
    val delta: OpenAiStreamDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OpenAiStreamDelta(
    val content: String? = null,
    val role: String? = null
)

// Ollama streaming chunk
@Serializable
data class OllamaStreamChunk(
    val model: String? = null,
    val message: OllamaStreamMessage? = null,
    val done: Boolean = false
)

@Serializable
data class OllamaStreamMessage(
    val content: String? = null,
    val role: String? = null
)
```
</action>
<acceptance_criteria>
- `grep -q 'interface OpenAiApi' app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt`
- `grep -q 'v1/chat/completions' app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt`
- `grep -q 'v1/models' app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt`
- `grep -q 'interface OllamaApi' app/src/main/java/com/warped/data/remote/api/OllamaApi.kt`
- `grep -q 'api/chat' app/src/main/java/com/warped/data/remote/api/OllamaApi.kt`
- `grep -q 'api/tags' app/src/main/java/com/warped/data/remote/api/OllamaApi.kt`
- `grep -q 'interface CustomApi' app/src/main/java/com/warped/data/remote/api/CustomApi.kt`
- `grep -q '@Serializable.*OpenAiChatRequest' app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt`
- `grep -q 'stream.*true' app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt`
- `grep -q 'max_tokens' app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt`
- `grep -q '@Serializable.*OpenAiModelListResponse' app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt`
- `grep -q '@Serializable.*OllamaChatRequest' app/src/main/java/com/warped/data/remote/dto/OllamaChatRequest.kt`
- `grep -q '@Serializable.*OllamaStreamChunk' app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt`
- `grep -q 'done.*Boolean' app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt`
</acceptance_criteria>

### Task 4: OpenAI Provider Implementation
<read_first>
- app/src/main/java/com/warped/domain/provider/LlmProvider.kt (interface contract)
- app/src/main/java/com/warped/domain/model/ChatRequest.kt
- app/src/main/java/com/warped/domain/model/StreamToken.kt
- app/src/main/java/com/warped/data/remote/network/SseExtensions.kt (asSseFlow)
- app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt
- app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt
- app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt
- app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §4.6 (lines 978-1012 for OpenAI token parsing)
</read_first>
<action>
Create **`app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`** following the exact provider pattern from RESEARCH.md §4.6 and §9.4:

```kotlin
package com.warped.data.remote.provider

import com.warped.data.remote.api.OpenAiApi
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.dto.OpenAiStreamChunk
import com.warped.data.remote.dto.OpenAiStreamDelta
import com.warped.data.remote.network.SseEvent
import com.warped.data.remote.network.asSseFlow
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAIProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : LlmProvider {

    override val type = ProviderType.OPENAI

    private var baseUrl: String = ""
    private var api: OpenAiApi? = null

    fun configure(endpoint: Endpoint): OpenAIProvider {
        baseUrl = endpoint.url.trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(OpenAiApi::class.java)
        return this
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val messages = request.messages.map { msg ->
            OpenAiMessage(role = msg.role.name.lowercase(), content = msg.content)
        }
        val apiRequest = OpenAiChatRequest(
            model = "gpt-3.5-turbo", // placeholder — set by ViewModel via endpoint config
            messages = messages,
            stream = true,
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            maxTokens = request.parameters.maxTokens
        )
        val response = api!!.chatCompletions(apiRequest)
        if (!response.isSuccessful) {
            emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            return@flow
        }
        val body = response.body() ?: run {
            emit(StreamToken.Error("Empty response body"))
            return@flow
        }
        emitAll(body.asSseFlow(json) { event -> parseOpenAiToken(event) })
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api!!.listModels()
            if (response.isSuccessful) {
                val body = response.body()
                val models = body?.data?.map {
                    ModelInfo(id = it.id, name = it.id, providerType = ProviderType.OPENAI)
                } ?: emptyList()
                Result.success(models)
            } else {
                Result.failure(IOException("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            val response = api!!.listModels()
            if (response.isSuccessful) {
                Result.success(ConnectionStatus.Connected)
            } else if (response.code() == 401 || response.code() == 403) {
                Result.success(ConnectionStatus.Disconnected) // auth error = disconnected
            } else {
                Result.success(ConnectionStatus.Disconnected)
            }
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }

    private fun parseOpenAiToken(event: SseEvent): StreamToken {
        return try {
            val chunk = json.decodeFromString<OpenAiStreamChunk>(event.data)
            val content = chunk.choices.firstOrNull()?.delta?.content
            if (content != null && content.isNotEmpty()) {
                StreamToken.Delta(content)
            } else if (chunk.choices.firstOrNull()?.finishReason != null) {
                StreamToken.Done
            } else {
                StreamToken.Delta("")
            }
        } catch (e: Exception) {
            StreamToken.Error("Failed to parse response token")
        }
    }
}
```
</action>
<acceptance_criteria>
- `grep -q 'class OpenAIProvider' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q ': LlmProvider' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'override val type = ProviderType.OPENAI' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'fun configure.*Endpoint' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'override fun chat.*Flow.*StreamToken' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'override suspend fun listModels' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'override suspend fun testConnection' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'asSseFlow' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'parseOpenAiToken' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
- `grep -q 'OpenAiStreamChunk' app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt`
</acceptance_criteria>

### Task 5: Ollama Provider Implementation
<read_first>
- app/src/main/java/com/warped/domain/provider/LlmProvider.kt
- app/src/main/java/com/warped/data/remote/api/OllamaApi.kt
- app/src/main/java/com/warped/data/remote/dto/OllamaChatRequest.kt
- app/src/main/java/com/warped/data/remote/dto/StreamChunks.kt (OllamaStreamChunk)
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §4.7 (lines 1019-1069 for Ollama token parsing and streaming pattern)
</read_first>
<action>
Create **`app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`** following the pattern from RESEARCH.md §4.7. Critical difference from OpenAI: Ollama uses **newline-delimited JSON** (not SSE double-newline), so it needs its own `flow {}` builder reading `readUtf8Line()` line-by-line, NOT `asSseFlow()`.

```kotlin
package com.warped.data.remote.provider

import com.warped.data.remote.api.OllamaApi
import com.warped.data.remote.dto.OllamaChatRequest
import com.warped.data.remote.dto.OllamaMessage
import com.warped.data.remote.dto.OllamaOptions
import com.warped.data.remote.dto.OllamaStreamChunk
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OllamaProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : LlmProvider {

    override val type = ProviderType.OLLAMA

    private var api: OllamaApi? = null

    fun configure(endpoint: Endpoint): OllamaProvider {
        val baseUrl = endpoint.url.trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(OllamaApi::class.java)
        return this
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val messages = request.messages.map { msg ->
            OllamaMessage(role = msg.role.name.lowercase(), content = msg.content)
        }
        val apiRequest = OllamaChatRequest(
            model = "", // Set by ViewModel
            messages = messages,
            stream = true,
            options = OllamaOptions(
                temperature = request.parameters.temperature,
                topP = request.parameters.topP,
                topK = request.parameters.topK,
                numPredict = request.parameters.maxTokens
            )
        )
        val response = api!!.chat(apiRequest)
        if (!response.isSuccessful) {
            emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            return@flow
        }
        val body = response.body() ?: run {
            emit(StreamToken.Error("Empty response body"))
            return@flow
        }
        val source = body.source()
        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val chunk = json.decodeFromString<OllamaStreamChunk>(line)
                if (chunk.done) {
                    emit(StreamToken.Done)
                    return@flow
                }
                val content = chunk.message?.content
                if (!content.isNullOrEmpty()) emit(StreamToken.Delta(content))
            }
        } catch (e: IOException) {
            emit(StreamToken.Error("Connection lost: ${e.message}"))
        } finally {
            body.close()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api!!.listModels()
            if (response.isSuccessful) {
                val body = response.body()
                val models = body?.models?.map {
                    ModelInfo(id = it.name, name = it.name, providerType = ProviderType.OLLAMA)
                } ?: emptyList()
                Result.success(models)
            } else {
                Result.failure(IOException("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            val response = api!!.listModels()
            if (response.isSuccessful) Result.success(ConnectionStatus.Connected)
            else Result.success(ConnectionStatus.Disconnected)
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }
}
```
</action>
<acceptance_criteria>
- `grep -q 'class OllamaProvider' app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`
- `grep -q ': LlmProvider' app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`
- `grep -q 'override val type = ProviderType.OLLAMA' app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`
- `grep -q 'OllamaStreamChunk' app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`
- `grep -q 'source.readUtf8Line()' app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt` — line-by-line JSON parsing
- `grep -q 'chunk.done' app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt`
- `grep -q 'api/tags' app/src/main/java/com/warped/data/remote/api/OllamaApi.kt` — verify API endpoint exists
</acceptance_criteria>

### Task 6: LM Studio + Custom Providers + ProviderRouter
<read_first>
- app/src/main/java/com/warped/domain/provider/LlmProvider.kt
- app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt (LMStudioProvider reuses same protocol)
- app/src/main/java/com/warped/data/remote/api/CustomApi.kt
- app/src/main/java/com/warped/domain/model/Endpoint.kt
- app/src/main/java/com/warped/domain/model/ProviderType.kt
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §4.8 and §9.4 (lines 1072-1077 for LM Studio/Custom details, lines 1857-1918 for ProviderRouter + configure pattern)
</read_first>
<action>
1. **`app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt`** — LM Studio is OpenAI-compatible. Reuses the same `/v1/chat/completions` + SSE protocol. Defaults to `http://localhost:1234`:
```kotlin
package com.warped.data.remote.provider

import com.warped.data.remote.api.OpenAiApi
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.dto.OpenAiStreamChunk
import com.warped.data.remote.network.SseEvent
import com.warped.data.remote.network.asSseFlow
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LMStudioProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : LlmProvider {

    override val type = ProviderType.LM_STUDIO

    private var api: OpenAiApi? = null

    fun configure(endpoint: Endpoint): LMStudioProvider {
        val baseUrl = endpoint.url.trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(OpenAiApi::class.java)
        return this
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val messages = request.messages.map { msg ->
            OpenAiMessage(role = msg.role.name.lowercase(), content = msg.content)
        }
        val apiRequest = OpenAiChatRequest(
            model = "",
            messages = messages,
            stream = true
        )
        val response = api!!.chatCompletions(apiRequest)
        if (!response.isSuccessful) {
            emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            return@flow
        }
        val body = response.body() ?: run {
            emit(StreamToken.Error("Empty response body"))
            return@flow
        }
        emitAll(body.asSseFlow(json) { event ->
            try {
                val chunk = json.decodeFromString<OpenAiStreamChunk>(event.data)
                val content = chunk.choices.firstOrNull()?.delta?.content
                if (content != null && content.isNotEmpty()) StreamToken.Delta(content)
                else if (chunk.choices.firstOrNull()?.finishReason != null) StreamToken.Done
                else StreamToken.Delta("")
            } catch (e: Exception) {
                StreamToken.Error("Failed to parse response token")
            }
        })
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api!!.listModels()
            if (response.isSuccessful) {
                val models = response.body()?.data?.map {
                    ModelInfo(id = it.id, name = it.id, providerType = ProviderType.LM_STUDIO)
                } ?: emptyList()
                Result.success(models)
            } else {
                Result.failure(IOException("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            val response = api!!.listModels()
            if (response.isSuccessful) Result.success(ConnectionStatus.Connected)
            else Result.success(ConnectionStatus.Disconnected)
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }
}
```

2. **`app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt`** — Uses `CustomApi` for user-configured base URLs. OpenAI-compatible protocol with configurable chat path and models path:
```kotlin
package com.warped.data.remote.provider

import com.warped.data.remote.api.CustomApi
import com.warped.data.remote.dto.OpenAiChatRequest
import com.warped.data.remote.dto.OpenAiMessage
import com.warped.data.remote.dto.OpenAiStreamChunk
import com.warped.data.remote.network.SseEvent
import com.warped.data.remote.network.asSseFlow
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ModelInfo
import com.warped.domain.model.ProviderType
import com.warped.domain.model.StreamToken
import com.warped.domain.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : LlmProvider {

    override val type = ProviderType.CUSTOM

    private var baseUrl: String = ""
    private var api: CustomApi? = null

    fun configure(endpoint: Endpoint): CustomProvider {
        baseUrl = endpoint.url.trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(CustomApi::class.java)
        return this
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val messages = request.messages.map { msg ->
            OpenAiMessage(role = msg.role.name.lowercase(), content = msg.content)
        }
        val apiRequest = OpenAiChatRequest(
            model = "",
            messages = messages,
            stream = true
        )
        val response = api!!.chatCompletions("v1/chat/completions", apiRequest)
        if (!response.isSuccessful) {
            emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            return@flow
        }
        val body = response.body() ?: run {
            emit(StreamToken.Error("Empty response body"))
            return@flow
        }
        emitAll(body.asSseFlow(json) { event ->
            try {
                val chunk = json.decodeFromString<OpenAiStreamChunk>(event.data)
                val content = chunk.choices.firstOrNull()?.delta?.content
                if (content != null && content.isNotEmpty()) StreamToken.Delta(content)
                else if (chunk.choices.firstOrNull()?.finishReason != null) StreamToken.Done
                else StreamToken.Delta("")
            } catch (e: Exception) {
                StreamToken.Error("Failed to parse response token")
            }
        })
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): Result<List<ModelInfo>> {
        return try {
            val response = api!!.listModels("v1/models")
            if (response.isSuccessful) {
                val models = response.body()?.data?.map {
                    ModelInfo(id = it.id, name = it.id, providerType = ProviderType.CUSTOM)
                } ?: emptyList()
                Result.success(models)
            } else {
                Result.failure(IOException("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<ConnectionStatus> {
        return try {
            val response = api!!.listModels("v1/models")
            if (response.isSuccessful) Result.success(ConnectionStatus.Connected)
            else Result.success(ConnectionStatus.Disconnected)
        } catch (e: Exception) {
            Result.success(ConnectionStatus.Disconnected)
        }
    }
}
```

3. **`app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`** — Exact from RESEARCH.md §9.4 lines 1858-1874. Maps `ProviderType` → configured `LlmProvider`:
```kotlin
package com.warped.data.remote.provider

import com.warped.domain.model.Endpoint
import com.warped.domain.model.ProviderType
import com.warped.domain.provider.LlmProvider
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ProviderRouter @Inject constructor(
    private val openAIProvider: Provider<OpenAIProvider>,
    private val ollamaProvider: Provider<OllamaProvider>,
    private val lmStudioProvider: Provider<LMStudioProvider>,
    private val customProvider: Provider<CustomProvider>
) {
    fun resolve(endpoint: Endpoint): LlmProvider = when (endpoint.apiType) {
        ProviderType.OPENAI -> openAIProvider.get().configure(endpoint)
        ProviderType.OLLAMA -> ollamaProvider.get().configure(endpoint)
        ProviderType.LM_STUDIO -> lmStudioProvider.get().configure(endpoint)
        ProviderType.CUSTOM -> customProvider.get().configure(endpoint)
        ProviderType.LOCAL -> error("Local provider not available in Phase 1")
    }
}
```
</action>
<acceptance_criteria>
- `grep -q 'class LMStudioProvider' app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt`
- `grep -q ': LlmProvider' app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt`
- `grep -q 'override val type = ProviderType.LM_STUDIO' app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt`
- `grep -q 'class CustomProvider' app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt`
- `grep -q ': LlmProvider' app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt`
- `grep -q 'override val type = ProviderType.CUSTOM' app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt`
- `grep -q 'class ProviderRouter' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
- `grep -q 'fun resolve.*Endpoint.*LlmProvider' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
- `grep -q 'ProviderType.OPENAI ->' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
- `grep -q 'ProviderType.OLLAMA ->' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
- `grep -q 'ProviderType.LM_STUDIO ->' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
- `grep -q 'ProviderType.CUSTOM ->' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
- `grep -q 'ProviderType.LOCAL -> error' app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt`
</acceptance_criteria>
---
plan: 04-ui-chat
wave: 4
depends_on: [02-data-layer, 03-networking-providers]
autonomous: false
requirements_addressed: [PROV-01, PROV-02, PROV-03, CHAT-01, CHAT-02, CHAT-03, CHAT-04, CHAT-05, PERS-01, PERS-02, SEC-01]
files_modified:
  - app/src/main/java/com/warped/ui/navigation/Screen.kt
  - app/src/main/java/com/warped/ui/navigation/NavGraph.kt
  - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt
  - app/src/main/java/com/warped/ui/chat/components/ConversationList.kt
  - app/src/main/java/com/warped/ui/chat/components/StreamingText.kt
  - app/src/main/java/com/warped/ui/endpoints/EndpointsUiState.kt
  - app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt
  - app/src/main/java/com/warped/ui/endpoints/EndpointsScreen.kt
  - app/src/main/java/com/warped/ui/endpoints/components/EndpointCard.kt
  - app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt
  - app/src/main/java/com/warped/ui/models/ModelsPlaceholderScreen.kt
  - app/src/main/java/com/warped/di/DatabaseModule.kt
  - app/src/main/java/com/warped/di/NetworkModule.kt
  - app/src/main/java/com/warped/di/RepositoryModule.kt
  - app/src/main/java/com/warped/di/SecurityModule.kt
  - app/src/main/java/com/warped/di/ProviderModule.kt
  - app/src/main/java/com/warped/WarpedApplication.kt
  - app/src/main/java/com/warped/MainActivity.kt
---

# Plan 04: Navigation Shell, Chat UI, Endpoints UI & Hilt DI Wiring

## Objective
Wire the entire app together: create the bottom-navigation shell with 3 tabs (Chat, Endpoints, Models placeholder), the ChatScreen with streaming token display and stop/cancel, the EndpointsScreen with CRUD and connection testing, and all 5 Hilt DI modules that connect every component from Plans 01-03 into a working app. Also update `WarpedApplication` to use the `RedactingTree` from this plan.

## must_haves
- Bottom nav with Chat/Endpoints/Models tabs, Chat as start destination
- `ChatViewModel.sendMessage()` creates conversation on first message, streams tokens via `ProviderRouter`, saves messages to Room on completion
- `ChatViewModel.stopGeneration()` cancels the generation coroutine and preserves partial response
- ChatScreen shows model selector at top, `LazyColumn` messages in center, `ChatInputBar` pinned at bottom with animated send→stop button
- Token batching at 50ms intervals per PITFALLS.md §3.1 — emits batched deltas to prevent recomposition jank
- EndpointsScreen lets user add/edit/delete endpoints with name, URL, provider type, API key, and test connection
- `EndpointForm` saves API key via `ApiKeyStore` (CharArray, zero-filled after use)
- All 5 Hilt modules compile: `DatabaseModule`, `NetworkModule`, `RepositoryModule`, `SecurityModule`, `ProviderModule`
- `WarpedApplication.kt` plants `RedactingTree` instead of `DebugTree`

## Verification
1. `grep -q 'sealed class Screen' app/src/main/java/com/warped/ui/navigation/Screen.kt`
2. `grep -q 'NavigationBar' app/src/main/java/com/warped/ui/navigation/NavGraph.kt`
3. `grep -q 'fun sendMessage' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
4. `grep -q 'fun stopGeneration' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
5. `grep -q 'LazyColumn' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
6. `grep -q 'ChatInputBar' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
7. `grep -q 'stopGeneration' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
8. `grep -q 'MessageBubble' app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt`
9. `grep -q 'class ChatViewModel' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
10. `grep -q '@HiltViewModel' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
11. `grep -q 'EndpointCard' app/src/main/java/com/warped/ui/endpoints/EndpointsScreen.kt`
12. `grep -q 'apiKeyStore.storeKey' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
13. `grep -q '@Module.*DatabaseModule' app/src/main/java/com/warped/di/DatabaseModule.kt`
14. `grep -q '@Module.*NetworkModule' app/src/main/java/com/warped/di/NetworkModule.kt`
15. `grep -q 'RedactingTree' app/src/main/java/com/warped/WarpedApplication.kt`

## Tasks

### Task 1: Navigation Shell (Screen definitions + NavGraph)
<read_first>
- app/src/main/java/com/warped/MainActivity.kt (will be updated to use NavGraph)
- app/src/main/java/com/warped/ui/theme/Theme.kt (theme to wrap)
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §6 (lines 1240-1310 for exact navigation code)
</read_first>
<action>
1. **`app/src/main/java/com/warped/ui/navigation/Screen.kt`** — Exact from RESEARCH.md §6.1 lines 1244-1251:
```kotlin
package com.warped.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Chat : Screen("chat", "Chat", Icons.Filled.Chat)
    data object Endpoints : Screen("endpoints", "Endpoints", Icons.Filled.Dns)
    data object Models : Screen("models", "Models", Icons.Filled.Memory)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}
```

2. **`app/src/main/java/com/warped/ui/navigation/NavGraph.kt`** — Exact from RESEARCH.md §6.2 lines 1256-1310. Key structure:
   - `Scaffold` with `NavigationBar` bottom bar containing Chat, Endpoints, Models tabs
   - `NavHost` with 4 destinations: Chat, Endpoints, Models (placeholder), Settings (placeholder)
   - `ChatScreen` receives `onNavigateToSettings` callback
   - Navigation uses `popUpTo` with `saveState=true`, `launchSingleTop=true`, `restoreState=true`
   - IMPORTANT: Wrap `Scaffold` in `WarpedTheme` and use `Modifier.padding(innerPadding)` on NavHost

3. **Update `app/src/main/java/com/warped/MainActivity.kt`** — Replace the placeholder `Text("Warped")` with `WarpedNavGraph()`. Keep `@AndroidEntryPoint`, `enableEdgeToEdge()`, `WarpedTheme`.
</action>
<acceptance_criteria>
- `grep -q 'sealed class Screen' app/src/main/java/com/warped/ui/navigation/Screen.kt`
- `grep -q 'data object Chat.*"chat"' app/src/main/java/com/warped/ui/navigation/Screen.kt`
- `grep -q 'data object Endpoints.*"endpoints"' app/src/main/java/com/warped/ui/navigation/Screen.kt`
- `grep -q 'data object Models.*"models"' app/src/main/java/com/warped/ui/navigation/Screen.kt`
- `grep -q 'fun WarpedNavGraph' app/src/main/java/com/warped/ui/navigation/NavGraph.kt`
- `grep -q 'NavigationBar' app/src/main/java/com/warped/ui/navigation/NavGraph.kt`
- `grep -q 'NavHost' app/src/main/java/com/warped/ui/navigation/NavGraph.kt`
- `grep -q 'Scaffold' app/src/main/java/com/warped/ui/navigation/NavGraph.kt`
- `grep -q 'WarpedNavGraph()' app/src/main/java/com/warped/MainActivity.kt`
- `grep -q 'WarpedTheme' app/src/main/java/com/warped/MainActivity.kt`
</acceptance_criteria>

### Task 2: Endpoints UI (Screen + ViewModel + Components)
<read_first>
- app/src/main/java/com/warped/domain/model/Endpoint.kt
- app/src/main/java/com/warped/domain/model/ProviderType.kt
- app/src/main/java/com/warped/domain/repository/EndpointRepository.kt
- app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
- app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt
- .planning/phases/01-foundation-remote-chat/01-CONTEXT.md (D-14 for error messages, "specific ideas" for EndpointsScreen design)
</read_first>
<action>
Create the endpoints management UI. This covers PROV-01 (add endpoint), PROV-02 (edit/delete), PROV-03 (test connection).

1. **`app/src/main/java/com/warped/ui/endpoints/EndpointsUiState.kt`**:
```kotlin
package com.warped.ui.endpoints

import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint

data class EndpointsUiState(
    val endpoints: List<Endpoint> = emptyList(),
    val isLoading: Boolean = false,
    val isFormVisible: Boolean = false,
    val editingEndpoint: Endpoint? = null,
    val formName: String = "",
    val formUrl: String = "",
    val formApiType: String = "OPENAI",
    val formApiKey: String = "",
    val testStatus: Map<Long, ConnectionStatus> = emptyMap(),
    val error: String? = null
)
```

2. **`app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`** — `@HiltViewModel` with:
   - Injects `EndpointRepository`, `ProviderRouter`, `ApiKeyStore`, `SavedStateHandle`
   - `observeEndpoints()` → collects endpoint flow into `_uiState`
   - `showAddForm()` → sets `isFormVisible=true`, `editingEndpoint=null`, clears form fields
   - `showEditForm(endpoint: Endpoint)` → sets form fields from endpoint
   - `saveEndpoint()` → validates name/URL non-empty, calls `endpointRepository.saveEndpoint()`, if API key is non-empty calls `apiKeyStore.storeKey(endpointId, apiKey.toCharArray())`, then hides form
   - `deleteEndpoint(endpointId: Long)` → calls `endpointRepository.deleteEndpoint()` (which also deletes key via repo)
   - `testConnection(endpointId: Long)` → gets endpoint from dao, resolves provider via `ProviderRouter.resolve(endpoint)`, calls `provider.testConnection()`, updates `testStatus` map
   - `activateEndpoint(endpointId: Long)` → calls `endpointRepository.activateEndpoint()`
   - `dismissForm()` → hides form
   - `updateFormField(field, value)` → updates form state fields

3. **`app/src/main/java/com/warped/ui/endpoints/EndpointsScreen.kt`** — Compose screen:
   - Observes `uiState.collectAsStateWithLifecycle()`
   - `Scaffold` with `TopAppBar` ("Endpoints") and FAB ("+" to add)
   - `LazyColumn` of `EndpointCard` items
   - If `isFormVisible`, show `EndpointForm` as a bottom sheet or full-screen dialog
   - Uses `hiltViewModel()`

4. **`app/src/main/java/com/warped/ui/endpoints/components/EndpointCard.kt`** — Card composable showing:
   - Endpoint name, URL, provider type badge (colored chip)
   - "Test" button with loading spinner (shows `ConnectionStatus`)
   - Active indicator (green dot if `isActive`)
   - Edit/Delete icon buttons
   - On click → navigates to edit, or can be configured to activate

5. **`app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt`** — Form composable with:
   - `OutlinedTextField` for name (required)
   - `OutlinedTextField` for URL (required, with keyboard type = Uri)
   - `ExposedDropdownMenuBox` for provider type (OPENAI, OLLAMA, LM_STUDIO, CUSTOM)
   - `OutlinedTextField` for API key (with visibility toggle, `visualTransformation = PasswordVisualTransformation()`)
   - Save and Cancel buttons
   - URL validation: warn if `http://` is used on non-LAN IP

Note: `apiKeyStore.storeKey()` is called in the ViewModel after the endpoint is saved (so we have the endpoint ID). The API key field in the form is a simple String for input; the ViewModel converts it to `CharArray` for storage.
</action>
<acceptance_criteria>
- `grep -q 'data class EndpointsUiState' app/src/main/java/com/warped/ui/endpoints/EndpointsUiState.kt`
- `grep -q 'isFormVisible' app/src/main/java/com/warped/ui/endpoints/EndpointsUiState.kt`
- `grep -q 'testStatus.*ConnectionStatus' app/src/main/java/com/warped/ui/endpoints/EndpointsUiState.kt`
- `grep -q 'class EndpointsViewModel' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q '@HiltViewModel' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q 'ProviderRouter' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q 'ApiKeyStore' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q 'fun testConnection' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q 'fun saveEndpoint' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q 'fun deleteEndpoint' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q 'fun EndpointsScreen' app/src/main/java/com/warped/ui/endpoints/EndpointsScreen.kt`
- `grep -q 'EndpointCard' app/src/main/java/com/warped/ui/endpoints/EndpointsScreen.kt`
- `grep -q 'fun EndpointCard' app/src/main/java/com/warped/ui/endpoints/components/EndpointCard.kt`
- `grep -q 'fun EndpointForm' app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt`
- `grep -q 'PasswordVisualTransformation' app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt` OR `grep -q 'visualTransformation' app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt`
</acceptance_criteria>

### Task 3: Chat UI State + ViewModel
<read_first>
- app/src/main/java/com/warped/domain/model/ChatMessage.kt
- app/src/main/java/com/warped/domain/model/Conversation.kt
- app/src/main/java/com/warped/domain/model/ProviderType.kt
- app/src/main/java/com/warped/domain/model/StreamToken.kt
- app/src/main/java/com/warped/domain/model/ChatRequest.kt
- app/src/main/java/com/warped/domain/model/ConnectionStatus.kt
- app/src/main/java/com/warped/domain/repository/ChatRepository.kt
- app/src/main/java/com/warped/domain/repository/EndpointRepository.kt
- app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §7.1, §7.2, §7.3, §7.4 (lines 1332-1597 for ChatUiState, ChatViewModel, ChatScreen structure, token batching)
- .planning/research/PITFALLS.md §3.1 (token batching), §3.4 (scroll position)
</read_first>
<action>
Create the chat state management and ViewModel. This is the core of the app — it drives streaming token-by-token chat.

1. **`app/src/main/java/com/warped/ui/chat/ChatUiState.kt`** — Exact from RESEARCH.md §7.1 lines 1335-1360:
```kotlin
package com.warped.ui.chat

import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Conversation
import com.warped.domain.model.ProviderType

data class ChatUiState(
    val conversationId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val streamingContent: String = "",
    val selectedProvider: ProviderType? = null,
    val selectedModelId: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
    val error: ChatError? = null,
    val conversations: List<Conversation> = emptyList(),
    val isStreaming: Boolean = false
)

sealed class ChatError {
    data class Network(val message: String) : ChatError()
    data class Server(val code: Int, val message: String) : ChatError()
    data class Auth(val message: String) : ChatError()
    data object ConnectionLost : ChatError()
    data class Unknown(val message: String) : ChatError()
}
```

2. **`app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`** — Based on RESEARCH.md §7.2 lines 1364-1503 with token batching from §7.4 lines 1575-1598. Full `@HiltViewModel` implementation:
   - Inject: `ChatRepository`, `EndpointRepository`, `ProviderRouter`, `SavedStateHandle`
   - `SendMessage(text: String)`:
     - Guard: text not blank, model/provider selected
     - Append user message to `_uiState.messages`
     - Call `ensureConversation()` → creates conversation if first message (via `chatRepository.createConversation()`)
     - Save user message via `chatRepository.saveMessage(conversationId, userMsg)`
     - Get active endpoint → resolve provider via `providerRouter.resolve(endpoint)`
     - Build `ChatRequest(messages, GenerationParameters())`
     - Launch generation coroutine stored in `generationJob`
     - **Token batching**: Accumulate tokens in `tokenBuffer` (list), emit batched to `streamingContent` every 50ms using `System.currentTimeMillis()` comparison. Flush remaining on `Done`.
     - On `StreamToken.Done`: create assistant `ChatMessage`, save via `chatRepository.saveMessage()`, clear streaming content, set `isGenerating=false`
     - On `StreamToken.Error`: set error state, stop generating
   - `stopGeneration()`: Cancel `generationJob`, set `isGenerating=false`, `isStreaming=false`. Partial streaming content is preserved.
   - `selectConversation(conversationId: Long)`: Load conversation+messages via `chatRepository.loadConversation()`, restore UI state
   - `newConversation()`: Clear messages, reset conversationId, preserve provider/model selection
   - `updateInput(text: String)`: Update `inputText`
   - `setSelectedModel(modelId: String)`: Update `selectedModelId`
   - `setSelectedProvider(providerType: ProviderType)`: Update `selectedProvider`
   - `observeConversations()` in init block: Collect `chatRepository.observeConversations()` flow
   - `ensureConversation()`: Private suspend — create conversation if `conversationId` is null, using first message content (truncated 50 chars) as title
</action>
<acceptance_criteria>
- `grep -q 'data class ChatUiState' app/src/main/java/com/warped/ui/chat/ChatUiState.kt`
- `grep -q 'sealed class ChatError' app/src/main/java/com/warped/ui/chat/ChatUiState.kt`
- `grep -q 'Network(val message: String)' app/src/main/java/com/warped/ui/chat/ChatUiState.kt`
- `grep -q 'ConnectionLost' app/src/main/java/com/warped/ui/chat/ChatUiState.kt`
- `grep -q 'class ChatViewModel' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q '@HiltViewModel' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'fun sendMessage' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'fun stopGeneration' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'ProviderRouter' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'ChatRepository' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'fun selectConversation' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'fun newConversation' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'generationJob' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'tokenBuffer\|lastEmitTime\|50' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` — token batching at 50ms
</acceptance_criteria>

### Task 4: ChatScreen + Chat Components
<read_first>
- app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
- app/src/main/java/com/warped/ui/chat/ChatUiState.kt
- app/src/main/java/com/warped/domain/model/ChatMessage.kt
- app/src/main/java/com/warped/domain/model/Role.kt
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §7.3 (lines 1507-1568 for ChatScreen structure)
- .planning/research/PITFALLS.md §3.4 (scroll position during streaming — auto-scroll only if near bottom)
</read_first>
<action>
Create the ChatScreen and all sub-components.

1. **`app/src/main/java/com/warped/ui/chat/ChatScreen.kt`** — Main chat composable following RESEARCH.md §7.3:
   - `viewModel: ChatViewModel = hiltViewModel()`
   - `val uiState by viewModel.uiState.collectAsStateWithLifecycle()`
   - `Scaffold` with:
     - `topBar`: `TopAppBar` with title = ModelSelector dropdown (shows selected model/provider or "Select a model"). Settings gear icon → `onNavigateToSettings`.
     - `bottomBar`: `ChatInputBar` with text, send/stop button, input field
     - `content` area: `LazyColumn` with `rememberLazyListState()`
       - Messages rendered as `MessageBubble` with key = message.id
       - Streaming content rendered as a special `MessageBubble` with key = "streaming" (assistant role, streaming content)
       - Error banner if `uiState.error != null`
       - Conversation list drawer (expandable from top bar hamburger or pull from left)
   - **Scroll behavior** (PITFALLS.md §3.4): In a `LaunchedEffect` keyed on `uiState.streamingContent.length`, if `lazyListState.firstVisibleItemIndex >= lazyListState.layoutInfo.totalItemsCount - 3`, auto-scroll to bottom. Otherwise, show a floating "↓" button.

2. **`app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt`**:
   - Takes `ChatMessage` parameter
   - User messages: right-aligned, colored bubble (using theme UserBubble colors)
   - Assistant messages: left-aligned, surfaceVariant bubble
   - Shows role label ("You" / "Assistant") in caption text
   - Supports markdown rendering via `buildAnnotatedString` (basic: bold, italic, code blocks)
   - Rounded shape with 12dp corners

3. **`app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt`**:
   - `OutlinedTextField` or `TextField` with `Modifier.weight(1f)` and placeholder "Type a message..."
   - Single row: text field + send/stop button
   - When `isGenerating=true` → show Stop button (red square icon), disable text field
   - When `isGenerating=false` → show Send button (arrow icon), enable text field
   - Send on IME action `ImeAction.Send` and on button click
   - Clear input after send

4. **`app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt`**:
   - Dropdown composable showing selected model/provider
   - When expanded, show list of available models from active endpoint
   - Each item shows model name and provider type
   - On model selection, calls `viewModel.setSelectedModel(id)` and `viewModel.setSelectedProvider(type)`
   - Shows "No models loaded" if empty

5. **`app/src/main/java/com/warped/ui/chat/components/ConversationList.kt`**:
   - `ModalDrawerSheet` or side panel showing list of conversations from `uiState.conversations`
   - Each item shows conversation title and timestamp
   - On click → `viewModel.selectConversation(id)`, close drawer
   - "New Chat" button at top → `viewModel.newConversation()`, close drawer
   - Active conversation highlighted

6. **`app/src/main/java/com/warped/ui/chat/components/StreamingText.kt`**:
   - Animated text composable for streaming content
   - Shows a blinking cursor at end during active streaming (`isStreaming=true`)
   - Uses `AnimatedVisibility` for fade-in effect on new tokens

7. **`app/src/main/java/com/warped/ui/models/ModelsPlaceholderScreen.kt`**:
   - Simple composable with centered text "Models coming in Phase 2"
   - Uses Material 3 `Text` with headline style
</action>
<acceptance_criteria>
- `grep -q 'fun ChatScreen' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'hiltViewModel()' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'collectAsStateWithLifecycle()' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'LazyColumn' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'ChatInputBar' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'ModelSelector' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'rememberLazyListState' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'fun MessageBubble' app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt`
- `grep -q 'Role.USER\|Role.ASSISTANT' app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt`
- `grep -q 'fun ChatInputBar' app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt`
- `grep -q 'isGenerating' app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt`
- `grep -q 'fun ModelSelector' app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt`
- `grep -q 'fun ConversationList' app/src/main/java/com/warped/ui/chat/components/ConversationList.kt`
- `grep -q 'fun StreamingText' app/src/main/java/com/warped/ui/chat/components/StreamingText.kt`
- `grep -q 'fun ModelsPlaceholderScreen' app/src/main/java/com/warped/ui/models/ModelsPlaceholderScreen.kt`
- `grep -q 'Phase 2' app/src/main/java/com/warped/ui/models/ModelsPlaceholderScreen.kt`
</acceptance_criteria>

### Task 5: Hilt DI Modules (All 5)
<read_first>
- app/src/main/java/com/warped/data/local/db/AppDatabase.kt
- app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt
- app/src/main/java/com/warped/data/local/db/dao/MessageDao.kt
- app/src/main/java/com/warped/data/local/db/dao/RemoteEndpointDao.kt
- app/src/main/java/com/warped/data/local/security/KeystoreManager.kt
- app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt
- app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt
- app/src/main/java/com/warped/data/repository/EndpointRepositoryImpl.kt
- app/src/main/java/com/warped/data/repository/ModelRepositoryImpl.kt
- app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt
- app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt
- app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt
- app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt
- app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
- app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt
- app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §5 (lines 1082-1236 for all Hilt module code)
</read_first>
<action>
Create all 5 Hilt DI modules in `app/src/main/java/com/warped/di/`. These wire the entire dependency graph.

1. **`app/src/main/java/com/warped/di/DatabaseModule.kt`** — Exact from RESEARCH.md §5.2 lines 1106-1127:
   - `@Module @InstallIn(SingletonComponent::class) object DatabaseModule`
   - `provideDatabase(@ApplicationContext context: Context): AppDatabase` — `Room.databaseBuilder(context, AppDatabase::class.java, "warped.db").fallbackToDestructiveMigration().build()`
   - `provideConversationDao(db)`, `provideMessageDao(db)`, `provideRemoteEndpointDao(db)`

2. **`app/src/main/java/com/warped/di/NetworkModule.kt`** — Exact from RESEARCH.md §5.3 lines 1133-1169:
   - `@Module @InstallIn(SingletonComponent::class) object NetworkModule`
   - `provideLoggingInterceptor(): HttpLoggingInterceptor` — `HEADERS` in debug, `NONE` in release. NEVER `BODY` level (would log tokens per PITFALLS §6.2).
   - `provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor, authInterceptor: AuthInterceptor): OkHttpClient` — 30s connect, 120s read, 30s write, connection pool (5, 1 min), `retryOnConnectionFailure(true)`. Add both interceptors.
   - `provideJson(): Json` — `Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; coerceInputValues = true }`

3. **`app/src/main/java/com/warped/di/RepositoryModule.kt`** — Exact from RESEARCH.md §5.4 lines 1174-1191:
   - `@Module @InstallIn(SingletonComponent::class) abstract class RepositoryModule`
   - `@Binds @Singleton abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository`
   - `@Binds @Singleton abstract fun bindEndpointRepository(impl: EndpointRepositoryImpl): EndpointRepository`
   - `@Binds @Singleton abstract fun bindModelRepository(impl: ModelRepositoryImpl): ModelRepository`

4. **`app/src/main/java/com/warped/di/SecurityModule.kt`** — Exact from RESEARCH.md §5.5 lines 1196-1211:
   - `@Module @InstallIn(SingletonComponent::class) object SecurityModule`
   - `provideKeystoreManager(@ApplicationContext context: Context): KeystoreManager`
   - `provideApiKeyStore(keystoreManager: KeystoreManager): ApiKeyStore`

5. **`app/src/main/java/com/warped/di/ProviderModule.kt`** — Exact from RESEARCH.md §5.6 lines 1217-1236:
   - `@Module @InstallIn(SingletonComponent::class) object ProviderModule`
   - `provideProviderRouter(openAIProvider: Provider<OpenAIProvider>, ollamaProvider: Provider<OllamaProvider>, lmStudioProvider: Provider<LMStudioProvider>, customProvider: Provider<CustomProvider>): ProviderRouter`
   - Uses `javax.inject.Provider` to lazy-load provider instances

6. **Update `app/src/main/java/com/warped/WarpedApplication.kt`** — Replace `Timber.plant(Timber.DebugTree())` with the `RedactingTree` implementation (inline inner class or separate file). The `RedactingTree` from RESEARCH.md §8.3 lines 1711-1724:
   - Extends `Timber.DebugTree()`
   - Overrides `log()` to redact API key patterns (`api[_-]?key|secret|token|authorization`, `Bearer\s+\S+`) with `[REDACTED]`
</action>
<acceptance_criteria>
- `grep -q '@Module.*DatabaseModule' app/src/main/java/com/warped/di/DatabaseModule.kt`
- `grep -q 'InstallIn(SingletonComponent::class)' app/src/main/java/com/warped/di/DatabaseModule.kt`
- `grep -q 'Room.databaseBuilder' app/src/main/java/com/warped/di/DatabaseModule.kt`
- `grep -q 'fallbackToDestructiveMigration()' app/src/main/java/com/warped/di/DatabaseModule.kt`
- `grep -q 'provideConversationDao' app/src/main/java/com/warped/di/DatabaseModule.kt`
- `grep -q '@Module.*NetworkModule' app/src/main/java/com/warped/di/NetworkModule.kt`
- `grep -q 'provideOkHttpClient' app/src/main/java/com/warped/di/NetworkModule.kt`
- `grep -q 'provideJson' app/src/main/java/com/warped/di/NetworkModule.kt`
- `grep -q 'ignoreUnknownKeys = true' app/src/main/java/com/warped/di/NetworkModule.kt`
- `grep -q 'BODY\|HEADERS' app/src/main/java/com/warped/di/NetworkModule.kt` — logging level NOT BODY
- `grep -q '@Module.*RepositoryModule' app/src/main/java/com/warped/di/RepositoryModule.kt`
- `grep -q 'abstract class RepositoryModule' app/src/main/java/com/warped/di/RepositoryModule.kt`
- `grep -q '@Binds.*bindChatRepository' app/src/main/java/com/warped/di/RepositoryModule.kt`
- `grep -q '@Binds.*bindEndpointRepository' app/src/main/java/com/warped/di/RepositoryModule.kt`
- `grep -q '@Binds.*bindModelRepository' app/src/main/java/com/warped/di/RepositoryModule.kt`
- `grep -q '@Module.*SecurityModule' app/src/main/java/com/warped/di/SecurityModule.kt`
- `grep -q 'provideKeystoreManager' app/src/main/java/com/warped/di/SecurityModule.kt`
- `grep -q 'provideApiKeyStore' app/src/main/java/com/warped/di/SecurityModule.kt`
- `grep -q '@Module.*ProviderModule' app/src/main/java/com/warped/di/ProviderModule.kt`
- `grep -q 'provideProviderRouter' app/src/main/java/com/warped/di/ProviderModule.kt`
- `grep -q 'Provider<OpenAIProvider>' app/src/main/java/com/warped/di/ProviderModule.kt`
- `grep -q 'RedactingTree' app/src/main/java/com/warped/WarpedApplication.kt`
- `grep -q 'REDACTED' app/src/main/java/com/warped/WarpedApplication.kt`
</acceptance_criteria>
