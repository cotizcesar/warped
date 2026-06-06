package com.warped.`data`.local.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.warped.`data`.local.db.entity.ConversationEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ConversationDao_Impl(
  __db: RoomDatabase,
) : ConversationDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfConversationEntity: EntityInsertAdapter<ConversationEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfConversationEntity = object : EntityInsertAdapter<ConversationEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `conversations` (`id`,`title`,`created_at`,`updated_at`,`provider_type`,`endpoint_id`,`model_id`,`system_prompt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ConversationEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindLong(3, entity.createdAt)
        statement.bindLong(4, entity.updatedAt)
        statement.bindText(5, entity.providerType)
        statement.bindLong(6, entity.endpointId)
        val _tmpModelId: String? = entity.modelId
        if (_tmpModelId == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpModelId)
        }
        val _tmpSystemPrompt: String? = entity.systemPrompt
        if (_tmpSystemPrompt == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpSystemPrompt)
        }
      }
    }
  }

  public override suspend fun upsert(conversation: ConversationEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfConversationEntity.insertAndReturnId(_connection, conversation)
    _result
  }

  public override fun observeAll(): Flow<List<ConversationEntity>> {
    val _sql: String = "SELECT * FROM conversations ORDER BY updated_at DESC"
    return createFlow(__db, false, arrayOf("conversations")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfProviderType: Int = getColumnIndexOrThrow(_stmt, "provider_type")
        val _columnIndexOfEndpointId: Int = getColumnIndexOrThrow(_stmt, "endpoint_id")
        val _columnIndexOfModelId: Int = getColumnIndexOrThrow(_stmt, "model_id")
        val _columnIndexOfSystemPrompt: Int = getColumnIndexOrThrow(_stmt, "system_prompt")
        val _result: MutableList<ConversationEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ConversationEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpProviderType: String
          _tmpProviderType = _stmt.getText(_columnIndexOfProviderType)
          val _tmpEndpointId: Long
          _tmpEndpointId = _stmt.getLong(_columnIndexOfEndpointId)
          val _tmpModelId: String?
          if (_stmt.isNull(_columnIndexOfModelId)) {
            _tmpModelId = null
          } else {
            _tmpModelId = _stmt.getText(_columnIndexOfModelId)
          }
          val _tmpSystemPrompt: String?
          if (_stmt.isNull(_columnIndexOfSystemPrompt)) {
            _tmpSystemPrompt = null
          } else {
            _tmpSystemPrompt = _stmt.getText(_columnIndexOfSystemPrompt)
          }
          _item = ConversationEntity(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpUpdatedAt,_tmpProviderType,_tmpEndpointId,_tmpModelId,_tmpSystemPrompt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): ConversationEntity? {
    val _sql: String = "SELECT * FROM conversations WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _columnIndexOfProviderType: Int = getColumnIndexOrThrow(_stmt, "provider_type")
        val _columnIndexOfEndpointId: Int = getColumnIndexOrThrow(_stmt, "endpoint_id")
        val _columnIndexOfModelId: Int = getColumnIndexOrThrow(_stmt, "model_id")
        val _columnIndexOfSystemPrompt: Int = getColumnIndexOrThrow(_stmt, "system_prompt")
        val _result: ConversationEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpProviderType: String
          _tmpProviderType = _stmt.getText(_columnIndexOfProviderType)
          val _tmpEndpointId: Long
          _tmpEndpointId = _stmt.getLong(_columnIndexOfEndpointId)
          val _tmpModelId: String?
          if (_stmt.isNull(_columnIndexOfModelId)) {
            _tmpModelId = null
          } else {
            _tmpModelId = _stmt.getText(_columnIndexOfModelId)
          }
          val _tmpSystemPrompt: String?
          if (_stmt.isNull(_columnIndexOfSystemPrompt)) {
            _tmpSystemPrompt = null
          } else {
            _tmpSystemPrompt = _stmt.getText(_columnIndexOfSystemPrompt)
          }
          _result = ConversationEntity(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpUpdatedAt,_tmpProviderType,_tmpEndpointId,_tmpModelId,_tmpSystemPrompt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateTimestamp(id: Long, timestamp: Long) {
    val _sql: String = "UPDATE conversations SET updated_at = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, timestamp)
        _argIndex = 2
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: Long) {
    val _sql: String = "DELETE FROM conversations WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM conversations"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
