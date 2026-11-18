package app.danebrain.chat

import app.danebrain.data.SearchIndex
import app.danebrain.data.Settings
import app.danebrain.data.Vault
import app.danebrain.net.OpenRouter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * A chat over the vault. The model gets three read-only tools that run on the phone: search the
 * index, list a folder, read a note. Nothing here can write to the vault.
 */
class ChatEngine(
    private val api: OpenRouter,
    private val settings: Settings,
    private val vault: Vault,
    private val index: SearchIndex,
) {
    /** The conversation in API format (system prompt excluded). */
    private val history = ArrayList<JSONObject>()

    fun reset() = history.clear()

    /** Sends one user message and returns the final answer. Blocking; call off the main thread. */
    fun send(userText: String, onStatus: (String) -> Unit): String {
        val mark = history.size
        history.add(JSONObject().put("role", "user").put("content", userText))
        try {
            repeat(MAX_STEPS) {
                val messages = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt()))
                history.forEach { messages.put(it) }
                val body = JSONObject()
                    .put("model", settings.chatModel)
                    .put("max_tokens", 4000)
                    .put("messages", messages)
                    .put("tools", TOOLS)
                val reply = api.chat(body)
                val calls = reply.optJSONArray("tool_calls")
                val assistant = JSONObject().put("role", "assistant")
                    .put("content", if (reply.isNull("content")) "" else reply.optString("content"))
                if (calls != null && calls.length() > 0) assistant.put("tool_calls", calls)
                history.add(assistant)
                if (calls == null || calls.length() == 0) return reply.optString("content").trim()

                for (i in 0 until calls.length()) {
                    val call = calls.getJSONObject(i)
                    val fn = call.optJSONObject("function") ?: JSONObject()
                    val name = fn.optString("name")
                    val args = try {
                        JSONObject(fn.optString("arguments", "{}").ifBlank { "{}" })
                    } catch (e: Exception) {
                        JSONObject()
                    }
                    onStatus(describe(name, args))
                    val result = try {
                        runTool(name, args)
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }
                    history.add(
                        JSONObject().put("role", "tool")
                            .put("tool_call_id", call.optString("id"))
                            .put("content", result)
                    )
                }
            }
            return "I ran out of lookup steps before reaching an answer. Try a narrower question."
        } catch (e: Exception) {
            // Drop the half-finished exchange so the next message starts from a valid history.
            while (history.size > mark) history.removeAt(history.size - 1)
            throw e
        }
    }

    private fun describe(name: String, args: JSONObject): String = when (name) {
        "search_notes" -> "Searching for \"${args.optString("query")}\""
        "list_folder" -> "Listing ${args.optString("path").ifBlank { "the vault" }}"
        "read_note" -> "Reading ${args.optString("path")}"
        else -> "Working"
    }

    private fun runTool(name: String, args: JSONObject): String = when (name) {
        "search_notes" -> {
            val limit = args.optInt("limit", 12).coerceIn(1, 25)
            val hits = index.search(args.optString("query"), limit, widen = true, prefixLast = false)
            if (hits.isEmpty()) "No notes matched. Try fewer or different keywords."
            else JSONArray(hits.map {
                JSONObject().put("path", it.path).put("title", it.title)
                    .put("snippet", it.snippet.replace(SearchIndex.MARK_START, "").replace(SearchIndex.MARK_END, ""))
            }).toString()
        }
        "list_folder" -> {
            val dir = vault.resolve(args.optString("path"))
            if (!dir.isDirectory) "Not a folder: ${args.optString("path")}"
            else {
                val entries = dir.listFiles().orEmpty()
                    .filter { !it.name.startsWith(".") }
                    .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                entries.take(MAX_LIST).joinToString("\n") { if (it.isDirectory) "${it.name}/" else it.name } +
                    if (entries.size > MAX_LIST) "\n… ${entries.size - MAX_LIST} more" else ""
            }
        }
        "read_note" -> {
            val path = args.optString("path")
            val file = vault.resolve(path)
            when {
                !file.isFile -> "No such file: $path"
                file.extension.lowercase() !in READABLE -> "Only text notes can be read."
                else -> {
                    val text = file.readText(Charsets.UTF_8)
                    if (text.length > MAX_NOTE_CHARS) text.take(MAX_NOTE_CHARS) + "\n\n[Truncated: note is ${text.length} characters long]"
                    else text
                }
            }
        }
        else -> "Unknown tool: $name"
    }

    private fun systemPrompt(): String {
        val root = vault.root
        val agents = root?.let { File(it, "AGENTS.md") }?.takeIf { it.isFile }?.readText(Charsets.UTF_8)?.take(8000)
        val folders = root?.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .map { it.name }.sorted().joinToString(", ")
        return buildString {
            append(agents ?: "You answer questions about the user's personal Obsidian vault.")
            append("\n\n---\n")
            append("You are running inside a phone app with read-only access to the vault. Today is ${LocalDate.now()}.\n")
            append("Top-level folders: $folders\n\n")
            append("How to work:\n")
            append("- Look things up with the tools before answering. Never answer about the vault from memory.\n")
            append("- search_notes is a keyword search: use one to three distinctive words and try variations.\n")
            append("- For totals or anything spanning many notes (for example every bill this year), list the folder and read each note.\n")
            append("- If the vault does not contain the answer, say so.\n")
            append("- Cite every source note as a [[wikilink]] using the filename without the .md extension.\n")
            append("- Keep answers short and plain. The screen is small and does not render tables.\n")
        }
    }

    companion object {
        private const val MAX_STEPS = 16
        private const val MAX_LIST = 300
        private const val MAX_NOTE_CHARS = 40_000
        private val READABLE = setOf("md", "txt", "base", "json", "canvas", "csv")

        private fun tool(name: String, description: String, props: JSONObject, required: List<String>) =
            JSONObject().put("type", "function").put(
                "function", JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put(
                        "parameters", JSONObject()
                            .put("type", "object")
                            .put("properties", props)
                            .put("required", JSONArray(required))
                    )
            )

        private fun str(description: String) = JSONObject().put("type", "string").put("description", description)

        private val TOOLS: JSONArray = JSONArray()
            .put(
                tool(
                    "search_notes",
                    "Keyword search across every note in the vault. Returns paths, titles and a matching snippet.",
                    JSONObject()
                        .put("query", str("One to three keywords. All must appear in a note for it to match."))
                        .put("limit", JSONObject().put("type", "integer").put("description", "Maximum results, default 12")),
                    listOf("query"),
                )
            )
            .put(
                tool(
                    "list_folder",
                    "Lists the files and subfolders in a vault folder.",
                    JSONObject().put("path", str("Folder path relative to the vault root, for example 20-Areas/finances. Empty for the root.")),
                    listOf("path"),
                )
            )
            .put(
                tool(
                    "read_note",
                    "Returns the full text of one note.",
                    JSONObject().put("path", str("File path relative to the vault root, including the .md extension.")),
                    listOf("path"),
                )
            )
    }
}
