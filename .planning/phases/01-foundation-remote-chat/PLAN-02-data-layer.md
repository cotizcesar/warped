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
