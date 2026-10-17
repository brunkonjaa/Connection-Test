package ie.suric.connectiontest.chat

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChatApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ChatApi
    @Before fun start() { server = MockWebServer(); server.start(); api = ChatApi(apiBase = server.url("/v1").toString().trimEnd('/')) }
    @After fun stop() { server.shutdown() }
    @Test fun responseStreamAndRequestUsePlanUsageContract() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Three points\"}\n\n" + "data: {\"type\":\"response.completed\"}\n\n"))
        val deltas = StringBuilder()
        api.stream("oauth-token-test", "model-from-account", listOf(ChatMessage("user", "test")), onDelta = deltas::append)
        assertEquals("Three points", deltas.toString())
        val request = server.takeRequest()
        assertEquals("Bearer oauth-token-test", request.getHeader("Authorization"))
        val json = org.json.JSONObject(request.body.readUtf8())
        assertEquals("model-from-account", json.getString("model"))
        assertFalse(json.getBoolean("store"))
        assertTrue(json.getBoolean("stream"))
        assertTrue(json.getJSONArray("input").getJSONObject(0).has("content"))
    }
    @Test fun malformedEventsAreIgnoredAndMissingCompletionFails() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: {not-json}\n\n"))
        val error = runCatching { api.stream("token", "model", listOf(ChatMessage("user", "x"))) {} }.exceptionOrNull()
        assertTrue(error is ApiException)
        assertTrue(error!!.message!!.contains("ended before"))
    }
    @Test fun httpAuthErrorGetsActionableMessage() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val error = runCatching { api.stream("token", "model", listOf(ChatMessage("user", "x"))) {} }.exceptionOrNull()
        assertEquals("ChatGPT sign-in expired. Reconnect your account and retry.", error?.message)
    }
    @Test fun malformedModelResponseFails() {
        server.enqueue(MockResponse().setBody("{\"unexpected\":true}"))
        assertTrue(runCatching { api.listModels("token") }.isFailure)
    }
    @Test fun networkDropPropagatesAsRetryableFailure() {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        assertTrue(runCatching { api.stream("token", "model", listOf(ChatMessage("user", "x"))) {} }.isFailure)
    }
}
