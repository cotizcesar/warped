package com.warped.`data`.local.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.warped.`data`.local.db.dao.ConversationDao
import com.warped.`data`.local.db.dao.ConversationDao_Impl
import com.warped.`data`.local.db.dao.DownloadCheckpointDao
import com.warped.`data`.local.db.dao.DownloadCheckpointDao_Impl
import com.warped.`data`.local.db.dao.LocalModelDao
import com.warped.`data`.local.db.dao.LocalModelDao_Impl
import com.warped.`data`.local.db.dao.MessageDao
import com.warped.`data`.local.db.dao.MessageDao_Impl
import com.warped.`data`.local.db.dao.PresetDao
import com.warped.`data`.local.db.dao.PresetDao_Impl
import com.warped.`data`.local.db.dao.RemoteEndpointDao
import com.warped.`data`.local.db.dao.RemoteEndpointDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _conversationDao: Lazy<ConversationDao> = lazy {
    ConversationDao_Impl(this)
  }

  private val _messageDao: Lazy<MessageDao> = lazy {
    MessageDao_Impl(this)
  }

  private val _remoteEndpointDao: Lazy<RemoteEndpointDao> = lazy {
    RemoteEndpointDao_Impl(this)
  }

  private val _localModelDao: Lazy<LocalModelDao> = lazy {
    LocalModelDao_Impl(this)
  }

  private val _presetDao: Lazy<PresetDao> = lazy {
    PresetDao_Impl(this)
  }

  private val _downloadCheckpointDao: Lazy<DownloadCheckpointDao> = lazy {
    DownloadCheckpointDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(9,
        "e41312ed320aba476c8587f8f66fc984", "8f5d39badb5575739c837ef1dffe0e26") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `conversations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `provider_type` TEXT NOT NULL, `endpoint_id` INTEGER NOT NULL, `model_id` TEXT, `system_prompt` TEXT)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `conversation_id` INTEGER NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `token_count` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `images` TEXT, `stats` TEXT, FOREIGN KEY(`conversation_id`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversation_id` ON `messages` (`conversation_id`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `endpoints` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `api_type` TEXT NOT NULL, `model_id` TEXT, `encrypted_api_key_ref` TEXT, `created_at` INTEGER NOT NULL, `is_active` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `local_models` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `file_path` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `quantization` TEXT NOT NULL, `parameter_count` TEXT NOT NULL, `architecture` TEXT NOT NULL, `imported_at` INTEGER NOT NULL, `model_format` TEXT NOT NULL DEFAULT 'GGUF')")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `temperature` REAL NOT NULL, `top_p` REAL NOT NULL, `top_k` INTEGER NOT NULL, `repeat_penalty` REAL NOT NULL, `max_tokens` INTEGER NOT NULL, `context_size` INTEGER NOT NULL, `seed` INTEGER NOT NULL, `threads` INTEGER NOT NULL, `model_format` TEXT NOT NULL, `created_at` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `download_checkpoints` (`model_id` TEXT NOT NULL, `file_name` TEXT NOT NULL, `file_url` TEXT NOT NULL, `total_bytes` INTEGER NOT NULL, `downloaded_bytes` INTEGER NOT NULL, PRIMARY KEY(`model_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'e41312ed320aba476c8587f8f66fc984')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `conversations`")
        connection.execSQL("DROP TABLE IF EXISTS `messages`")
        connection.execSQL("DROP TABLE IF EXISTS `endpoints`")
        connection.execSQL("DROP TABLE IF EXISTS `local_models`")
        connection.execSQL("DROP TABLE IF EXISTS `presets`")
        connection.execSQL("DROP TABLE IF EXISTS `download_checkpoints`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsConversations: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsConversations.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("provider_type", TableInfo.Column("provider_type", "TEXT", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("endpoint_id", TableInfo.Column("endpoint_id", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("model_id", TableInfo.Column("model_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsConversations.put("system_prompt", TableInfo.Column("system_prompt", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysConversations: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesConversations: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoConversations: TableInfo = TableInfo("conversations", _columnsConversations,
            _foreignKeysConversations, _indicesConversations)
        val _existingConversations: TableInfo = read(connection, "conversations")
        if (!_infoConversations.equals(_existingConversations)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |conversations(com.warped.data.local.db.entity.ConversationEntity).
              | Expected:
              |""".trimMargin() + _infoConversations + """
              |
              | Found:
              |""".trimMargin() + _existingConversations)
        }
        val _columnsMessages: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsMessages.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("conversation_id", TableInfo.Column("conversation_id", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("role", TableInfo.Column("role", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("content", TableInfo.Column("content", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("token_count", TableInfo.Column("token_count", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("images", TableInfo.Column("images", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("stats", TableInfo.Column("stats", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysMessages: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysMessages.add(TableInfo.ForeignKey("conversations", "CASCADE", "NO ACTION",
            listOf("conversation_id"), listOf("id")))
        val _indicesMessages: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesMessages.add(TableInfo.Index("index_messages_conversation_id", false,
            listOf("conversation_id"), listOf("ASC")))
        val _infoMessages: TableInfo = TableInfo("messages", _columnsMessages, _foreignKeysMessages,
            _indicesMessages)
        val _existingMessages: TableInfo = read(connection, "messages")
        if (!_infoMessages.equals(_existingMessages)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |messages(com.warped.data.local.db.entity.MessageEntity).
              | Expected:
              |""".trimMargin() + _infoMessages + """
              |
              | Found:
              |""".trimMargin() + _existingMessages)
        }
        val _columnsEndpoints: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsEndpoints.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsEndpoints.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsEndpoints.put("url", TableInfo.Column("url", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsEndpoints.put("api_type", TableInfo.Column("api_type", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsEndpoints.put("model_id", TableInfo.Column("model_id", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsEndpoints.put("encrypted_api_key_ref", TableInfo.Column("encrypted_api_key_ref",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEndpoints.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsEndpoints.put("is_active", TableInfo.Column("is_active", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysEndpoints: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesEndpoints: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoEndpoints: TableInfo = TableInfo("endpoints", _columnsEndpoints,
            _foreignKeysEndpoints, _indicesEndpoints)
        val _existingEndpoints: TableInfo = read(connection, "endpoints")
        if (!_infoEndpoints.equals(_existingEndpoints)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |endpoints(com.warped.data.local.db.entity.RemoteEndpointEntity).
              | Expected:
              |""".trimMargin() + _infoEndpoints + """
              |
              | Found:
              |""".trimMargin() + _existingEndpoints)
        }
        val _columnsLocalModels: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsLocalModels.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocalModels.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocalModels.put("file_path", TableInfo.Column("file_path", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocalModels.put("size_bytes", TableInfo.Column("size_bytes", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLocalModels.put("quantization", TableInfo.Column("quantization", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLocalModels.put("parameter_count", TableInfo.Column("parameter_count", "TEXT", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLocalModels.put("architecture", TableInfo.Column("architecture", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLocalModels.put("imported_at", TableInfo.Column("imported_at", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLocalModels.put("model_format", TableInfo.Column("model_format", "TEXT", true, 0,
            "'GGUF'", TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysLocalModels: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesLocalModels: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoLocalModels: TableInfo = TableInfo("local_models", _columnsLocalModels,
            _foreignKeysLocalModels, _indicesLocalModels)
        val _existingLocalModels: TableInfo = read(connection, "local_models")
        if (!_infoLocalModels.equals(_existingLocalModels)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |local_models(com.warped.data.local.db.entity.LocalModelEntity).
              | Expected:
              |""".trimMargin() + _infoLocalModels + """
              |
              | Found:
              |""".trimMargin() + _existingLocalModels)
        }
        val _columnsPresets: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPresets.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPresets.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPresets.put("temperature", TableInfo.Column("temperature", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPresets.put("top_p", TableInfo.Column("top_p", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPresets.put("top_k", TableInfo.Column("top_k", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPresets.put("repeat_penalty", TableInfo.Column("repeat_penalty", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPresets.put("max_tokens", TableInfo.Column("max_tokens", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPresets.put("context_size", TableInfo.Column("context_size", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPresets.put("seed", TableInfo.Column("seed", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPresets.put("threads", TableInfo.Column("threads", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPresets.put("model_format", TableInfo.Column("model_format", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsPresets.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPresets: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPresets: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPresets: TableInfo = TableInfo("presets", _columnsPresets, _foreignKeysPresets,
            _indicesPresets)
        val _existingPresets: TableInfo = read(connection, "presets")
        if (!_infoPresets.equals(_existingPresets)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |presets(com.warped.data.local.db.entity.PresetEntity).
              | Expected:
              |""".trimMargin() + _infoPresets + """
              |
              | Found:
              |""".trimMargin() + _existingPresets)
        }
        val _columnsDownloadCheckpoints: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDownloadCheckpoints.put("model_id", TableInfo.Column("model_id", "TEXT", true, 1,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadCheckpoints.put("file_name", TableInfo.Column("file_name", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadCheckpoints.put("file_url", TableInfo.Column("file_url", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadCheckpoints.put("total_bytes", TableInfo.Column("total_bytes", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadCheckpoints.put("downloaded_bytes", TableInfo.Column("downloaded_bytes",
            "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDownloadCheckpoints: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesDownloadCheckpoints: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoDownloadCheckpoints: TableInfo = TableInfo("download_checkpoints",
            _columnsDownloadCheckpoints, _foreignKeysDownloadCheckpoints,
            _indicesDownloadCheckpoints)
        val _existingDownloadCheckpoints: TableInfo = read(connection, "download_checkpoints")
        if (!_infoDownloadCheckpoints.equals(_existingDownloadCheckpoints)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |download_checkpoints(com.warped.data.local.db.entity.DownloadCheckpointEntity).
              | Expected:
              |""".trimMargin() + _infoDownloadCheckpoints + """
              |
              | Found:
              |""".trimMargin() + _existingDownloadCheckpoints)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "conversations", "messages",
        "endpoints", "local_models", "presets", "download_checkpoints")
  }

  public override fun clearAllTables() {
    super.performClear(true, "conversations", "messages", "endpoints", "local_models", "presets",
        "download_checkpoints")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(ConversationDao::class, ConversationDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(MessageDao::class, MessageDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(RemoteEndpointDao::class, RemoteEndpointDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(LocalModelDao::class, LocalModelDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PresetDao::class, PresetDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(DownloadCheckpointDao::class,
        DownloadCheckpointDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun conversationDao(): ConversationDao = _conversationDao.value

  public override fun messageDao(): MessageDao = _messageDao.value

  public override fun remoteEndpointDao(): RemoteEndpointDao = _remoteEndpointDao.value

  public override fun localModelDao(): LocalModelDao = _localModelDao.value

  public override fun presetDao(): PresetDao = _presetDao.value

  public override fun downloadCheckpointDao(): DownloadCheckpointDao = _downloadCheckpointDao.value
}
