package app.danebrain.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The service answered, but with an error (bad key, unknown model, no eligible provider...). */
class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Minimal blocking client for an OpenAI-compatible chat completions API; OpenRouter by default.
 * Call off the main thread.
 */
class OpenRouter(private val apiKey: () -> String, private val baseUrl: () -> String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun base(): String = baseUrl().trim().trimEnd('/')
    private fun isOpenRouter(): Boolean = base().contains("openrouter.ai")

    /**
     * Checks that the endpoint answers and accepts the key. Returns a short line to show the user;
     * throws [ApiException] or [IOException] with the reason otherwise.
     */
    fun test(): String {
        val key = apiKey()
        if (key.isBlank() && isOpenRouter()) throw ApiException(401, "No API key set")
        // OpenRouter's model list is public, so its key endpoint is the one that proves the key.
        val url = base() + if (isOpenRouter()) "/key" else "/models"
        val builder = Request.Builder().url(url).get()
        if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                null
            }
            json?.optJSONObject("error")?.let { err ->
                throw ApiException(err.optInt("code", response.code), err.optString("message", "Request failed"))
            }
            if (!response.isSuccessful) throw ApiException(response.code, "HTTP ${response.code}")
            val remaining = json?.optJSONObject("data")?.takeIf { !it.isNull("limit_remaining") }?.optDouble("limit_remaining")
            return if (remaining != null && !remaining.isNaN()) "Key works. $%.2f left on its limit.".format(remaining)
            else if (isOpenRouter()) "Key works. It has no spending limit set."
            else "The endpoint answered."
        }
    }

    /** Sends [body] and returns the first choice's message object. */
    fun chat(body: JSONObject): JSONObject {
        val key = apiKey()
        if (key.isBlank() && isOpenRouter()) throw ApiException(401, "No API key set")
        // On OpenRouter, only route to providers that do not retain or train on prompts.
        if (isOpenRouter() && !body.has("provider")) body.put("provider", JSONObject().put("data_collection", "deny"))
        val builder = Request.Builder()
            .url(base() + "/chat/completions")
            .post(body.toString().toRequestBody(JSON))
        if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw ApiException(response.code, "HTTP ${response.code}: ${text.take(200)}")
            }
            json.optJSONObject("error")?.let { err ->
                throw ApiException(err.optInt("code", response.code), err.optString("message", "Request failed"))
            }
            if (!response.isSuccessful) throw ApiException(response.code, "HTTP ${response.code}")
            val choice = json.optJSONArray("choices")?.optJSONObject(0)
                ?: throw ApiException(response.code, "Reply had no choices")
            choice.optJSONObject("error")?.let { err ->
                throw ApiException(err.optInt("code", 500), err.optString("message", "Provider error"))
            }
            return choice.optJSONObject("message") ?: throw ApiException(response.code, "Reply had no message")
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
