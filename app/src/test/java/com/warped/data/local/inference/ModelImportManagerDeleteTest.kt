package com.warped.data.local.inference

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.LocalModel
import com.warped.domain.repository.LocalModelRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

/**
 * Honest-delete contract (quick plan 2026-09-28, D4): `deleteModel` throws
 * `IllegalStateException` with an English message on every failure path and
 * never swallows — including repository failures, which propagate.
 *
 * `deleteModel` only touches `model.filePath` + the repository, so a
 * relaxed `Context` mock suffices (no `filesDir` stubbing needed).
 */
class ModelImportManagerDeleteTest {

    @TempDir
    lateinit var tempDir: File

    private fun manager(repository: LocalModelRepository): ModelImportManager {
        val context = mockk<Context>(relaxed = true)
        return ModelImportManager(context, repository)
    }

    private fun modelAt(path: String, name: String = "test-model"): LocalModel =
        LocalModel(
            id = 7,
            name = name,
            filePath = path,
            sizeBytes = 10,
            quantization = "Q4",
            parameterCount = "1B",
            architecture = "Test",
            importedAt = Instant.EPOCH
        )

    @Test
    fun `file present and rows deleted succeeds and removes file`() = runTest {
        val file = File(tempDir, "model.litertlm").also { it.writeText("weights") }
        val repository = mockk<LocalModelRepository>()
        coEvery { repository.deleteByFilePath(file.absolutePath) } returns 1

        manager(repository).deleteModel(modelAt(file.absolutePath))

        assertThat(file.exists()).isFalse()
        coVerify { repository.deleteByFilePath(file.absolutePath) }
    }

    @Test
    fun `file present but zero rows throws no-library-entry`() = runTest {
        val file = File(tempDir, "orphan.litertlm").also { it.writeText("weights") }
        val repository = mockk<LocalModelRepository>()
        coEvery { repository.deleteByFilePath(file.absolutePath) } returns 0

        val error = assertThrows<IllegalStateException> {
            manager(repository).deleteModel(modelAt(file.absolutePath))
        }

        assertThat(error.message).contains("no library entry")
    }

    @Test
    fun `file absent but rows deleted succeeds as stale-row cleanup`() = runTest {
        val missing = File(tempDir, "gone.litertlm").absolutePath
        val repository = mockk<LocalModelRepository>()
        coEvery { repository.deleteByFilePath(missing) } returns 2

        manager(repository).deleteModel(modelAt(missing))

        coVerify { repository.deleteByFilePath(missing) }
    }

    @Test
    fun `file absent and zero rows throws not-found`() = runTest {
        val missing = File(tempDir, "nowhere.litertlm").absolutePath
        val repository = mockk<LocalModelRepository>()
        coEvery { repository.deleteByFilePath(missing) } returns 0

        val error = assertThrows<IllegalStateException> {
            manager(repository).deleteModel(modelAt(missing, name = "ghost"))
        }

        assertThat(error.message).contains("ghost")
    }

    @Test
    fun `repository failure propagates and is never swallowed`() = runTest {
        val file = File(tempDir, "model.litertlm").also { it.writeText("weights") }
        val repository = mockk<LocalModelRepository>()
        coEvery { repository.deleteByFilePath(any()) } throws
            RuntimeException("disk I/O error")

        assertThrows<RuntimeException> {
            manager(repository).deleteModel(modelAt(file.absolutePath))
        }
    }
}
