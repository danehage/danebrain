package app.danebrain.work

import android.util.Base64
import app.danebrain.core.Analysis
import app.danebrain.core.Route
import app.danebrain.data.Capture
import app.danebrain.net.OpenRouter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** One model call per capture: pages and typed text in, a filing suggestion out. */
class Analyzer(private val api: OpenRouter, private val routes: () -> List<Route>, private val model: () -> String) {

    fun analyze(capture: Capture): Analysis {
        val content = JSONArray()
        val intro = buildString {
            if (capture.pages.isNotEmpty()) append("Scanned pages: ${capture.pages.size}.\n")
            if (capture.userText.isNotBlank()) append("The user's own text:\n${capture.userText.trim()}")
            else append("The user typed nothing.")
        }
        content.put(JSONObject().put("type", "text").put("text", intro))
        for (path in capture.pages) {
            val b64 = Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP)
            content.put(
                JSONObject().put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
            )
        }
        val body = JSONObject()
            .put("model", model())
            .put("temperature", 0)
            .put("max_tokens", 6000)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", Analysis.systemPrompt(routes(), LocalDate.now().toString())))
                    .put(JSONObject().put("role", "user").put("content", content))
            )
        val message = api.chat(body)
        return Analysis.parse(message.optString("content"))
    }
}
