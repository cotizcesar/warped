# Phase 1 Research: Foundation & Remote Chat

**Date:** 2026-04-30
**Phase:** 01-foundation-remote-chat
**Purpose:** Provide the planner with concrete technical details — dependency coordinates, code patterns, file paths, API contract shapes, and verification criteria.

> **Sources:** PROJECT.md, REQUIREMENTS.md, ROADMAP.md §Phase 1, STACK.md, ARCHITECTURE.md, PITFALLS.md, CONTEXT.md (user decisions D-01 through D-18)

---

## 1. Project Setup & Configuration

### 1.1 Gradle Wrapper

```
File: gradle/wrapper/gradle-wrapper.properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
```

Gradle 8.13 required for AGP 9.0.x (verified: Dagger 2.59 release notes state AGP 9+ requires Gradle 9.1+, but AGP 9.0.x works with Gradle 8.13+ — exact version resolved at init time via `./gradlew wrapper --gradle-version`).

### 1.2 Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
kotlin = "2.1.10"
agp = "9.0.0"
compose-bom = "2025.04.00"
compose-compiler = "2.1.10"  # bundled with Kotlin 2.1.10
hilt = "2.59.2"
hilt-navigation-compose = "1.2.0"
room = "2.7.1"
lifecycle = "2.8.7"
navigation = "2.8.8"
okhttp = "4.12.0"
retrofit = "2.11.1"
kotlinx-serialization = "1.7.3"
kotlinx-serialization-converter = "1.0.0"
coroutines = "1.9.0"
datastore = "1.1.3"
security-crypto = "1.1.0-alpha06"  # latest with Tink integration
timber = "5.0.1"
ksp = "2.1.10-1.0.31"
# Testing
junit5 = "5.11.4"
mockk = "1.13.16"
turbine = "1.1.0"
truth = "1.4.4"
coroutines-test = "1.9.0"

[libraries]
# Compose BOM (governs all Compose versions)
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
# Compose
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
compose-ui-test = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }

# Lifecycle + ViewModel
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }

# Navigation
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt-navigation-compose" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Networking
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }

# Serialization
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

# Coroutines
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# DataStore
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# Security
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "security-crypto" }

# Logging
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }

# Testing
junit5 = { group = "org.junit.jupiter", name = "junit-jupiter", version.ref = "junit5" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines-test" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

[plugins]
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### 1.3 Root `build.gradle.kts`

```kotlin
// File: build.gradle.kts (root)
plugins {
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

### 1.4 `settings.gradle.kts`

```kotlin
// File: settings.gradle.kts
pluginManagement {
    repositories {
        google {
            content { includeGroupByRegex("com\\.android.*") }
            content { includeGroupByRegex("com\\.google.*") }
            content { includeGroupByRegex("androidx.*") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Warped"
include(":app")
```

### 1.5 App module `app/build.gradle.kts`

```kotlin
// File: app/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.warped"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.warped"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // StrictMode enabled in Application.onCreate for debug builds
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose BOM governs all Compose library versions
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Security
    implementation(libs.security.crypto)

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.compose.ui.test)
}
```

### 1.6 AndroidManifest.xml

```xml
<!-- File: app/src/main/AndroidManifest.xml -->
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".WarpedApplication"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="false"
        android:icon="@mipmap/ic_launcher"
        android:label="Warped"
        android:largeHeap="true"
        android:extractNativeLibs="false"
        android:networkSecurityConfig="@xml/network_security_config"
        android:supportsRtl="true"
        android:theme="@style/Theme.Warped">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

Key settings:
- `android:largeHeap="true"` — Phase 2 prep: models need large heap.
- `android:extractNativeLibs="false"` — Phase 2 prep: reduce APK size, libs stay compressed.
- `android:allowBackup="false"` — No cloud backup of encrypted keys or chat history.
- `configChanges` — Prevent activity recreation on rotation (ViewModel survives anyway).

### 1.7 Network Security Config

```xml
<!-- File: app/src/main/res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Production: block cleartext globally -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- Allow cleartext only for LAN IPs (local Ollama/LM Studio) -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">localhost</domain>
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">10.0.0.0</domain>
        <domain includeSubdomains="false">172.16.0.0</domain>
        <domain includeSubdomains="false">192.168.0.0</domain>
    </domain-config>
</network-security-config>
```

Per PITFALLS.md §6.4: allows cleartext only for LAN IPs; shows warning when user enters `http://` URL.

### 1.8 `gradle.properties`

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

---

## 2. Architecture & Package Structure

Single module (`app/`) with Clean Architecture package layout. Domain layer has zero Android dependencies.

### 2.1 Package Tree

```
app/src/main/java/com/warped/
├── WarpedApplication.kt          # @HiltAndroidApp
├── MainActivity.kt               # @AndroidEntryPoint, single activity
│
├── domain/
│   ├── model/                    # Pure Kotlin data classes
│   │   ├── ChatMessage.kt
│   │   ├── ChatRequest.kt
│   │   ├── Conversation.kt
│   │   ├── Role.kt               # enum: SYSTEM, USER, ASSISTANT
│   │   ├── ModelInfo.kt
│   │   ├── ProviderType.kt       # enum: OPENAI, OLLAMA, LM_STUDIO, CUSTOM, LOCAL
│   │   ├── GenerationParameters.kt
│   │   ├── ConnectionStatus.kt
│   │   └── StreamToken.kt        # sealed interface Delta | Done | Error
│   │
│   ├── repository/               # Interfaces (no implementations)
│   │   ├── ChatRepository.kt
│   │   ├── EndpointRepository.kt
│   │   └── ModelRepository.kt
│   │
│   └── provider/                 # Provider contract
│       └── LlmProvider.kt        # interface with chat(), listModels(), testConnection()
│
├── data/
│   ├── local/
│   │   ├── db/
│   │   │   ├── AppDatabase.kt        # @Database with entities, version, DAOs
│   │   │   ├── entity/
│   │   │   │   ├── ConversationEntity.kt
│   │   │   │   ├── MessageEntity.kt
│   │   │   │   └── RemoteEndpointEntity.kt
│   │   │   ├── dao/
│   │   │   │   ├── ConversationDao.kt
│   │   │   │   ├── MessageDao.kt
│   │   │   │   └── RemoteEndpointDao.kt
│   │   │   └── converter/
│   │   │       └── Converters.kt      # Type converters for Role, ProviderType, Instant
│   │   │
│   │   └── security/
│   │       ├── KeystoreManager.kt     # EncryptedSharedPreferences wrapper
│   │       └── ApiKeyStore.kt         # Encrypt/decrypt per endpoint
│   │
│   ├── remote/
│   │   ├── network/
│   │   │   ├── HttpClientFactory.kt   # OkHttpClient builder with interceptors
│   │   │   ├── AuthInterceptor.kt     # Bearer token injection
│   │   │   ├── SseParser.kt          # Manual SSE line accumulator + parser
│   │   │   └── SseEvent.kt           # data class for parsed SSE event
│   │   │
│   │   ├── api/
│   │   │   ├── OpenAiApi.kt          # Retrofit interface: /v1/chat/completions, /v1/models
│   │   │   ├── OllamaApi.kt          # Retrofit interface: /api/chat, /api/tags
│   │   │   └── CustomApi.kt          # Generic Retrofit interface
│   │   │
│   │   ├── dto/                       # Data Transfer Objects (server JSON shapes)
│   │   │   ├── OpenAiChatRequest.kt
│   │   │   ├── OpenAiChatResponse.kt
│   │   │   ├── OpenAiModelListResponse.kt
│   │   │   ├── OllamaChatRequest.kt
│   │   │   └── OllamaChatResponse.kt
│   │   │
│   │   └── provider/
│   │       ├── ProviderRouter.kt      # Maps ProviderType → LlmProvider impl
│   │       ├── OpenAIProvider.kt      # Implements LlmProvider via OpenAiApi
│   │       ├── OllamaProvider.kt      # Implements LlmProvider via OllamaApi
│   │       ├── LMStudioProvider.kt    # Implements LlmProvider (same as OpenAI, defaults localhost)
│   │       └── CustomProvider.kt      # Implements LlmProvider via CustomApi
│   │
│   └── repository/
│       ├── ChatRepositoryImpl.kt
│       ├── EndpointRepositoryImpl.kt
│       └── ModelRepositoryImpl.kt
│
├── ui/
│   ├── navigation/
│   │   ├── NavGraph.kt               # NavHost with 3 destinations
│   │   └── Screen.kt                 # sealed class defining route objects
│   │
│   ├── theme/
│   │   ├── Theme.kt                  # Material 3 theme (light + dark)
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   └── Shape.kt
│   │
│   ├── chat/
│   │   ├── ChatScreen.kt             # Main chat composable
│   │   ├── ChatViewModel.kt          # StateFlow<ChatUiState>
│   │   ├── ChatUiState.kt            # Sealed class: Loading, Active, Error
│   │   ├── components/
│   │   │   ├── MessageBubble.kt      # User/assistant message bubble
│   │   │   ├── ChatInputBar.kt       # Input field + send/stop button
│   │   │   ├── ModelSelector.kt      # Top dropdown to pick model
│   │   │   ├── ConversationList.kt   # History drawer or screen
│   │   │   └── StreamingText.kt      # Animated token-by-token text display
│   │
│   ├── endpoints/
│   │   ├── EndpointsScreen.kt        # List of saved endpoints
│   │   ├── EndpointsViewModel.kt
│   │   ├── EndpointsUiState.kt
│   │   ├── components/
│   │   │   ├── EndpointCard.kt       # Card: name, URL, type badge, test button
│   │   │   ├── EndpointForm.kt       # Add/edit form
│   │   │   └── TestResultDialog.kt   # Connection test result
│   │
│   └── models/                       # Placeholder tab for Phase 2
│       └── ModelsPlaceholderScreen.kt
│
└── di/
    ├── AppModule.kt                  # @Module: Room DB, DataStore, OkHttp
    ├── NetworkModule.kt              # @Module: Retrofit, API interfaces
    ├── DatabaseModule.kt             # @Module: DAOs
    ├── RepositoryModule.kt           # @Module: Binds repository impls
    └── SecurityModule.kt             # @Module: KeystoreManager, ApiKeyStore
```

### 2.2 Layer Dependency Rules

```
ui/     → depends on → domain/  (via ViewModel-aware use cases)
                      → uses Hilt @Inject constructor
data/   → depends on → domain/  (implements domain repository interfaces)
                      → may use Android APIs
domain/ → zero Android imports
        → kotlin stdlib + coroutines only
di/     → depends on → data/, domain/, ui/  (wires everything)
```

---

## 3. Room Database Design

### 3.1 Entities

```kotlin
// File: app/src/main/java/com/warped/data/local/db/entity/ConversationEntity.kt
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,      // epoch millis
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "provider_type") val providerType: String, // "OPENAI", "OLLAMA", etc.
    @ColumnInfo(name = "endpoint_id") val endpointId: Long,
    @ColumnInfo(name = "model_id") val modelId: String?,
    @ColumnInfo(name = "system_prompt") val systemPrompt: String?
)
```

```kotlin
// File: app/src/main/java/com/warped/data/local/db/entity/MessageEntity.kt
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
    @ColumnInfo(name = "role") val role: String,            // "SYSTEM", "USER", "ASSISTANT"
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "token_count") val tokenCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```

```kotlin
// File: app/src/main/java/com/warped/data/local/db/entity/RemoteEndpointEntity.kt
@Entity(tableName = "endpoints")
data class RemoteEndpointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "api_type") val apiType: String,     // "OPENAI", "OLLAMA", "LM_STUDIO", "CUSTOM"
    @ColumnInfo(name = "encrypted_api_key_ref") val encryptedApiKeyRef: String?,  // keystore alias
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false
)
```

### 3.2 DAOs

```kotlin
// File: app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt
@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET updated_at = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: Long, timestamp: Long)

    @Delete
    suspend fun delete(conversation: ConversationEntity)
}
```

```kotlin
// File: app/src/main/java/com/warped/data/local/db/dao/MessageDao.kt
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    suspend fun getByConversation(conversationId: Long): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
```

```kotlin
// File: app/src/main/java/com/warped/data/local/db/dao/RemoteEndpointDao.kt
@Dao
interface RemoteEndpointDao {
    @Query("SELECT * FROM endpoints ORDER BY created_at DESC")
    fun observeAll(): Flow<List<RemoteEndpointEntity>>

    @Query("SELECT * FROM endpoints WHERE id = :id")
    suspend fun getById(id: Long): RemoteEndpointEntity?

    @Query("SELECT * FROM endpoints WHERE is_active = 1 LIMIT 1")
    suspend fun getActive(): RemoteEndpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(endpoint: RemoteEndpointEntity): Long

    @Query("UPDATE endpoints SET is_active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE endpoints SET is_active = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Delete
    suspend fun delete(endpoint: RemoteEndpointEntity)
}
```

### 3.3 Type Converters

```kotlin
// File: app/src/main/java/com/warped/data/local/db/converter/Converters.kt
class Converters {
    // In Phase 1, Role and ProviderType are stored as String in the entity.
    // Mapping between String and domain enums happens in the repository layer.
    // This avoids Room needing to know domain types (keeps domain pure).

    @TypeConverter
    fun fromTimestamp(value: Long?): Long? = value

    @TypeConverter
    fun toTimestamp(value: Long?): Long? = value
}
```

Design note: Domain enums (`Role`, `ProviderType`) are stored as `String` in entities and converted in the repository layer. This keeps `domain/` pure (zero Room annotations) and avoids TypeConverter explosion. Map in repository: `entity.toDomain()` and `domain.toEntity()`.

### 3.4 Database Class

```kotlin
// File: app/src/main/java/com/warped/data/local/db/AppDatabase.kt
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

Schema export location (in build.gradle.kts):
```kotlin
room {
    schemaDirectory("$projectDir/schemas")
}
```

### 3.5 Entity ↔ Domain Mapping Pattern

```kotlin
// In repository implementation:
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
```

---

## 4. Networking Setup

### 4.1 OkHttp Client Configuration

```kotlin
// File: app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt
@Singleton
class HttpClientFactory @Inject constructor(
    private val authInterceptor: AuthInterceptor,
    private val loggingInterceptor: HttpLoggingInterceptor
) {
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)     // Long read for SSE streaming
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)   // No global call timeout (streaming)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))
        .build()

    fun createForEndpoint(apiKey: String?): OkHttpClient {
        // Per-endpoint client with specific auth
        val builder = create().newBuilder()
        if (apiKey != null) {
            builder.addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                chain.proceed(request)
            }
        }
        return builder.build()
    }
}
```

### 4.2 Auth Interceptor

```kotlin
// File: app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt
/**
 * Injects Bearer token from the active endpoint into requests.
 * The API key is resolved from Keystore at request time, not stored in memory long-term.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val apiKeyStore: ApiKeyStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        // Tag the request with endpoint info so we know which key to use
        val endpointId = originalRequest.tag(Long::class.java) ?: return chain.proceed(originalRequest)

        val apiKey = apiKeyStore.getKey(endpointId)
        val request = if (apiKey != null) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
        } else {
            originalRequest
        }
        return chain.proceed(request)
    }
}
```

### 4.3 Retrofit Interfaces (Exact API Shapes)

```kotlin
// File: app/src/main/java/com/warped/data/remote/api/OpenAiApi.kt
interface OpenAiApi {
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun chatCompletions(
        @Body request: OpenAiChatRequest
    ): Response<ResponseBody>  // ResponseBody for manual SSE parsing

    @GET("v1/models")
    suspend fun listModels(): Response<OpenAiModelListResponse>
}
```

```kotlin
// File: app/src/main/java/com/warped/data/remote/dto/OpenAiChatRequest.kt
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
    val role: String,   // "system", "user", "assistant"
    val content: String
)

@Serializable
data class OpenAiModelListResponse(
    val `object`: String,
    val data: List<OpenAiModelData>
)

@Serializable
data class OpenAiModelData(
    val id: String,
    val `object`: String,
    val ownedBy: String = ""
)
```

```kotlin
// File: app/src/main/java/com/warped/data/remote/api/OllamaApi.kt
interface OllamaApi {
    @POST("api/chat")
    @Headers("Content-Type: application/json")
    suspend fun chat(
        @Body request: OllamaChatRequest
    ): Response<ResponseBody>  // Ollama also supports streaming via "stream": true

    @GET("api/tags")
    suspend fun listModels(): Response<OllamaModelListResponse>
}

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
    val models: List<OllamaModelData>
)

@Serializable
data class OllamaModelData(
    val name: String,
    @SerialName("modified_at") val modifiedAt: String,
    val size: Long
)
```

### 4.4 SSE Parser (Critical — per PITFALLS.md §4.1)

```kotlin
// File: app/src/main/java/com/warped/data/remote/network/SseParser.kt
/**
 * Manual SSE parser with line accumulator buffer for handling fragmented TCP packets.
 *
 * Per PITFALLS.md §4.1: A naive line.contains("data:") split will miss tokens
 * spanning chunk boundaries. This parser accumulates until \n\n.
 */
class SseParser {
    private val buffer = StringBuilder()

    /**
     * Feed raw bytes from ResponseBody.source(). Returns list of complete SSE events.
     * Call this in a loop within a flow {} builder.
     */
    fun feed(chunk: String): List<SseEvent> {
        buffer.append(chunk)
        val events = mutableListOf<SseEvent>()

        while (true) {
            val doubleNewlineIndex = buffer.indexOf("\n\n")
            if (doubleNewlineIndex == -1) break

            val raw = buffer.substring(0, doubleNewlineIndex)
            buffer.delete(0, doubleNewlineIndex + 2) // remove parsed + \n\n

            val event = parse(raw)
            if (event != null) events.add(event)
        }
        return events
    }

    private fun parse(raw: String): SseEvent? {
        val lines = raw.split("\n")
        var data: String? = null
        var event: String? = null

        for (line in lines) {
            when {
                line.startsWith("data: ") -> data = line.removePrefix("data: ")
                line.startsWith("data:") -> data = line.removePrefix("data:")
                line.startsWith("event: ") -> event = line.removePrefix("event: ")
                line.startsWith("event:") -> event = line.removePrefix("event:")
                line.startsWith(":") -> continue // SSE comment lines
            }
        }

        return if (data != null) SseEvent(data = data.trim(), event = event) else null
    }

    fun reset() { buffer.clear() }
}

data class SseEvent(val data: String, val event: String? = null)
```

### 4.5 SSE Streaming Flow Builder

```kotlin
// File: app/src/main/java/com/warped/data/remote/network/SseExtensions.kt
/**
 * Extension on ResponseBody to produce a Flow<StreamToken> from SSE stream.
 *
 * CRITICAL (PITFALLS.md §4.2):
 * - Treat "data: [DONE]" as terminal event — close ResponseBody immediately.
 * - Socket timeout for idle streams: 10s read timeout per chunk.
 */
fun ResponseBody.asSseFlow(
    json: Json,
    onToken: (SseEvent) -> StreamToken
): Flow<StreamToken> = flow {
    val parser = SseParser()
    val source = this@asSseFlow.source()

    try {
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            val events = parser.feed(line + "\n")

            for (event in events) {
                when {
                    event.data == "[DONE]" -> {
                        emit(StreamToken.Done)
                        return@flow
                    }
                    event.data.startsWith("{") -> {
                        val token = onToken(event)
                        emit(token)
                    }
                    event.data.trim().isEmpty() -> {
                        // Empty data, skip
                    }
                    else -> {
                        // Non-JSON data, skip or log warning
                    }
                }
            }
        }
    } catch (e: IOException) {
        emit(StreamToken.Error("Connection lost: ${e.message}"))
    } finally {
        parser.reset()
        close()
    }
}.flowOn(Dispatchers.IO)
```

### 4.6 OpenAI Provider — Token Parsing

```kotlin
// File: app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt

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

fun parseOpenAiToken(event: SseEvent, json: Json): StreamToken {
    return try {
        val chunk = json.decodeFromString<OpenAiStreamChunk>(event.data)
        val content = chunk.choices.firstOrNull()?.delta?.content
        if (content != null && content.isNotEmpty()) {
            StreamToken.Delta(content)
        } else if (chunk.choices.firstOrNull()?.finishReason != null) {
            StreamToken.Done
        } else {
            StreamToken.Delta("") // empty delta, skip
        }
    } catch (e: Exception) {
        StreamToken.Error("Failed to parse response token")
    }
}
```

### 4.7 Ollama Provider — Token Parsing

The Ollama streaming response is newline-delimited JSON (each line is a complete JSON object). Not standard SSE. The same SseParser handles it if Ollama doesn't send double-newlines — adapt as needed.

```kotlin
// File: app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt

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

fun parseOllamaToken(event: SseEvent, json: Json): StreamToken {
    return try {
        val chunk = json.decodeFromString<OllamaStreamChunk>(event.data)
        when {
            chunk.done -> StreamToken.Done
            chunk.message?.content != null && chunk.message.content.isNotEmpty() ->
                StreamToken.Delta(chunk.message.content)
            else -> StreamToken.Delta("")
        }
    } catch (e: Exception) {
        StreamToken.Error("Failed to parse Ollama response")
    }
}
```

**Ollama streaming note**: Ollama sends `\n`-delimited JSON (one object per line), NOT double-newline. The SseParser must be adapted or a separate `OllamaStreamParser` used. Pattern:
```kotlin
fun ResponseBody.asOllamaFlow(json: Json): Flow<StreamToken> = flow {
    val source = this@asOllamaFlow.source()
    try {
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank()) continue
            val chunk = json.decodeFromString<OllamaStreamChunk>(line)
            if (chunk.done) {
                emit(StreamToken.Done); return@flow
            }
            val content = chunk.message?.content
            if (!content.isNullOrEmpty()) emit(StreamToken.Delta(content))
        }
    } catch (e: IOException) {
        emit(StreamToken.Error("Connection lost: ${e.message}"))
    } finally { close() }
}.flowOn(Dispatchers.IO)
```

### 4.8 LM Studio & llama.cpp Server

Both are OpenAI-compatible: same `/v1/chat/completions` protocol, same JSON shapes. The `LMStudioProvider` and `CustomProvider` reuse `OpenAiChatRequest` / `OpenAiChatResponse` DTOs but use a user-specified base URL. Default ports:
- LM Studio: `http://localhost:1234`
- Ollama: `http://localhost:11434`
- llama.cpp server: `http://localhost:8080`

---

## 5. DI Configuration (Hilt Modules)

### 5.1 Application Class

```kotlin
// File: app/src/main/java/com/warped/WarpedApplication.kt
@HiltAndroidApp
class WarpedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(RedactingTree())
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
```

### 5.2 Database Module

```kotlin
// File: app/src/main/java/com/warped/di/DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "warped.db")
            .fallbackToDestructiveMigration() // MVP: replace with proper migration in Phase 2+
            .build()

    @Provides
    fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideRemoteEndpointDao(db: AppDatabase): RemoteEndpointDao = db.remoteEndpointDao()
}
```

### 5.3 Network Module

```kotlin
// File: app/src/main/java/com/warped/di/NetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS  // Never BODY (logs tokens)
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }
}
```

### 5.4 Repository Module

```kotlin
// File: app/src/main/java/com/warped/di/RepositoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindEndpointRepository(impl: EndpointRepositoryImpl): EndpointRepository

    @Binds
    @Singleton
    abstract fun bindModelRepository(impl: ModelRepositoryImpl): ModelRepository
}
```

### 5.5 Security Module

```kotlin
// File: app/src/main/java/com/warped/di/SecurityModule.kt
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideKeystoreManager(@ApplicationContext context: Context): KeystoreManager =
        KeystoreManager(context)

    @Provides
    @Singleton
    fun provideApiKeyStore(keystoreManager: KeystoreManager): ApiKeyStore =
        ApiKeyStore(keystoreManager)
}
```

### 5.6 Provider Module (for LlmProvider implementations)

```kotlin
// File: app/src/main/java/com/warped/di/ProviderModule.kt
@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

    @Provides
    @Singleton
    fun provideProviderRouter(
        openAIProvider: Provider<OpenAIProvider>,
        ollamaProvider: Provider<OllamaProvider>,
        lmStudioProvider: Provider<LMStudioProvider>,
        customProvider: Provider<CustomProvider>
    ): ProviderRouter = ProviderRouter(
        openAIProvider = openAIProvider,
        ollamaProvider = ollamaProvider,
        lmStudioProvider = lmStudioProvider,
        customProvider = customProvider
    )
}
```

---

## 6. Navigation (Compose Bottom Nav)

### 6.1 Screen Definitions

```kotlin
// File: app/src/main/java/com/warped/ui/navigation/Screen.kt
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Chat : Screen("chat", "Chat", Icons.Filled.Chat)
    data object Endpoints : Screen("endpoints", "Endpoints", Icons.Filled.Dns)
    data object Models : Screen("models", "Models", Icons.Filled.Memory)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}
```

### 6.2 NavGraph

```kotlin
// File: app/src/main/java/com/warped/ui/navigation/NavGraph.kt
@Composable
fun WarpedNavGraph(
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            NavigationBar {
                listOf(Screen.Chat, Screen.Endpoints, Screen.Models).forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Chat.route) {
                ChatScreen(
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Endpoints.route) {
                EndpointsScreen()
            }
            composable(Screen.Models.route) {
                ModelsPlaceholderScreen()
            }
            composable(Screen.Settings.route) {
                // Settings screen placeholder — expanded in Phase 4/5
                Text("Settings")
            }
        }
    }
}
```

### 6.3 MainActivity

```kotlin
// File: app/src/main/java/com/warped/MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WarpedTheme {
                WarpedNavGraph()
            }
        }
    }
}
```

---

## 7. State Management (ViewModel + StateFlow)

### 7.1 ChatUiState

```kotlin
// File: app/src/main/java/com/warped/ui/chat/ChatUiState.kt
data class ChatUiState(
    val conversationId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val streamingContent: String = "",       // In-progress assistant message
    val selectedProvider: ProviderType? = null,
    val selectedModelId: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
    val error: ChatError? = null,
    val conversations: List<Conversation> = emptyList(),
    val isStreaming: Boolean = false          // True while tokens are arriving
)

sealed class ChatError {
    data class Network(val message: String) : ChatError()
    data class Server(val code: Int, val message: String) : ChatError()
    data class Auth(val message: String) : ChatError()
    data object ConnectionLost : ChatError()
    data class Unknown(val message: String) : ChatError()
}

enum class ConnectionStatus { Unknown, Connected, Connecting, Disconnected }
```

### 7.2 ChatViewModel

```kotlin
// File: app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val endpointRepository: EndpointRepository,
    private val providerRouter: ProviderRouter,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        // Load conversation list
        viewModelScope.launch {
            chatRepository.observeConversations().collect { conversations ->
                _uiState.update { it.copy(conversations = conversations) }
            }
        }
    }

    fun sendMessage(text: String) {
        val currentState = _uiState.value
        if (text.isBlank() || currentState.selectedModelId == null || currentState.selectedProvider == null) return

        viewModelScope.launch {
            // Append user message
            val userMsg = ChatMessage(role = Role.USER, content = text)
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + userMsg,
                    inputText = "",
                    isGenerating = true,
                    streamingContent = "",
                    error = null
                )
            }

            // Persist message (conversation creation if first message)
            val conversationId = ensureConversation()
            chatRepository.saveMessage(conversationId, userMsg)

            // Get provider
            val endpoint = endpointRepository.getActive()
            if (endpoint == null) {
                _uiState.update { it.copy(error = ChatError.Network("No endpoint configured")) }
                return@launch
            }
            val provider = providerRouter.resolve(endpoint)

            // Build request
            val request = ChatRequest(
                messages = _uiState.value.messages,
                parameters = GenerationParameters()
            )

            // Stream
            generationJob = viewModelScope.launch {
                var fullResponse = ""
                provider.chat(request).collect { token ->
                    when (token) {
                        is StreamToken.Delta -> {
                            fullResponse += token.content
                            _uiState.update { it.copy(streamingContent = fullResponse) }
                        }
                        is StreamToken.Done -> {
                            val assistantMsg = ChatMessage(
                                role = Role.ASSISTANT,
                                content = fullResponse
                            )
                            chatRepository.saveMessage(conversationId, assistantMsg)
                            _uiState.update { state ->
                                state.copy(
                                    messages = state.messages + assistantMsg,
                                    streamingContent = "",
                                    isGenerating = false,
                                    isStreaming = false
                                )
                            }
                        }
                        is StreamToken.Error -> {
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    isStreaming = false,
                                    error = ChatError.Network(token.message)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        _uiState.update {
            it.copy(isGenerating = false, isStreaming = false)
        }
    }

    fun selectConversation(conversationId: Long) {
        viewModelScope.launch {
            chatRepository.loadConversation(conversationId)?.let { (conv, msgs) ->
                _uiState.update {
                    it.copy(
                        conversationId = conv.id,
                        messages = msgs,
                        streamingContent = ""
                    )
                }
            }
        }
    }

    fun newConversation() {
        _uiState.update {
            ChatUiState(
                conversations = it.conversations,
                selectedProvider = it.selectedProvider,
                selectedModelId = it.selectedModelId
            )
        }
    }

    private suspend fun ensureConversation(): Long {
        val state = _uiState.value
        return state.conversationId ?: chatRepository.createConversation(
            title = state.messages.firstOrNull()?.content?.take(50) ?: "New Chat",
            providerType = state.selectedProvider!!,
            endpointId = endpointRepository.getActive()!!.id
        ).also {
            _uiState.update { state -> state.copy(conversationId = it) }
        }
    }
}
```

### 7.3 ChatScreen (Structure)

```kotlin
// File: app/src/main/java/com/warped/ui/chat/ChatScreen.kt
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* Model selector dropdown */ },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                text = uiState.inputText,
                isGenerating = uiState.isGenerating,
                onTextChange = { /* update input */ },
                onSend = { viewModel.sendMessage(it) },
                onStop = { viewModel.stopGeneration() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = rememberLazyListState()
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }
            // Streaming content
            if (uiState.streamingContent.isNotEmpty()) {
                item(key = "streaming") {
                    MessageBubble(
                        message = ChatMessage(
                            id = "streaming",
                            role = Role.ASSISTANT,
                            content = uiState.streamingContent,
                            createdAt = Instant.now()
                        )
                    )
                }
            }
            // Error display
            if (uiState.error != null) {
                item(key = "error") {
                    ErrorBanner(error = uiState.error)
                }
            }
        }
    }
}
```

### 7.4 Token Batching Strategy (per PITFALLS.md §3.1)

For every token, we update `streamingContent` in the StateFlow. This triggers recomposition per token. To mitigate jank:

```kotlin
// In ViewModel, buffer tokens at 30-60ms intervals:
private val tokenBuffer = mutableListOf<String>()
private var lastEmitTime = 0L

// Inside stream collector:
is StreamToken.Delta -> {
    tokenBuffer.add(token.content)
    val now = System.currentTimeMillis()
    if (now - lastEmitTime > 50) { // 50ms batch interval
        val batch = tokenBuffer.joinToString("")
        tokenBuffer.clear()
        fullResponse += batch
        _uiState.update { it.copy(streamingContent = fullResponse, isStreaming = true) }
        lastEmitTime = now
    }
}

// On Done, flush remaining:
tokenBuffer.joinToString("").let { remaining ->
    fullResponse += remaining
    tokenBuffer.clear()
}
```

For the `LazyListState` scroll behavior (PITFALLS.md §3.4): auto-scroll only if user is within 3 items of bottom.

---

## 8. Security (Keystore Integration)

### 8.1 KeystoreManager

```kotlin
// File: app/src/main/java/com/warped/data/local/security/KeystoreManager.kt
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

### 8.2 ApiKeyStore

```kotlin
// File: app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt
@Singleton
class ApiKeyStore @Inject constructor(
    private val keystoreManager: KeystoreManager
) {
    /**
     * Store an API key for an endpoint.
     * Key is never stored in Room or plaintext DataStore.
     * Room stores only the alias reference: "api_key_{endpointId}"
     */
    fun storeKey(endpointId: Long, apiKey: CharArray) {
        val alias = "api_key_$endpointId"
        keystoreManager.put(alias, String(apiKey))
        apiKey.fill('0') // Zero out after use
    }

    /**
     * Retrieve key. Caller should zero the returned CharArray after use.
     */
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

Inline key usage pattern (zero after use):
```kotlin
fun useApiKey(endpointId: Long, block: (String) -> Unit) {
    val keyChars = apiKeyStore.getKey(endpointId) ?: return
    try {
        block(String(keyChars))
    } finally {
        keyChars.fill('0') // Zero-fill per PITFALLS.md §6.3
    }
}
```

### 8.3 Redacting Timber Tree

```kotlin
// File: app/src/main/java/com/warped/WarpedApplication.kt (inner)
class RedactingTree : Timber.DebugTree() {
    private val keyPattern = Regex("""(api[_-]?key|secret|token|authorization)[=:]\s*\S+""", RegexOption.IGNORE_CASE)
    private val bearerPattern = Regex("""Bearer\s+\S+""")

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val redacted = message
            .replace(keyPattern) { "${it.groupValues[1]}=[REDACTED]" }
            .replace(bearerPattern) { "Bearer [REDACTED]" }
        super.log(priority, tag, redacted, t)
    }
}
```

---

## 9. Domain Model Contracts

### 9.1 Core Domain Types

```kotlin
// File: app/src/main/java/com/warped/domain/model/Role.kt
enum class Role { SYSTEM, USER, ASSISTANT }

// File: app/src/main/java/com/warped/domain/model/ProviderType.kt
enum class ProviderType { OPENAI, OLLAMA, LM_STUDIO, CUSTOM, LOCAL }

// File: app/src/main/java/com/warped/domain/model/ChatMessage.kt
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val tokenCount: Int = 0,
    val createdAt: Instant = Instant.now()
)

// File: app/src/main/java/com/warped/domain/model/Conversation.kt
data class Conversation(
    val id: Long,
    val title: String,
    val providerType: ProviderType,
    val endpointId: Long,
    val modelId: String?,
    val systemPrompt: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

// File: app/src/main/java/com/warped/domain/model/ChatRequest.kt
data class ChatRequest(
    val messages: List<ChatMessage>,
    val parameters: GenerationParameters
)

// File: app/src/main/java/com/warped/domain/model/GenerationParameters.kt
data class GenerationParameters(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 2048,
    val contextSize: Int = 4096,
    val seed: Int = -1,
    val threads: Int = 4
)

// File: app/src/main/java/com/warped/domain/model/ModelInfo.kt
data class ModelInfo(
    val id: String,
    val name: String,
    val providerType: ProviderType
)

// File: app/src/main/java/com/warped/domain/model/ConnectionStatus.kt
enum class ConnectionStatus {
    Unknown, Connected, Connecting, Disconnected
}

// File: app/src/main/java/com/warped/domain/model/StreamToken.kt
sealed interface StreamToken {
    data class Delta(val content: String) : StreamToken
    data object Done : StreamToken
    data class Error(val message: String) : StreamToken
}

// File: app/src/main/java/com/warped/domain/model/Endpoint.kt
data class Endpoint(
    val id: Long = 0,
    val name: String,
    val url: String,
    val apiType: ProviderType,
    val isActive: Boolean = false,
    val createdAt: Instant = Instant.now()
)
```

### 9.2 LlmProvider Interface

```kotlin
// File: app/src/main/java/com/warped/domain/provider/LlmProvider.kt
interface LlmProvider {
    val type: ProviderType

    /** Stream a chat completion. Emits tokens one-by-one via Flow. */
    fun chat(request: ChatRequest): Flow<StreamToken>

    /** List available models from this provider. */
    suspend fun listModels(): Result<List<ModelInfo>>

    /** Test connectivity to the provider. */
    suspend fun testConnection(): Result<ConnectionStatus>
}
```

### 9.3 Repository Interfaces

```kotlin
// File: app/src/main/java/com/warped/domain/repository/ChatRepository.kt
interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>
    suspend fun loadConversation(conversationId: Long): Pair<Conversation, List<ChatMessage>>?
    suspend fun createConversation(title: String, providerType: ProviderType, endpointId: Long): Long
    suspend fun saveMessage(conversationId: Long, message: ChatMessage)
    suspend fun updateConversationTitle(conversationId: Long, title: String)
    suspend fun deleteConversation(conversationId: Long)
    suspend fun deleteAllConversations()
}

// File: app/src/main/java/com/warped/domain/repository/EndpointRepository.kt
interface EndpointRepository {
    fun observeEndpoints(): Flow<List<Endpoint>>
    suspend fun getActive(): Endpoint?
    suspend fun saveEndpoint(endpoint: Endpoint)
    suspend fun deleteEndpoint(endpointId: Long)
    suspend fun activateEndpoint(endpointId: Long)
}

// File: app/src/main/java/com/warped/domain/repository/ModelRepository.kt
interface ModelRepository {
    fun observeModels(): Flow<List<ModelInfo>>
    suspend fun refreshModels(endpointId: Long)
}
```

### 9.4 ProviderRouter

```kotlin
// File: app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
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

Provider configuration pattern (each provider has a `configure(endpoint)` method that sets base URL + auth):

```kotlin
class OpenAIProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : LlmProvider {
    override val type = ProviderType.OPENAI

    private var baseUrl: String = ""
    private var apiKey: CharArray? = null
    private var retrofit: Retrofit? = null
    private var api: OpenAiApi? = null

    fun configure(endpoint: Endpoint): OpenAIProvider {
        baseUrl = endpoint.url.trimEnd('/')
        // apiKey resolved from Keystore at request time via AuthInterceptor
        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl + "/")
            .client(okHttpClient)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit!!.create(OpenAiApi::class.java)
        return this
    }

    override fun chat(request: ChatRequest): Flow<StreamToken> = flow {
        val response = api!!.chatCompletions(request.toOpenAiDto())
        if (!response.isSuccessful) {
            emit(StreamToken.Error("HTTP ${response.code()}: ${response.message()}"))
            return@flow
        }
        val body = response.body() ?: run {
            emit(StreamToken.Error("Empty response body"))
            return@flow
        }
        // Delegate to SSE flow parser
        emitAll(body.asSseFlow(json) { event -> parseOpenAiToken(event, json) })
    }.flowOn(Dispatchers.IO)

    // ... listModels, testConnection
}
```

---

## 10. Build & Test Strategy

### 10.1 Build Verification Criteria

| # | Check | Command/Tool | Pass Condition |
|---|-------|-------------|----------------|
| 1 | Gradle sync | `./gradlew --refresh-dependencies` | No dependency resolution errors |
| 2 | Compile debug | `./gradlew assembleDebug` | BUILD SUCCESSFUL |
| 3 | Lint | `./gradlew lint` | No errors (warnings ok for MVP) |
| 4 | Unit tests | `./gradlew test` | All pass |
| 5 | KSP annotation processing | Compile succeeds | No KSP errors for Room, Hilt, Serialization |
| 6 | Hilt graph validation | App launches without `MissingBinding` crash | DI graph complete |

### 10.2 Test Architecture

**Unit tests** (`src/test/` — JVM, no emulator):
- Domain model tests: `ChatMessage`, `ChatRequest`, serialization round-trip
- SseParser tests: chunked input, fragmented lines, `[DONE]` detection, invalid JSON
- ViewModel tests: `ChatViewModel` with mocked repository/provider

**Instrumented tests** (`src/androidTest/` — emulator/device):
- Room DAO tests (in-memory database)
- OkHttp + MockWebServer: SSE streaming end-to-end
- Compose UI tests: ChatScreen rendering, navigation

### 10.3 Key Test Scenarios (Phase 1)

```kotlin
// Example: SseParser fragmented chunk test
@Test
fun `sseParser handles token split across two chunks`() {
    val parser = SseParser()
    val events1 = parser.feed("data: {\"choices\":[{\"de")
    assertThat(events1).isEmpty() // Incomplete — no \n\n

    val events2 = parser.feed("lta\":{\"content\":\"Hello\"}}]}\n\n")
    assertThat(events2).hasSize(1)
    assertThat(events2[0].data).contains("Hello")
}

// Example: Room DAO test
@Test
fun `conversationDao inserts and retrieves by id`() = runTest {
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val dao = db.conversationDao()
    val entity = ConversationEntity(title = "Test", createdAt = 0, updatedAt = 0, providerType = "OPENAI", endpointId = 1)
    val id = dao.upsert(entity)
    val retrieved = dao.getById(id)
    assertThat(retrieved).isNotNull()
    assertThat(retrieved!!.title).isEqualTo("Test")
    db.close()
}

// Example: ChatViewModel sendMessage test
@Test
fun `sendMessage appends user message and starts streaming`() = runTest {
    val mockRepo = mockk<ChatRepository>(relaxed = true)
    val mockEndpointRepo = mockk<EndpointRepository>(relaxed = true)
    val mockRouter = mockk<ProviderRouter>(relaxed = true)
    val mockProvider = mockk<LlmProvider>(relaxed = true)

    coEvery { mockEndpointRepo.getActive() } returns Endpoint(id = 1, name = "Test", url = "http://test", apiType = ProviderType.OPENAI)
    every { mockRouter.resolve(any()) } returns mockProvider
    every { mockProvider.chat(any()) } returns flowOf(StreamToken.Delta("Hi"), StreamToken.Done)
    coEvery { mockRepo.createConversation(any(), any(), any()) } returns 1L

    val vm = ChatViewModel(mockRepo, mockEndpointRepo, mockRouter, SavedStateHandle())
    vm.sendMessage("Hello")

    vm.uiState.test {
        val state = awaitItem()
        assertThat(state.messages).hasSize(1)
        assertThat(state.messages[0].content).isEqualTo("Hello")
        cancelAndConsumeRemainingEvents()
    }
}
```

### 10.4 GPT Instructions for Test Generation

```
When generating tests for Phase 1, create:
1. `app/src/test/java/com/warped/domain/` — pure Kotlin unit tests (JVM)
2. `app/src/test/java/com/warped/data/remote/network/SseParserTest.kt`
3. `app/src/test/java/com/warped/ui/chat/ChatViewModelTest.kt`
4. `app/src/androidTest/java/com/warped/data/local/db/` — Room DAO instrumented tests
5. `app/src/androidTest/java/com/warped/data/remote/` — MockWebServer SSE tests
6. `app/src/androidTest/java/com/warped/ui/` — Compose UI tests

Use: Turbine for Flow testing, MockK for mocking, Room.inMemoryDatabaseBuilder for DAOs,
MockWebServer for Retrofit, ComposeTestRule for UI.
```

---

## 11. References

### Official Documentation (exact shapes)

| Topic | URL | Key API |
|-------|-----|---------|
| OpenAI Chat Completions | https://platform.openai.com/docs/api-reference/chat/create | `POST /v1/chat/completions`, `stream: true`, SSE format |
| OpenAI List Models | https://platform.openai.com/docs/api-reference/models/list | `GET /v1/models` → `{ "object": "list", "data": [...] }` |
| Ollama API Chat | https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion | `POST /api/chat`, newline-delimited JSON stream |
| Ollama API List | https://github.com/ollama/ollama/blob/main/docs/api.md#list-local-models | `GET /api/tags` → `{ "models": [...] }` |
| LM Studio Server | https://lmstudio.ai/docs/local-server | OpenAI-compatible at `localhost:1234` |
| llama.cpp server | https://github.com/ggerganov/llama.cpp/tree/master/examples/server | OpenAI-compatible at `localhost:8080` |
| Room Entities | https://developer.android.com/training/data-storage/room/defining-data | `@Entity`, `@PrimaryKey`, `@ForeignKey`, indices |
| Room DAOs | https://developer.android.com/training/data-storage/room/accessing-data | `@Query`, `@Insert`, `@Delete`, `Flow<List<T>>` |
| Hilt Setup | https://developer.android.com/training/dependency-injection/hilt-android | `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel` |
| Hilt Navigation | https://developer.android.com/training/dependency-injection/hilt-jetpack#navigation | `hiltNavigationCompose()` |
| EncryptedSharedPreferences | https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences | `MasterKey`, AES-256-GCM, Keystore-backed |
| Compose Navigation | https://developer.android.com/develop/ui/compose/navigation | `NavHost`, `composable()`, `NavigationBar` |
| collectAsStateWithLifecycle | https://developer.android.com/reference/androidx/lifecycle/compose/package-summary#collectAsStateWithLifecycle | Flow → Compose State with lifecycle awareness |
| SSE Spec | https://html.spec.whatwg.org/multipage/server-sent-events.html | `data:`, `event:`, double-newline termination |
| Network Security Config | https://developer.android.com/privacy-and-security/security-config | cleartext per domain, LAN IPs |
| Kotlinx Serialization | https://github.com/Kotlin/kotlinx.serialization | `@Serializable`, `@SerialName`, `Json { ignoreUnknownKeys = true }` |
| OkHttp Events/Sources | https://square.github.io/okhttp/features/events/ | `ResponseBody.source()`, `BufferedSource.readUtf8Line()` |
| Compose BOM | https://developer.android.com/jetpack/compose/bom/bom-mapping | Version-controlled Compose library set |
| AGP Compatibility | https://developer.android.com/build/releases/gradle-plugin#compatibility | Gradle ↔ AGP version matrix |
| KSP Releases | https://github.com/google/ksp/releases | Kotlin ↔ KSP version mapping |

### Project-Internal References

| Document | Path | What It Provides |
|----------|------|-----------------|
| Phase Context | `.planning/phases/01-foundation-remote-chat/01-CONTEXT.md` | All implementation decisions (D-01 through D-18) |
| Requirements | `.planning/REQUIREMENTS.md` | PROV-01..05, CHAT-01..05, PERS-01-02, SEC-01 |
| Roadmap Phase 1 | `.planning/ROADMAP.md` | Phase goal, success criteria |
| Stack Decisions | `.planning/research/STACK.md` | Exact versions, dependency coordinates, rationale |
| Architecture | `.planning/research/ARCHITECTURE.md` | Layer diagrams, data flow, component boundaries |
| Pitfalls | `.planning/research/PITFALLS.md` | SSE parsing (P2/§4.1), API key storage (P5/§6.1), token batching (P1/§3.1) |

### Pitfalls Cross-Reference (Phase 1 relevant)

| PITFALLS Ref | Topic | Phase 1 Relevance |
|-------------|-------|------------------|
| §3.1 | Token-by-token recomposition jank | **HIGH** — Must batch tokens at 30-60ms intervals |
| §3.4 | Scroll position during streaming | **HIGH** — Auto-scroll only near bottom, else show "↓" button |
| §3.3 | Rapid re-generation spam | **MEDIUM** — Cancel previous Job before starting new generation |
| §4.1 | SSE chunk fragmentation | **CRITICAL** — Use line accumulator, test with throttled connections |
| §4.2 | `[DONE]` event | **CRITICAL** — Close ResponseBody immediately on `[DONE]` |
| §4.3 | Retry storms | **MEDIUM** — Cap auto-retries at 1, never retry 4xx |
| §4.4 | Self-signed certs for LAN | **LOW** — User opt-in trust toggle for LAN endpoints |
| §4.5 | Per-endpoint health check | **HIGH** — Lightweight HEAD/GET on add/edit/launch |
| §6.1 | Plaintext API key storage | **CRITICAL** — EncryptedSharedPreferences only, zero-fill after use |
| §6.2 | Prompt/conversation logging | **HIGH** — RedactingTree, no message content in logs |
| §6.4 | Cleartext HTTP on LAN | **HIGH** — network_security_config.xml with LAN exceptions |
| §7.1 | Process death | **MEDIUM** — SavedStateHandle, persist to Room after each response |
| §7.6 | Large file OOM | **N/A** Phase 1 — no GGUF handling yet |

---

## RESEARCH COMPLETE

*Document prepared for the planning agent. All dependency coordinates, code patterns, file paths, API contract shapes, and verification criteria are specified. The planner should be able to produce a detailed PLAN.md directly from this research.*
