package com.warped.data.local.download

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import com.warped.data.local.db.dao.DownloadCheckpointDao
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

/**
 * Phase 62-01 (LEAK-05 regression, part 1): locks the Phase 61 Leg 5-adjacent
 * observer discipline in [ModelDownloadManager] — every
 * `observeForever(workId)` registration is paired with a `removeObserver`
 * on EVERY terminal path (SUCCEEDED / FAILED / CANCELLED) and on
 * user-initiated pause.
 *
 * The WorkManager/LiveData/DAO collaborators are MockK fakes (no real
 * workers, no sockets, no disk writes outside @TempDir); the observer
 * lambda under test is the production one, captured from
 * `observeForever` and driven with terminal [WorkInfo] fakes.
 *
 * Design pin (d): [ModelDownloadManager.cancelDownload] deliberately does
 * NOT remove the observer itself — the CANCELLED callback owns cleanup, so
 * the platform stop-reason copy (Phase 60-02 API-04) is always observed.
 * Removing that callback cleanup (or the CANCELLED branch) fails
 * `cancel keeps the observer until CANCELLED...`.
 *
 * Mutation-sanity (by inspection): deleting any `cleanupObserver(workId)`
 * call fails its corresponding terminal test via the `observers` size
 * assertion; deleting `activeWorkIds.remove(modelId)` fails the
 * `activeIds` assertions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadObserverRegressionTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var liveData: LiveData<WorkInfo?>
    private lateinit var workManager: WorkManager
    private lateinit var observerSlot: io.mockk.CapturingSlot<Observer<WorkInfo?>>
    private lateinit var manager: ModelDownloadManager

    private fun setUp() {
        liveData = mockk(relaxed = true)
        observerSlot = slot()
        every { liveData.observeForever(capture(observerSlot)) } just Runs
        workManager = mockk(relaxed = true)
        every { workManager.getWorkInfoByIdLiveData(any<UUID>()) } returns liveData
        val checkpointDao = mockk<DownloadCheckpointDao>()
        coEvery { checkpointDao.deleteCheckpoint(any()) } returns Unit
        coEvery { checkpointDao.upsertCheckpoint(any()) } returns Unit
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        every { context.getString(any<Int>()) } returns ""
        every { context.getString(any<Int>(), *anyVararg<Any>()) } returns ""
        manager = ModelDownloadManager(context, workManager, checkpointDao)
    }

    @Suppress("UNCHECKED_CAST")
    private fun observersOf(m: ModelDownloadManager): MutableMap<UUID, Observer<WorkInfo?>> {
        val field = ModelDownloadManager::class.java.getDeclaredField("workObservers")
        field.isAccessible = true
        return field.get(m) as MutableMap<UUID, Observer<WorkInfo?>>
    }

    @Suppress("UNCHECKED_CAST")
    private fun activeIdsOf(m: ModelDownloadManager): MutableMap<String, UUID> {
        val field = ModelDownloadManager::class.java.getDeclaredField("activeWorkIds")
        field.isAccessible = true
        return field.get(m) as MutableMap<String, UUID>
    }

    private fun startDownload(modelId: String = "model-a") {
        manager.startDownload(
            modelId = modelId,
            fileName = "model-a.litertlm",
            fileUrl = "https://huggingface.co/x/resolve/main/model-a.litertlm",
            // Zero bytes skips the StatFs storage gate (Android framework —
            // not drivable on JVM); the observer path under test is identical.
            fileSizeBytes = 0L,
        )
    }

    private fun terminal(state: WorkInfo.State, error: String? = null): WorkInfo {
        val info = mockk<WorkInfo>()
        every { info.state } returns state
        val output = mockk<Data>()
        every { output.getString(any()) } returns (error ?: "boom")
        every { info.outputData } returns output
        return info
    }

    @Test
    fun `start registers exactly one observer and tracks the work id`() = runTest {
        setUp()

        startDownload()

        assertThat(observersOf(manager)).hasSize(1)
        assertThat(activeIdsOf(manager)).containsKey("model-a")
    }

    @Test
    fun `SUCCEEDED unregisters the observer and clears the work id`() = runTest {
        setUp()
        startDownload()

        observerSlot.captured.onChanged(terminal(WorkInfo.State.SUCCEEDED))

        assertThat(observersOf(manager)).isEmpty()
        assertThat(activeIdsOf(manager)).doesNotContainKey("model-a")
        verify(exactly = 1) { liveData.removeObserver(observerSlot.captured) }
        assertThat(manager.getDownloadState("model-a").progress).isEqualTo(1f)
    }

    @Test
    fun `FAILED unregisters the observer and clears the work id`() = runTest {
        setUp()
        startDownload()

        observerSlot.captured.onChanged(terminal(WorkInfo.State.FAILED))

        assertThat(observersOf(manager)).isEmpty()
        assertThat(activeIdsOf(manager)).doesNotContainKey("model-a")
        verify(exactly = 1) { liveData.removeObserver(observerSlot.captured) }
        assertThat(manager.getDownloadState("model-a").error).isEqualTo("boom")
    }

    @Test
    fun `cancel keeps the observer until CANCELLED arrives then cleans up`() = runTest {
        setUp()
        startDownload()

        manager.cancelDownload("model-a")
        verify(exactly = 1) { workManager.cancelWorkById(any<UUID>()) }
        // The observer MUST survive the user cancel: the CANCELLED callback
        // below owns cleanup (and the stop-reason copy). Removing it early
        // would leak the platform-stop observation, never the observer.
        assertThat(observersOf(manager)).hasSize(1)

        observerSlot.captured.onChanged(terminal(WorkInfo.State.CANCELLED))

        assertThat(observersOf(manager)).isEmpty()
        assertThat(activeIdsOf(manager)).doesNotContainKey("model-a")
        verify(exactly = 1) { liveData.removeObserver(observerSlot.captured) }
        assertThat(manager.getDownloadState("model-a").isPaused).isTrue()
    }

    @Test
    fun `pause cancels work and unregisters the observer up-front`() = runTest {
        setUp()
        startDownload()

        manager.pauseDownload("model-a")

        verify(exactly = 1) { workManager.cancelWorkById(any<UUID>()) }
        verify(exactly = 1) { liveData.removeObserver(observerSlot.captured) }
        assertThat(observersOf(manager)).isEmpty()
        assertThat(activeIdsOf(manager)).doesNotContainKey("model-a")
    }

    @Test
    fun `two concurrent downloads track independent observers cleaned independently`() = runTest {
        setUp()
        startDownload("model-a")
        val firstObserver = observerSlot.captured
        startDownload("model-b")
        val secondObserver = observerSlot.captured

        assertThat(observersOf(manager)).hasSize(2)

        firstObserver.onChanged(terminal(WorkInfo.State.SUCCEEDED))

        assertThat(observersOf(manager)).hasSize(1)
        assertThat(activeIdsOf(manager)).doesNotContainKey("model-a")
        assertThat(activeIdsOf(manager)).containsKey("model-b")

        secondObserver.onChanged(terminal(WorkInfo.State.CANCELLED))
        assertThat(observersOf(manager)).isEmpty()
    }
}
