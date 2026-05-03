package com.warped.`data`.local.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.warped.`data`.local.db.entity.LocalModelEntity
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
public class LocalModelDao_Impl(
  __db: RoomDatabase,
) : LocalModelDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfLocalModelEntity: EntityInsertAdapter<LocalModelEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfLocalModelEntity = object : EntityInsertAdapter<LocalModelEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `local_models` (`id`,`name`,`file_path`,`size_bytes`,`quantization`,`parameter_count`,`architecture`,`imported_at`,`model_format`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LocalModelEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.filePath)
        statement.bindLong(4, entity.sizeBytes)
        statement.bindText(5, entity.quantization)
        statement.bindText(6, entity.parameterCount)
        statement.bindText(7, entity.architecture)
        statement.bindLong(8, entity.importedAt)
        statement.bindText(9, entity.modelFormat)
      }
    }
  }

  public override suspend fun upsert(model: LocalModelEntity): Long = performSuspending(__db, false,
      true) { _connection ->
    val _result: Long = __insertAdapterOfLocalModelEntity.insertAndReturnId(_connection, model)
    _result
  }

  public override fun observeAll(): Flow<List<LocalModelEntity>> {
    val _sql: String = "SELECT * FROM local_models ORDER BY imported_at DESC"
    return createFlow(__db, false, arrayOf("local_models")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfFilePath: Int = getColumnIndexOrThrow(_stmt, "file_path")
        val _columnIndexOfSizeBytes: Int = getColumnIndexOrThrow(_stmt, "size_bytes")
        val _columnIndexOfQuantization: Int = getColumnIndexOrThrow(_stmt, "quantization")
        val _columnIndexOfParameterCount: Int = getColumnIndexOrThrow(_stmt, "parameter_count")
        val _columnIndexOfArchitecture: Int = getColumnIndexOrThrow(_stmt, "architecture")
        val _columnIndexOfImportedAt: Int = getColumnIndexOrThrow(_stmt, "imported_at")
        val _columnIndexOfModelFormat: Int = getColumnIndexOrThrow(_stmt, "model_format")
        val _result: MutableList<LocalModelEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: LocalModelEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpFilePath: String
          _tmpFilePath = _stmt.getText(_columnIndexOfFilePath)
          val _tmpSizeBytes: Long
          _tmpSizeBytes = _stmt.getLong(_columnIndexOfSizeBytes)
          val _tmpQuantization: String
          _tmpQuantization = _stmt.getText(_columnIndexOfQuantization)
          val _tmpParameterCount: String
          _tmpParameterCount = _stmt.getText(_columnIndexOfParameterCount)
          val _tmpArchitecture: String
          _tmpArchitecture = _stmt.getText(_columnIndexOfArchitecture)
          val _tmpImportedAt: Long
          _tmpImportedAt = _stmt.getLong(_columnIndexOfImportedAt)
          val _tmpModelFormat: String
          _tmpModelFormat = _stmt.getText(_columnIndexOfModelFormat)
          _item =
              LocalModelEntity(_tmpId,_tmpName,_tmpFilePath,_tmpSizeBytes,_tmpQuantization,_tmpParameterCount,_tmpArchitecture,_tmpImportedAt,_tmpModelFormat)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): LocalModelEntity? {
    val _sql: String = "SELECT * FROM local_models WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfFilePath: Int = getColumnIndexOrThrow(_stmt, "file_path")
        val _columnIndexOfSizeBytes: Int = getColumnIndexOrThrow(_stmt, "size_bytes")
        val _columnIndexOfQuantization: Int = getColumnIndexOrThrow(_stmt, "quantization")
        val _columnIndexOfParameterCount: Int = getColumnIndexOrThrow(_stmt, "parameter_count")
        val _columnIndexOfArchitecture: Int = getColumnIndexOrThrow(_stmt, "architecture")
        val _columnIndexOfImportedAt: Int = getColumnIndexOrThrow(_stmt, "imported_at")
        val _columnIndexOfModelFormat: Int = getColumnIndexOrThrow(_stmt, "model_format")
        val _result: LocalModelEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpFilePath: String
          _tmpFilePath = _stmt.getText(_columnIndexOfFilePath)
          val _tmpSizeBytes: Long
          _tmpSizeBytes = _stmt.getLong(_columnIndexOfSizeBytes)
          val _tmpQuantization: String
          _tmpQuantization = _stmt.getText(_columnIndexOfQuantization)
          val _tmpParameterCount: String
          _tmpParameterCount = _stmt.getText(_columnIndexOfParameterCount)
          val _tmpArchitecture: String
          _tmpArchitecture = _stmt.getText(_columnIndexOfArchitecture)
          val _tmpImportedAt: Long
          _tmpImportedAt = _stmt.getLong(_columnIndexOfImportedAt)
          val _tmpModelFormat: String
          _tmpModelFormat = _stmt.getText(_columnIndexOfModelFormat)
          _result =
              LocalModelEntity(_tmpId,_tmpName,_tmpFilePath,_tmpSizeBytes,_tmpQuantization,_tmpParameterCount,_tmpArchitecture,_tmpImportedAt,_tmpModelFormat)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: Long) {
    val _sql: String = "DELETE FROM local_models WHERE id = ?"
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
