package com.warped.chat

import com.google.common.truth.Truth.assertThat
import com.warped.domain.model.StreamToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase 62-01 (LEAK-03 regression): locks the Phase 61 Leg 2 clean result
 * (streaming + Stop ×2 with zero zombie collectors).
 *
 * Models the single-flight `runInference` path at the Flow level with the
 * same contract [ChatViewModel] enforces (CR-02 pre-cancel on new send,
 * RUNTIME-14 transport-stop then scope-cancel on Stop): a cancellable fake
 * token Flow wrapped in a REAL OkHttp [ResponseBody] holder (the SSE-stream
 * stand-in — never mocked, never a real socket).
 *
 * Mutation-sanity (by inspection): removing the `stop()` transport call
 * leaves the stream open (`sse closed on Stop` fails); removing the
 * pre-cancel in `send()` lets two collectors interleave (`new send
 * pre-cancels...` fails on content/starts); removing the `finally` close in
 * [SseTokenStream] fails every closed-flag assertion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InferenceCancelRegressionTest {

    /** SSE-stream stand-in: a real ResponseBody whose close is observable. */
    private class SseTokenStream(
        private val body: okhttp3.ResponseBody = "stream".toResponseBody(),
        private val tokenCount: Int = Int.MAX_VALUE,
    ) {
        val starts = AtomicInteger(0)
        val closed = AtomicBoolean(false)

        fun tokens(): Flow<StreamToken> = flow {
            starts.incrementAndGet()
            try {
                var i = 1
                while (i <= tokenCount) {
                    // Touch the real body per token so a closed-stream read
                    // would fail loudly instead of emitting silently.
                    body.source()
                    emit(StreamToken.Delta("tok$i"))
                    i++
                    delay(100)
                }
                emit(StreamToken.Done())
            } finally {
                // SSE discipline: the stream closeable is ALWAYS released —
                // completion, failure, or Stop-cancel alike.
                try {
                    body.close()
                } finally {
                    closed.set(true)
                }
            }
        }
    }

    /**
     * Single-flight runner mirroring the ChatViewModel turn contract:
     * at most one collecting job; new send pre-cancels the old turn;
     * stop halts transport first, then cancels the scope.
     */
    private class SingleFlightRunner(
        private val scope: CoroutineScope,
        private val stream: SseTokenStream,
    ) {
        var content = ""
        var streaming = false
        private var job: Job? = null

        fun send() {
            // CR-02: cancel any in-flight turn before starting a new one.
            job?.cancel()
            job = null
            content = ""
            val turnJob = scope.launch {
                streaming = true
                val turnScope = this
                stream.tokens()
                    .shareIn(turnScope, SharingStarted.Eagerly, replay = 1)
                    .collect { token ->
                        when (token) {
                            is StreamToken.Delta -> content += token.content
                            is StreamToken.Done -> streaming = false
                            else -> Unit
                        }
                    }
            }
            job = turnJob
        }

        fun stop() {
            // RUNTIME-14 order: transport halt (cancels the collecting
            // coroutine, running the stream finally-close), then state reset.
            job?.cancel()
            job = null
            streaming = false
        }
    }

    @Test
    fun `cancel mid-stream emits no further tokens and leaves no zombie job`() = runTest {
        val stream = SseTokenStream()
        val worker = CoroutineScope(coroutineContext + SupervisorJob())
        val runner = SingleFlightRunner(worker, stream)

        runner.send()
        advanceTimeBy(250) // tok1..tok3 at t=0,100,200
        assertThat(runner.content).isEqualTo("tok1tok2tok3")

        runner.stop()
        advanceTimeBy(1_000)

        assertThat(runner.content).isEqualTo("tok1tok2tok3")
        assertThat(runner.streaming).isFalse()
        assertThat(stream.starts.get()).isEqualTo(1)
        worker.cancel()
    }

    @Test
    fun `sse stream closeable is closed on Stop`() = runTest {
        val stream = SseTokenStream()
        val worker = CoroutineScope(coroutineContext + SupervisorJob())
        val runner = SingleFlightRunner(worker, stream)

        runner.send()
        advanceTimeBy(150)
        assertThat(stream.closed.get()).isFalse()

        runner.stop()
        advanceUntilIdle()

        assertThat(stream.closed.get()).isTrue()
        worker.cancel()
    }

    @Test
    fun `sse stream closeable is closed on normal completion`() = runTest {
        val stream = SseTokenStream(tokenCount = 2)
        val worker = CoroutineScope(coroutineContext + SupervisorJob())
        val runner = SingleFlightRunner(worker, stream)

        runner.send()
        advanceUntilIdle()

        assertThat(runner.content).isEqualTo("tok1tok2")
        assertThat(runner.streaming).isFalse()
        assertThat(stream.closed.get()).isTrue()
        worker.cancel()
    }

    @Test
    fun `follow-up run after Stop starts cleanly with no concurrent duplicate`() = runTest {
        val stream = SseTokenStream(tokenCount = 5)
        val worker = CoroutineScope(coroutineContext + SupervisorJob())
        val runner = SingleFlightRunner(worker, stream)

        runner.send()
        advanceTimeBy(250)
        runner.stop()
        advanceUntilIdle()

        runner.send()
        advanceUntilIdle()

        assertThat(runner.content).isEqualTo("tok1tok2tok3tok4tok5")
        assertThat(runner.streaming).isFalse()
        assertThat(stream.closed.get()).isTrue()
        // Exactly two upstream runs — Stop never leaks a zombie that would
        // make this three, and the follow-up never double-starts.
        assertThat(stream.starts.get()).isEqualTo(2)
        worker.cancel()
    }

    @Test
    fun `new send pre-cancels the in-flight turn so collectors never interleave`() = runTest {
        val stream = SseTokenStream(tokenCount = 5)
        val worker = CoroutineScope(coroutineContext + SupervisorJob())
        val runner = SingleFlightRunner(worker, stream)

        runner.send()
        advanceTimeBy(150)
        runner.send() // no Stop — the pre-cancel must still kill turn 1
        advanceUntilIdle()

        // Turn 2's full sequence only — zero tokens leaked from turn 1's
        // collector into the new turn's accumulation.
        assertThat(runner.content).isEqualTo("tok1tok2tok3tok4tok5")
        assertThat(stream.starts.get()).isEqualTo(2)
        worker.cancel()
    }

    @Test
    fun `cancelled run never reaches Done - streaming flag cleared by Stop not by terminal`() = runTest {
        val stream = SseTokenStream(tokenCount = 5)
        val worker = CoroutineScope(coroutineContext + SupervisorJob())
        val runner = SingleFlightRunner(worker, stream)

        runner.send()
        advanceTimeBy(150)
        assertThat(runner.streaming).isTrue()
        runner.stop()
        advanceUntilIdle()

        // A cancelled run emits no Done; the Stop path (not a terminal
        // token) is what clears streaming — a wedged spinner here would be
        // the Leg 2 leak surfacing in UI state.
        assertThat(runner.streaming).isFalse()
        assertThat(runner.content).isEqualTo("tok1tok2")
        assertThat(stream.closed.get()).isTrue()
        worker.cancel()
    }
}
