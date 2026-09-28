package com.warped.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.warped.data.local.db.dao.LocalModelDao
import com.warped.data.local.db.entity.LocalModelEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `deleteByFilePath` contract (quick plan 2026-09-28): removes EVERY row
 * sharing a file path (re-download/import duplicates) and returns the
 * deleted count, leaving other rows untouched.
 *
 * NOTE: instrumentation test — requires a device/emulator. If it cannot run
 * in this environment it is documented as not-run in the plan SUMMARY, not
 * faked as passing.
 */
@RunWith(AndroidJUnit4::class)
class LocalModelDaoFilePathTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: LocalModelDao

    private fun entity(filePath: String, name: String = "m") = LocalModelEntity(
        name = name,
        filePath = filePath,
        sizeBytes = 10,
        quantization = "Q4",
        parameterCount = "1B",
        architecture = "Test",
        importedAt = 0L
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.localModelDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteByFilePath_removesAllDuplicates_returnsCount() = runBlocking {
        dao.upsert(entity("/models/dup.litertlm", "a"))
        dao.upsert(entity("/models/dup.litertlm", "b"))
        dao.upsert(entity("/models/other.litertlm", "c"))

        val deleted = dao.deleteByFilePath("/models/dup.litertlm")

        assertEquals(2, deleted)
        assertNull(dao.getByFilePath("/models/dup.litertlm"))
        assertNotNull(dao.getByFilePath("/models/other.litertlm"))
    }

    @Test
    fun deleteByFilePath_unknownPath_returnsZero() = runBlocking {
        dao.upsert(entity("/models/kept.litertlm", "k"))

        val deleted = dao.deleteByFilePath("/models/missing.litertlm")

        assertEquals(0, deleted)
        assertNotNull(dao.getByFilePath("/models/kept.litertlm"))
    }
}
