package com.warped.`data`.local.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.warped.`data`.local.db.entity.RemoteEndpointEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class RemoteEndpointDao_Impl(
  __db: RoomDatabase,
) : RemoteEndpointDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfRemoteEndpointEntity: EntityInsertAdapter<RemoteEndpointEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfRemoteEndpointEntity = object :
        EntityInsertAdapter<RemoteEndpointEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `endpoints` (`id`,`name`,`url`,`api_type`,`model_id`,`encrypted_api_key_ref`,`created_at`,`is_active`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: RemoteEndpointEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.url)
        statement.bindText(4, entity.apiType)
        val _tmpModelId: String? = entity.modelId
        if (_tmpModelId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpModelId)
        }
        val _tmpEncryptedApiKeyRef: String? = entity.encryptedApiKeyRef
        if (_tmpEncryptedApiKeyRef == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpEncryptedApiKeyRef)
        }
        statement.bindLong(7, entity.createdAt)
        val _tmp: Int = if (entity.isActive) 1 else 0
        statement.bindLong(8, _tmp.toLong())
      }
    }
  }

  public override suspend fun upsert(endpoint: RemoteEndpointEntity): Long = performSuspending(__db,
      false, true) { _connection ->
    val _result: Long = __insertAdapterOfRemoteEndpointEntity.insertAndReturnId(_connection,
        endpoint)
    _result
  }

  public override fun observeAll(): Flow<List<RemoteEndpointEntity>> {
    val _sql: String = "SELECT * FROM endpoints ORDER BY created_at DESC"
    return createFlow(__db, false, arrayOf("endpoints")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfUrl: Int = getColumnIndexOrThrow(_stmt, "url")
        val _columnIndexOfApiType: Int = getColumnIndexOrThrow(_stmt, "api_type")
        val _columnIndexOfModelId: Int = getColumnIndexOrThrow(_stmt, "model_id")
        val _columnIndexOfEncryptedApiKeyRef: Int = getColumnIndexOrThrow(_stmt,
            "encrypted_api_key_ref")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "is_active")
        val _result: MutableList<RemoteEndpointEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: RemoteEndpointEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpUrl: String
          _tmpUrl = _stmt.getText(_columnIndexOfUrl)
          val _tmpApiType: String
          _tmpApiType = _stmt.getText(_columnIndexOfApiType)
          val _tmpModelId: String?
          if (_stmt.isNull(_columnIndexOfModelId)) {
            _tmpModelId = null
          } else {
            _tmpModelId = _stmt.getText(_columnIndexOfModelId)
          }
          val _tmpEncryptedApiKeyRef: String?
          if (_stmt.isNull(_columnIndexOfEncryptedApiKeyRef)) {
            _tmpEncryptedApiKeyRef = null
          } else {
            _tmpEncryptedApiKeyRef = _stmt.getText(_columnIndexOfEncryptedApiKeyRef)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpIsActive: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIsActive = _tmp != 0
          _item =
              RemoteEndpointEntity(_tmpId,_tmpName,_tmpUrl,_tmpApiType,_tmpModelId,_tmpEncryptedApiKeyRef,_tmpCreatedAt,_tmpIsActive)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): RemoteEndpointEntity? {
    val _sql: String = "SELECT * FROM endpoints WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfUrl: Int = getColumnIndexOrThrow(_stmt, "url")
        val _columnIndexOfApiType: Int = getColumnIndexOrThrow(_stmt, "api_type")
        val _columnIndexOfModelId: Int = getColumnIndexOrThrow(_stmt, "model_id")
        val _columnIndexOfEncryptedApiKeyRef: Int = getColumnIndexOrThrow(_stmt,
            "encrypted_api_key_ref")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "is_active")
        val _result: RemoteEndpointEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpUrl: String
          _tmpUrl = _stmt.getText(_columnIndexOfUrl)
          val _tmpApiType: String
          _tmpApiType = _stmt.getText(_columnIndexOfApiType)
          val _tmpModelId: String?
          if (_stmt.isNull(_columnIndexOfModelId)) {
            _tmpModelId = null
          } else {
            _tmpModelId = _stmt.getText(_columnIndexOfModelId)
          }
          val _tmpEncryptedApiKeyRef: String?
          if (_stmt.isNull(_columnIndexOfEncryptedApiKeyRef)) {
            _tmpEncryptedApiKeyRef = null
          } else {
            _tmpEncryptedApiKeyRef = _stmt.getText(_columnIndexOfEncryptedApiKeyRef)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpIsActive: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIsActive = _tmp != 0
          _result =
              RemoteEndpointEntity(_tmpId,_tmpName,_tmpUrl,_tmpApiType,_tmpModelId,_tmpEncryptedApiKeyRef,_tmpCreatedAt,_tmpIsActive)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getActive(): RemoteEndpointEntity? {
    val _sql: String = "SELECT * FROM endpoints WHERE is_active = 1 LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfUrl: Int = getColumnIndexOrThrow(_stmt, "url")
        val _columnIndexOfApiType: Int = getColumnIndexOrThrow(_stmt, "api_type")
        val _columnIndexOfModelId: Int = getColumnIndexOrThrow(_stmt, "model_id")
        val _columnIndexOfEncryptedApiKeyRef: Int = getColumnIndexOrThrow(_stmt,
            "encrypted_api_key_ref")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfIsActive: Int = getColumnIndexOrThrow(_stmt, "is_active")
        val _result: RemoteEndpointEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpUrl: String
          _tmpUrl = _stmt.getText(_columnIndexOfUrl)
          val _tmpApiType: String
          _tmpApiType = _stmt.getText(_columnIndexOfApiType)
          val _tmpModelId: String?
          if (_stmt.isNull(_columnIndexOfModelId)) {
            _tmpModelId = null
          } else {
            _tmpModelId = _stmt.getText(_columnIndexOfModelId)
          }
          val _tmpEncryptedApiKeyRef: String?
          if (_stmt.isNull(_columnIndexOfEncryptedApiKeyRef)) {
            _tmpEncryptedApiKeyRef = null
          } else {
            _tmpEncryptedApiKeyRef = _stmt.getText(_columnIndexOfEncryptedApiKeyRef)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpIsActive: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsActive).toInt()
          _tmpIsActive = _tmp != 0
          _result =
              RemoteEndpointEntity(_tmpId,_tmpName,_tmpUrl,_tmpApiType,_tmpModelId,_tmpEncryptedApiKeyRef,_tmpCreatedAt,_tmpIsActive)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deactivateAll() {
    val _sql: String = "UPDATE endpoints SET is_active = 0"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun activate(id: Long) {
    val _sql: String = "UPDATE endpoints SET is_active = 1 WHERE id = ?"
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

  public override suspend fun deleteById(id: Long) {
    val _sql: String = "DELETE FROM endpoints WHERE id = ?"
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

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
