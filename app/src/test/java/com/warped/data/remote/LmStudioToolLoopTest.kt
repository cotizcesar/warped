package com.warped.data.remote

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.inference.InputSanitizer
import com.warped.data.remote.dto.OpenAiFunctionDef
import com.warped.data.remote.dto.OpenAiTool
import com.warped.data.remote.provider.LmStudioToolLoop
import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ChatRequest
import com.warped.domain.model.GenerationParameters
import com.warped.domain.model.Role
import com.warped.domain.model.StreamToken
import com.warped.domain.skills.ToolExecutor
import com.warped.domain.skills.ToolResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import java.util.Collections

/**
 * 47-03 (SKILLS-10/SKILLS-12): [LmStudioToolLoop] verification.
 *
 * No MockWebServer dependency: an OkHttp application interceptor serves
 * canned SSE/JSON bodies in order (short-circuit, no sockets), so the
 * release graph + RUNTIME-12 audit stay untouched (zero new deps).
 * `runBlocking` is used throughout — the loop blocks on OkHttp reads,
 * exactly like the 46-01 `LmStudioCancelTest` HTTP tests.
 */
class LmStudioToolLoopTest {

    // ------------------------------------------------------------------
    // Scripted transport fake.
    // ------------------------------------------------------------------

    private class Scripted(
        bodies: List<String>,
        val requestBodies: MutableList<String> = Collections.synchronizedList(mutableListOf()),
        val requestPaths: MutableList<String> = Collections.synchronizedList(mutableListOf()),
    ) : Interceptor {
        private val queue = ArrayDeque(bodies)

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            requestPaths += req.url.encodedPath
            requestBodies += bodyString(req)
            val body = synchronized(queue) {
                if (queue.isEmpty()) {
                    "data: {\"choices\":[{\"delta\":{\"content\":\"drained\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                } else {
                    queue.removeFirst()
                }
            }
            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("text/event-stream".toMediaType()))
                .build()
        }

        companion object {
            fun bodyString(req: Request): String {
                val buf = Buffer()
                req.body?.writeTo(buf)
                return buf.readUtf8()
            }
        }
    }

    private class FakeExecutor(
        val calls: MutableList<Pair<String, String>> = Collections.synchronizedList(mutableListOf()),
        val handler: suspend (String, String) -> ToolResult = { name, _ ->
            ToolResult.Success("ok:$name", "ok:$name")
        },
    ) : ToolExecutor {
        override suspend fun execute(name: String, argsJson: String): ToolResult {
            calls += name to argsJson
            return handler(name, argsJson)
        }
    }

    private fun loop(
        scripted: Scripted,
        executor: ToolExecutor,
    ): Pair<LmStudioToolLoop, OkHttpClient> {
        val client = OkHttpClient.Builder()
            .addInterceptor(scripted)
            .build()
        return LmStudioToolLoop(
            client = client,
            baseUrl = "http://127.0.0.1:9/",
            modelId = "test-model",
            executor = executor,
            inputSanitizer = InputSanitizer(),
        ) to client
    }

    private fun chatRequest() = ChatRequest(
        messages = listOf(ChatMessage(role = Role.USER, content = "what is (2+3)*4?")),
        parameters = GenerationParameters(),
    )

    private fun calcTools() = listOf(
        OpenAiTool(
            function = OpenAiFunctionDef(
                name = "calculator",
                description = "Evaluate an arithmetic expression.",
                parameters = null,
            ),
        ),
    )

    // ------------------------------------------------------------------
    // SSE body builders.
    // ------------------------------------------------------------------

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun chunk(deltaJson: String, finish: String? = null): String {
        val fr = if (finish == null) "null" else "\"$finish\""
        return "data: {\"choices\":[{\"delta\":$deltaJson,\"finish_reason\":$fr}]}\n\n"
    }

    private fun contentChunk(text: String, finish: String? = null): String =
        chunk("{\"content\":\"${esc(text)}\"}", finish)

    private fun toolCallChunk(
        index: Int,
        id: String?,
        nameFrag: String?,
        argsFrag: String?,
    ): String {
        val parts = mutableListOf("\"index\":$index")
        if (id != null) parts += "\"id\":\"$id\""
        val fn = mutableListOf<String>()
        if (nameFrag != null) fn += "\"name\":\"${esc(nameFrag)}\""
        if (argsFrag != null) fn += "\"arguments\":\"${esc(argsFrag)}\""
        parts += "\"function\":{${fn.joinToString(",")}}"
        return chunk("{\"tool_calls\":[{${parts.joinToString(",")}}]}")
    }

    private fun collectAll(loop: LmStudioToolLoop, tools: List<OpenAiTool>, created: MutableList<Call>): List<StreamToken> {
        val tokens = mutableListOf<StreamToken>()
        runBlocking {
            loop.run(chatRequest(), tools) { created += it }.collect { tokens += it }
        }
        return tokens
    }

    // ------------------------------------------------------------------
    // (a) Chunk-split tool_calls reassembly + role:tool re-POST.
    // ------------------------------------------------------------------

    @Test
    fun `tool_calls split across chunks materialize and re-POST role tool`() {
        val fullArgs = "{\"expression\":\"(2+3)*4\"}"
        // Fragments chosen so no single chunk holds the full object.
        val scripted = Scripted(
            listOf(
                // Round 1: content + tool call in 3 fragments.
                contentChunk("Let me compute. ") +
                    toolCallChunk(0, "call-abc", "calc", "") +
                    toolCallChunk(0, null, "ulator", "{\"expr") +
                    toolCallChunk(0, null, null, "ession\":\"(2+3)*4\"}", ) +
                    chunk("{}", "tool_calls") +
                    "data: [DONE]\n\n",
                // Round 2: final answer.
                contentChunk("The answer is 20", "stop") +
                    "data: [DONE]\n\n",
            ),
        )
        val executor = FakeExecutor { _, _ -> ToolResult.Success("20", "20") }
        val (toolLoop, _) = loop(scripted, executor)
        val created = mutableListOf<Call>()

        val tokens = collectAll(toolLoop, calcTools(), created)

        // Accumulator reassembled the split fragments exactly.
        assertThat(executor.calls).hasSize(1)
        assertThat(executor.calls.single().first).isEqualTo("calculator")
        assertThat(executor.calls.single().second).isEqualTo(fullArgs)
        // Progress + completion records, no text markers.
        assertThat(tokens).contains(StreamToken.ToolStatus("calculator"))
        assertThat(tokens).contains(StreamToken.ToolCompleted("calculator", "20", null))
        assertThat(tokens.filterIsInstance<StreamToken.Delta>().map { it.content })
            .contains("The answer is 20")
        assertThat(tokens.filterIsInstance<StreamToken.Done>()).hasSize(1)
        // Follow-up re-POST carries the assistant echo + role:tool reply.
        assertThat(scripted.requestBodies).hasSize(2)
        assertThat(scripted.requestPaths).containsExactly("/v1/chat/completions", "/v1/chat/completions")
        val followUp = scripted.requestBodies[1]
        assertThat(followUp).contains("\"role\":\"tool\"")
        assertThat(followUp).contains("\"tool_call_id\":\"call-abc\"")
        assertThat(followUp).contains("20")
        // tools[] populated on the initial request.
        assertThat(scripted.requestBodies[0]).contains("\"tools\"")
        assertThat(scripted.requestBodies[0]).contains("calculator")
        // Every round reported its Call (46-01 cancel contract).
        assertThat(created).hasSize(2)
    }

    @Test
    fun `parallel tool_calls stay index-keyed and never mix`() {
        val scripted = Scripted(
            listOf(
                // Two parallel calls, arguments interleaved by index.
                toolCallChunk(0, "call-0", "calc", "") +
                    toolCallChunk(1, "call-1", "json", "") +
                    toolCallChunk(0, null, "ulator", "{\"expression\"") +
                    toolCallChunk(1, null, "_formatter", "{\"json\"") +
                    toolCallChunk(0, null, null, ":\"1+1\"}") +
                    toolCallChunk(1, null, null, ":\"{\\\"a\\\":1}\"}") +
                    chunk("{}", "tool_calls") +
                    "data: [DONE]\n\n",
                contentChunk("done both", "stop") +
                    "data: [DONE]\n\n",
            ),
        )
        val executor = FakeExecutor { _, _ -> ToolResult.Success("r", "r") }
        val (toolLoop, _) = loop(scripted, executor)
        val created = mutableListOf<Call>()

        collectAll(toolLoop, calcTools(), created)

        assertThat(executor.calls.map { it.first })
            .containsExactly("calculator", "json_formatter")
        assertThat(executor.calls[0].second).isEqualTo("{\"expression\":\"1+1\"}")
        assertThat(executor.calls[1].second).isEqualTo("{\"json\":\"{\\\"a\\\":1}\"}")
        val followUp = scripted.requestBodies[1]
        assertThat(followUp).contains("\"tool_call_id\":\"call-0\"")
        assertThat(followUp).contains("\"tool_call_id\":\"call-1\"")
    }

    // ------------------------------------------------------------------
    // (b) 5-round cap: 6th tool round never fires.
    // ------------------------------------------------------------------

    @Test
    fun `tool loop caps at five rounds then answers`() {
        fun toolRound(id: String): String =
            toolCallChunk(0, id, "calculator", "{\"expression\":\"1+1\"}") +
                contentChunk("part") +
                chunk("{}", "tool_calls") +
                "data: [DONE]\n\n"
        // Six tool_calls responses queued; the 6th must never execute.
        // Each carries content so the silent-model fallback stays quiet and
        // the request count proves the cap exactly (1 initial + 5 rounds).
        val scripted = Scripted((1..6).map { toolRound("call-$it") })
        val executor = FakeExecutor { _, _ -> ToolResult.Success("2", "2") }
        val (toolLoop, _) = loop(scripted, executor)
        val created = mutableListOf<Call>()

        val tokens = collectAll(toolLoop, calcTools(), created)

        assertThat(executor.calls).hasSize(5)
        assertThat(scripted.requestBodies).hasSize(6)
        assertThat(created).hasSize(6)
        assertThat(tokens.filterIsInstance<StreamToken.Done>()).hasSize(1)
        assertThat(tokens.filterIsInstance<StreamToken.Error>()).isEmpty()
    }

    // ------------------------------------------------------------------
    // (c) Cancel between rounds: no further Call is created.
    // ------------------------------------------------------------------

    @Test
    fun `cancel between rounds aborts before re-POST`() {
        val gate = CompletableDeferred<Unit>()
        val scripted = Scripted(
            listOf(
                toolCallChunk(0, "call-1", "calculator", "{\"expression\":\"1+1\"}") +
                    chunk("{}", "tool_calls") +
                    "data: [DONE]\n\n",
                contentChunk("never", "stop") +
                    "data: [DONE]\n\n",
            ),
        )
        val executor = FakeExecutor { _, _ ->
            gate.await() // suspended mid-round until the test cancels
            ToolResult.Success("2", "2")
        }
        val (toolLoop, _) = loop(scripted, executor)
        val created = Collections.synchronizedList(mutableListOf<Call>())

        runBlocking {
            val job = launch(Dispatchers.IO) {
                toolLoop.run(chatRequest(), calcTools()) { created += it }.collect { }
            }
            val entered = withTimeoutOrNull(10_000) {
                while (executor.calls.isEmpty()) delay(50)
                true
            } ?: false
            assertThat(entered).isTrue()
            // ensureActive() after the (now-cancelled) execute must abort —
            // no second request, no second Call.
            job.cancel()
            job.join()
        }

        assertThat(executor.calls).hasSize(1)
        assertThat(scripted.requestBodies).hasSize(1)
        assertThat(created).hasSize(1)
    }

    // ------------------------------------------------------------------
    // (d) Malformed calls fall back to plain content, never crash.
    // ------------------------------------------------------------------

    @Test
    fun `blank tool name falls back to content`() {
        val scripted = Scripted(
            listOf(
                contentChunk("Sorry, plain answer.") +
                    toolCallChunk(0, "call-x", "", "{\"expression\":\"1+1\"}") +
                    chunk("{}", "tool_calls") +
                    "data: [DONE]\n\n",
            ),
        )
        val executor = FakeExecutor()
        val (toolLoop, _) = loop(scripted, executor)
        val created = mutableListOf<Call>()

        val tokens = collectAll(toolLoop, calcTools(), created)

        assertThat(executor.calls).isEmpty()
        assertThat(tokens.filterIsInstance<StreamToken.ToolCompleted>()).isEmpty()
        assertThat(tokens.filterIsInstance<StreamToken.Delta>().map { it.content })
            .contains("Sorry, plain answer.")
        assertThat(tokens.filterIsInstance<StreamToken.Done>()).hasSize(1)
        assertThat(scripted.requestBodies).hasSize(1)
    }

    @Test
    fun `non-object args fall back to content`() {
        val scripted = Scripted(
            listOf(
                contentChunk("Plain it is.") +
                    toolCallChunk(0, "call-x", "calculator", "\"oops\"") +
                    chunk("{}", "tool_calls") +
                    "data: [DONE]\n\n",
            ),
        )
        val executor = FakeExecutor()
        val (toolLoop, _) = loop(scripted, executor)

        val tokens = collectAll(toolLoop, calcTools(), mutableListOf())

        assertThat(executor.calls).isEmpty()
        assertThat(tokens.filterIsInstance<StreamToken.Delta>().map { it.content })
            .contains("Plain it is.")
        assertThat(tokens.filterIsInstance<StreamToken.Done>()).hasSize(1)
    }

    // ------------------------------------------------------------------
    // (e) Non-streaming toolCalls DTO path executes.
    // ------------------------------------------------------------------

    @Test
    fun `non-streaming tool_calls execute and re-POST`() {
        val nsBody = "{\"choices\":[{\"message\":{\"content\":\"\",\"tool_calls\":" +
            "[{\"id\":\"call-ns\",\"function\":{\"name\":\"calculator\"," +
            "\"arguments\":\"{\\\"expression\\\":\\\"1+1\\\"}\"}}]}}]}"
        val scripted = Scripted(
            listOf(
                "$nsBody\n",
                contentChunk("Two", "stop") +
                    "data: [DONE]\n\n",
            ),
        )
        val executor = FakeExecutor { _, _ -> ToolResult.Success("2", "2") }
        val (toolLoop, _) = loop(scripted, executor)
        val created = mutableListOf<Call>()

        val tokens = collectAll(toolLoop, calcTools(), created)

        assertThat(executor.calls).hasSize(1)
        assertThat(executor.calls.single().first).isEqualTo("calculator")
        assertThat(tokens).contains(StreamToken.ToolCompleted("calculator", "2", null))
        assertThat(tokens.filterIsInstance<StreamToken.Delta>().map { it.content })
            .contains("Two")
        assertThat(scripted.requestBodies).hasSize(2)
        assertThat(scripted.requestBodies[1]).contains("\"tool_call_id\":\"call-ns\"")
    }

    // ------------------------------------------------------------------
    // (f) Silent model after a tool error → one plain re-POST fallback.
    // ------------------------------------------------------------------

    @Test
    fun `tool error then silence triggers plain fallback re-POST`() {
        val scripted = Scripted(
            listOf(
                // Round 1: tool_calls, no content; execution fails.
                toolCallChunk(0, "call-e", "calculator", "{\"expression\":\"???\"}") +
                    chunk("{}", "tool_calls") +
                    "data: [DONE]\n\n",
                // Round 2: model goes silent (empty stop).
                chunk("{}", "stop") +
                    "data: [DONE]\n\n",
                // Round 3 (fallback, no tools[]): plain answer.
                contentChunk("I could not compute that, but here is a plain answer.", "stop") +
                    "data: [DONE]\n\n",
            ),
        )
        val executor = FakeExecutor { _, _ -> ToolResult.Failure("invalid expression") }
        val (toolLoop, _) = loop(scripted, executor)

        val tokens = collectAll(toolLoop, calcTools(), mutableListOf())

        assertThat(executor.calls).hasSize(1)
        assertThat(tokens)
            .contains(StreamToken.ToolCompleted("calculator", "invalid expression", "invalid expression"))
        // The fallback round carries no tools[] (plain re-POST).
        assertThat(scripted.requestBodies).hasSize(3)
        assertThat(scripted.requestBodies[2]).doesNotContain("\"tools\"")
        assertThat(tokens.filterIsInstance<StreamToken.Delta>().map { it.content })
            .contains("I could not compute that, but here is a plain answer.")
        assertThat(tokens.filterIsInstance<StreamToken.Done>()).hasSize(1)
    }

    // ------------------------------------------------------------------
    // Unknown tool names: executor rejects, error-string role:tool reply.
    // ------------------------------------------------------------------

    @Test
    fun `unknown tool name surfaces failure without crashing`() {
        val scripted = Scripted(
            listOf(
                toolCallChunk(0, "call-u", "evil_tool", "{}") +
                    chunk("{}", "tool_calls") +
                    "data: [DONE]\n\n",
                contentChunk("Recovered.", "stop") +
                    "data: [DONE]\n\n",
            ),
        )
        // Mirrors LocalToolExecutor allowlist behavior: unknown → Failure.
        val executor = FakeExecutor { name, _ -> ToolResult.Failure("unknown tool: $name") }
        val (toolLoop, _) = loop(scripted, executor)

        val tokens = collectAll(toolLoop, calcTools(), mutableListOf())

        assertThat(tokens.filterIsInstance<StreamToken.ToolCompleted>()).hasSize(1)
        val record = tokens.filterIsInstance<StreamToken.ToolCompleted>().single()
        assertThat(record.errorReason).isNotNull()
        val followUp = scripted.requestBodies[1]
        assertThat(followUp).contains("Error:")
        assertThat(tokens.filterIsInstance<StreamToken.Delta>().map { it.content })
            .contains("Recovered.")
    }

    // ------------------------------------------------------------------
    // Resumed history: role:tool rows re-send as tool context.
    // ------------------------------------------------------------------

    @Test
    fun `resumed tool rows resend as role tool context`() {
        val scripted = Scripted(
            listOf(
                contentChunk("Fresh answer", "stop") +
                    "data: [DONE]\n\n",
            ),
        )
        val executor = FakeExecutor()
        val (toolLoop, _) = loop(scripted, executor)
        val resumed = chatRequest().copy(
            messages = listOf(
                ChatMessage(role = Role.USER, content = "calc please"),
                // 47-01 persistence encoding.
                ChatMessage(role = Role.TOOL, content = "calculator\n20"),
                ChatMessage(role = Role.ASSISTANT, content = "It is 20."),
                ChatMessage(role = Role.USER, content = "and double it?"),
            ),
        )

        val tokens = mutableListOf<StreamToken>()
        runBlocking {
            toolLoop.run(resumed, calcTools()) { }.collect { tokens += it }
        }

        assertThat(scripted.requestBodies).hasSize(1)
        val initial = scripted.requestBodies.single()
        assertThat(initial).contains("\"role\":\"tool\"")
        assertThat(initial).contains("\"tool_call_id\":\"calculator\"")
        assertThat(initial).contains("20")
        assertThat(tokens.filterIsInstance<StreamToken.Done>()).hasSize(1)
    }
}
