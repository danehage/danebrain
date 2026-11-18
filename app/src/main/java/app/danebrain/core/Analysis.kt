package app.danebrain.core

import org.json.JSONArray
import org.json.JSONObject

/** What the model returns for one capture. Everything here is a suggestion the user can edit. */
data class Analysis(
    val route: String,
    val title: String,
    val summary: String,
    val transcription: String,
    val tags: List<String>,
    val fields: Map<String, String>,
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("route", route)
        o.put("title", title)
        o.put("summary", summary)
        o.put("transcription", transcription)
        o.put("tags", JSONArray(tags))
        val f = JSONObject()
        fields.forEach { (k, v) -> f.put(k, v) }
        o.put("fields", f)
        return o.toString()
    }

    companion object {
        val WINE_FIELDS = listOf("producer", "wine", "vintage", "grapes", "place", "country", "color", "price", "where", "served", "again")
        val DOCUMENT_FIELDS = listOf("vendor", "doc_kind", "amount", "currency", "doc_date", "due_date", "account_last4")

        fun fieldsFor(format: String): List<String> = when (format) {
            Routes.FORMAT_WINE -> WINE_FIELDS
            Routes.FORMAT_DOCUMENT -> DOCUMENT_FIELDS
            else -> emptyList()
        }

        /** Parses model output, tolerating code fences and text around the JSON object. */
        fun parse(raw: String): Analysis {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            require(start >= 0 && end > start) { "Model reply contained no JSON object" }
            val o = JSONObject(raw.substring(start, end + 1))
            val title = o.optString("title").trim()
            require(title.isNotEmpty()) { "Model reply had no title" }
            val tags = ArrayList<String>()
            o.optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val t = Text.slugify(arr.optString(i), 30)
                    if (t.isNotEmpty() && t !in tags) tags.add(t)
                }
            }
            val fields = LinkedHashMap<String, String>()
            o.optJSONObject("fields")?.let { f ->
                for (k in f.keys()) {
                    if (f.isNull(k)) continue
                    val v = when (val any = f.get(k)) {
                        is JSONArray -> (0 until any.length()).joinToString(", ") { any.optString(it) }
                        else -> any.toString()
                    }.trim()
                    if (v.isNotEmpty()) fields[k] = v
                }
            }
            return Analysis(
                route = o.optString("route").trim(),
                title = title,
                summary = o.optString("summary").trim(),
                transcription = o.optString("transcription").trim(),
                tags = tags.take(6),
                fields = fields,
            )
        }

        /** The instructions sent with every capture. Only route names and descriptions leave the phone. */
        fun systemPrompt(routes: List<Route>, today: String): String {
            val routeLines = routes.joinToString("\n") { "- ${it.key}: ${it.whenText}" }
            return """
You file captures into a personal Obsidian vault. A capture is typed or dictated text, scanned pages, or both. Today is $today.

Reply with one JSON object and nothing else:
{
  "route": one of the route keys below,
  "title": short descriptive title, no date, no quotes,
  "summary": one or two plain sentences,
  "transcription": for scans, the full text of the pages in reading order as markdown; "" when there are no pages,
  "tags": up to 4 lowercase kebab-case topic tags,
  "fields": object, see below
}

Routes:
$routeLines

Rules:
- Pick the most specific route that clearly fits. When unsure, use "${Routes.default(routes).key}".
- Transcribe exactly what is on the page. Mark anything you cannot read as [illegible]. Never guess a number.
- Never rewrite, summarize or correct the user's own typed text; it is saved separately as written.
- Dates are ISO (YYYY-MM-DD). Leave a field out when the page does not show it.

"fields" for route "wine-bottle" (read from the label; leave out what is not printed):
  producer, wine (the cuvee or bottling name), vintage (year), grapes (comma-separated), place (appellation or region), country, color (red, white, rose, sparkling, orange, fortified)
"fields" for route "document":
  vendor, doc_kind (invoice, bill, receipt, statement, letter or other), amount (total due or total paid, digits only), currency (ISO code), doc_date, due_date, account_last4 (last four characters of the account number only)
For every other route "fields" is {}.
""".trim()
        }
    }
}
