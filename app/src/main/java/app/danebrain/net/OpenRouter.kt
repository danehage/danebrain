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

/** Minimal blocking client for OpenRouter's chat completions endpoint. Call off the main thread. */
class OpenRouter(private val apiKey: () -> String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Sends [body] and returns the first choice's message object. */
    fun chat(body: JSONObject): JSONObject {
        val key = apiKey()
        if (key.isBlank()) throw ApiException(401, "No OpenRouter API key set")
        // Only route to providers that do not retain or train on prompts.
        if (!body.has("provider")) body.put("provider", JSONObject().put("data_collection", "deny"))
        val request = Request.Builder()
            .url(URL)
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
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
        private const val URL = "https://openrouter.ai/api/v1/chat/completions"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
