package ie.suric.connectiontest.chat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val text: String)
data class ModelOption(val slug: String, val displayName: String)

class ChatApi(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(0, TimeUnit.MILLISECONDS).build(),
    private val apiBase: String = "https://api.openai.com/v1"
) {
    fun listModels(accessToken: String): List<ModelOption> {
        val request = Request.Builder().url("$apiBase/models")
            .header("Authorization", "Bearer $accessToken").get().build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, "Could not load models. Check the connection and try again.")
            val models = JSONObject(text).getJSONArray("models")
            return (0 until models.length()).mapNotNull { i ->
                val m = models.optJSONObject(i) ?: return@mapNotNull null
                if (m.optString("visibility") == "list") ModelOption(m.optString("slug"), m.optString("display_name")) else null
            }.filter { it.slug.isNotBlank() }
        }
    }

    fun stream(accessToken: String, model: String, history: List<ChatMessage>, imageDataUrl: String? = null, onDelta: (String) -> Unit): String {
        val input = JSONArray()
        history.forEachIndexed { index, message ->
            val content = if (index == history.lastIndex && message.role == "user" && imageDataUrl != null) {
                JSONArray().put(JSONObject().put("type", "input_text").put("text", message.text))
                    .put(JSONObject().put("type", "input_image").put("image_url", imageDataUrl))
            } else message.text
            input.put(JSONObject().put("role", message.role).put("content", content))
        }
        val payload = JSONObject().put("model", model).put("input", input).put("store", false).put("stream", true)
        val request = Request.Builder().url("$apiBase/responses")
            .header("Authorization", "Bearer $accessToken").header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val message = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                    ?.takeIf(String::isNotBlank) ?: when (response.code) {
                    401 -> "ChatGPT sign-in expired. Reconnect your account and retry."
                    429 -> "ChatGPT plan usage is temporarily unavailable or at its limit. Try again later."
                    else -> "The ChatGPT request failed (HTTP ${response.code}). Try again."
                }
                throw ApiException(response.code, message)
            }
            val source = response.body?.source() ?: throw ApiException(0, "ChatGPT returned an empty response.")
            var complete = false
            var eventError: String? = null
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data:")) {
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val event = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    when (event.optString("type")) {
                        "response.output_text.delta" -> onDelta(event.optString("delta"))
                        "response.completed" -> complete = true
                        "response.failed", "error" -> eventError = event.optJSONObject("response")
                            ?.optJSONObject("error")?.optString("message")
                            ?.takeIf(String::isNotBlank)
                            ?: event.optJSONObject("error")?.optString("message")?.takeIf(String::isNotBlank)
                            ?: "ChatGPT could not complete this response."
                    }
                }
            }
            eventError?.let { throw ApiException(0, it) }
            if (!complete) throw ApiException(0, "The response ended before ChatGPT confirmed completion. Retry the question.")
            return ""
        }
    }
}

class ApiException(val statusCode: Int, override val message: String) : IOException(message)
