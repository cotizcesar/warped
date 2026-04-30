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
