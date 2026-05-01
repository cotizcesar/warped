package com.warped.`data`.local.db.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.warped.`data`.local.db.entity.MessageEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class MessageDao_Impl(
  __db: RoomDatabase,
) : MessageDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfMessageEntity: EntityInsertAdapter<MessageEntity>

  private val __updateAdapterOfMessageEntity: EntityDeleteOrUpdateAdapter<MessageEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfMessageEntity = object : EntityInsertAdapter<MessageEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `messages` (`id`,`conversation_id`,`role`,`content`,`token_count`,`created_at`,`images`,`reasoning`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: MessageEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.conversationId)
        statement.bindText(3, entity.role)
        statement.bindText(4, entity.content)
        statement.bindLong(5, entity.tokenCount.toLong())
        statement.bindLong(6, entity.createdAt)
        val _tmpImages: String? = entity.images
        if (_tmpImages == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpImages)
        }
        val _tmpReasoning: String? = entity.reasoning
        if (_tmpReasoning == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpReasoning)
        }
      }
    }
    this.__updateAdapterOfMessageEntity = object : EntityDeleteOrUpdateAdapter<MessageEntity>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `messages` SET `id` = ?,`conversation_id` = ?,`role` = ?,`content` = ?,`token_count` = ?,`created_at` = ?,`images` = ?,`reasoning` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: MessageEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.conversationId)
        statement.bindText(3, entity.role)
        statement.bindText(4, entity.content)
        statement.bindLong(5, entity.tokenCount.toLong())
        statement.bindLong(6, entity.createdAt)
        val _tmpImages: String? = entity.images
        if (_tmpImages == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpImages)
        }
        val _tmpReasoning: String? = entity.reasoning
        if (_tmpReasoning == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpReasoning)
        }
        statement.bindLong(9, entity.id)
      }
    }
  }

  public override suspend fun insert(message: MessageEntity): Long = performSuspending(__db, false,
      true) { _connection ->
    val _result: Long = __insertAdapterOfMessageEntity.insertAndReturnId(_connection, message)
    _result
  }

  public override suspend fun update(message: MessageEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __updateAdapterOfMessageEntity.handle(_connection, message)
  }

  public override fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>> {
    val _sql: String = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at ASC"
    return createFlow(__db, false, arrayOf("messages")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, conversationId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversation_id")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfTokenCount: Int = getColumnIndexOrThrow(_stmt, "token_count")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfImages: Int = getColumnIndexOrThrow(_stmt, "images")
        val _columnIndexOfReasoning: Int = getColumnIndexOrThrow(_stmt, "reasoning")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpConversationId: Long
          _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpTokenCount: Int
          _tmpTokenCount = _stmt.getLong(_columnIndexOfTokenCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpImages: String?
          if (_stmt.isNull(_columnIndexOfImages)) {
            _tmpImages = null
          } else {
            _tmpImages = _stmt.getText(_columnIndexOfImages)
          }
          val _tmpReasoning: String?
          if (_stmt.isNull(_columnIndexOfReasoning)) {
            _tmpReasoning = null
          } else {
            _tmpReasoning = _stmt.getText(_columnIndexOfReasoning)
          }
          _item =
              MessageEntity(_tmpId,_tmpConversationId,_tmpRole,_tmpContent,_tmpTokenCount,_tmpCreatedAt,_tmpImages,_tmpReasoning)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getByConversation(conversationId: Long): List<MessageEntity> {
    val _sql: String = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, conversationId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversation_id")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfTokenCount: Int = getColumnIndexOrThrow(_stmt, "token_count")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfImages: Int = getColumnIndexOrThrow(_stmt, "images")
        val _columnIndexOfReasoning: Int = getColumnIndexOrThrow(_stmt, "reasoning")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpConversationId: Long
          _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpTokenCount: Int
          _tmpTokenCount = _stmt.getLong(_columnIndexOfTokenCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpImages: String?
          if (_stmt.isNull(_columnIndexOfImages)) {
            _tmpImages = null
          } else {
            _tmpImages = _stmt.getText(_columnIndexOfImages)
          }
          val _tmpReasoning: String?
          if (_stmt.isNull(_columnIndexOfReasoning)) {
            _tmpReasoning = null
          } else {
            _tmpReasoning = _stmt.getText(_columnIndexOfReasoning)
          }
          _item =
              MessageEntity(_tmpId,_tmpConversationId,_tmpRole,_tmpContent,_tmpTokenCount,_tmpCreatedAt,_tmpImages,_tmpReasoning)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteByConversation(conversationId: Long) {
    val _sql: String = "DELETE FROM messages WHERE conversation_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, conversationId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM messages"
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
