package com.warped.`data`.local.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.warped.`data`.local.db.entity.PresetEntity
import javax.`annotation`.processing.Generated
import kotlin.Float
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
public class PresetDao_Impl(
  __db: RoomDatabase,
) : PresetDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPresetEntity: EntityInsertAdapter<PresetEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPresetEntity = object : EntityInsertAdapter<PresetEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `presets` (`id`,`name`,`temperature`,`top_p`,`top_k`,`repeat_penalty`,`max_tokens`,`context_size`,`seed`,`threads`,`model_format`,`created_at`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PresetEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindDouble(3, entity.temperature.toDouble())
        statement.bindDouble(4, entity.topP.toDouble())
        statement.bindLong(5, entity.topK.toLong())
        statement.bindDouble(6, entity.repeatPenalty.toDouble())
        statement.bindLong(7, entity.maxTokens.toLong())
        statement.bindLong(8, entity.contextSize.toLong())
        statement.bindLong(9, entity.seed.toLong())
        statement.bindLong(10, entity.threads.toLong())
        statement.bindText(11, entity.modelFormat)
        statement.bindLong(12, entity.createdAt)
      }
    }
  }

  public override suspend fun upsert(preset: PresetEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfPresetEntity.insertAndReturnId(_connection, preset)
    _result
  }

  public override fun observeAll(): Flow<List<PresetEntity>> {
    val _sql: String = "SELECT * FROM presets ORDER BY created_at DESC"
    return createFlow(__db, false, arrayOf("presets")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfTemperature: Int = getColumnIndexOrThrow(_stmt, "temperature")
        val _columnIndexOfTopP: Int = getColumnIndexOrThrow(_stmt, "top_p")
        val _columnIndexOfTopK: Int = getColumnIndexOrThrow(_stmt, "top_k")
        val _columnIndexOfRepeatPenalty: Int = getColumnIndexOrThrow(_stmt, "repeat_penalty")
        val _columnIndexOfMaxTokens: Int = getColumnIndexOrThrow(_stmt, "max_tokens")
        val _columnIndexOfContextSize: Int = getColumnIndexOrThrow(_stmt, "context_size")
        val _columnIndexOfSeed: Int = getColumnIndexOrThrow(_stmt, "seed")
        val _columnIndexOfThreads: Int = getColumnIndexOrThrow(_stmt, "threads")
        val _columnIndexOfModelFormat: Int = getColumnIndexOrThrow(_stmt, "model_format")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _result: MutableList<PresetEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PresetEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpTemperature: Float
          _tmpTemperature = _stmt.getDouble(_columnIndexOfTemperature).toFloat()
          val _tmpTopP: Float
          _tmpTopP = _stmt.getDouble(_columnIndexOfTopP).toFloat()
          val _tmpTopK: Int
          _tmpTopK = _stmt.getLong(_columnIndexOfTopK).toInt()
          val _tmpRepeatPenalty: Float
          _tmpRepeatPenalty = _stmt.getDouble(_columnIndexOfRepeatPenalty).toFloat()
          val _tmpMaxTokens: Int
          _tmpMaxTokens = _stmt.getLong(_columnIndexOfMaxTokens).toInt()
          val _tmpContextSize: Int
          _tmpContextSize = _stmt.getLong(_columnIndexOfContextSize).toInt()
          val _tmpSeed: Int
          _tmpSeed = _stmt.getLong(_columnIndexOfSeed).toInt()
          val _tmpThreads: Int
          _tmpThreads = _stmt.getLong(_columnIndexOfThreads).toInt()
          val _tmpModelFormat: String
          _tmpModelFormat = _stmt.getText(_columnIndexOfModelFormat)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = PresetEntity(_tmpId,_tmpName,_tmpTemperature,_tmpTopP,_tmpTopK,_tmpRepeatPenalty,_tmpMaxTokens,_tmpContextSize,_tmpSeed,_tmpThreads,_tmpModelFormat,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): PresetEntity? {
    val _sql: String = "SELECT * FROM presets WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfTemperature: Int = getColumnIndexOrThrow(_stmt, "temperature")
        val _columnIndexOfTopP: Int = getColumnIndexOrThrow(_stmt, "top_p")
        val _columnIndexOfTopK: Int = getColumnIndexOrThrow(_stmt, "top_k")
        val _columnIndexOfRepeatPenalty: Int = getColumnIndexOrThrow(_stmt, "repeat_penalty")
        val _columnIndexOfMaxTokens: Int = getColumnIndexOrThrow(_stmt, "max_tokens")
        val _columnIndexOfContextSize: Int = getColumnIndexOrThrow(_stmt, "context_size")
        val _columnIndexOfSeed: Int = getColumnIndexOrThrow(_stmt, "seed")
        val _columnIndexOfThreads: Int = getColumnIndexOrThrow(_stmt, "threads")
        val _columnIndexOfModelFormat: Int = getColumnIndexOrThrow(_stmt, "model_format")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _result: PresetEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpTemperature: Float
          _tmpTemperature = _stmt.getDouble(_columnIndexOfTemperature).toFloat()
          val _tmpTopP: Float
          _tmpTopP = _stmt.getDouble(_columnIndexOfTopP).toFloat()
          val _tmpTopK: Int
          _tmpTopK = _stmt.getLong(_columnIndexOfTopK).toInt()
          val _tmpRepeatPenalty: Float
          _tmpRepeatPenalty = _stmt.getDouble(_columnIndexOfRepeatPenalty).toFloat()
          val _tmpMaxTokens: Int
          _tmpMaxTokens = _stmt.getLong(_columnIndexOfMaxTokens).toInt()
          val _tmpContextSize: Int
          _tmpContextSize = _stmt.getLong(_columnIndexOfContextSize).toInt()
          val _tmpSeed: Int
          _tmpSeed = _stmt.getLong(_columnIndexOfSeed).toInt()
          val _tmpThreads: Int
          _tmpThreads = _stmt.getLong(_columnIndexOfThreads).toInt()
          val _tmpModelFormat: String
          _tmpModelFormat = _stmt.getText(_columnIndexOfModelFormat)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _result = PresetEntity(_tmpId,_tmpName,_tmpTemperature,_tmpTopP,_tmpTopK,_tmpRepeatPenalty,_tmpMaxTokens,_tmpContextSize,_tmpSeed,_tmpThreads,_tmpModelFormat,_tmpCreatedAt)
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
    val _sql: String = "DELETE FROM presets WHERE id = ?"
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
