package app.danebrain.core

import org.json.JSONObject

/** Works out where things go in a vault the app has never seen, from what is already there. */
object VaultLayout {
    const val DEFAULT_INBOX = "Inbox"
    const val DEFAULT_ATTACHMENTS = "Attachments"

    /**
     * The folder scans are kept under. Obsidian's own attachment folder wins when it names a fixed
     * vault folder; otherwise an existing top-level "attachments" folder; otherwise a new one.
     * [obsidianAppJson] is the text of `.obsidian/app.json`, if it could be read.
     */
    fun attachmentRoot(obsidianAppJson: String?, topFolders: List<String>): String {
        val configured = try {
            obsidianAppJson?.let { JSONObject(it).optString("attachmentFolderPath") }.orEmpty().trim()
        } catch (e: Exception) {
            ""
        }
        // "/" is the vault root and "./..." is relative to each note; neither names one folder.
        val fixed = configured.trim('/')
        if (fixed.isNotEmpty() && !configured.startsWith("./") && !configured.startsWith(".") && !fixed.contains("..")) {
            return fixed
        }
        return topFolders.firstOrNull { it.contains("attachment", ignoreCase = true) } ?: DEFAULT_ATTACHMENTS
    }

    /** An existing top-level folder that looks like an inbox, if there is one. */
    fun guessInbox(topFolders: List<String>): String? =
        topFolders.firstOrNull { it.contains("inbox", ignoreCase = true) }

    /**
     * A first route table for a vault: the inbox plus one route per top-level folder. Folders that
     * are not places to file new notes (attachments, templates, archive, daily notes) are left out.
     * The "when" sentences are placeholders for the owner to rewrite.
     */
    fun starterRoutesJson(inboxFolder: String, topFolders: List<String>): String {
        val skip = listOf("attachment", "template", "archive", "daily")
        val lines = ArrayList<String>()
        lines.add(
            "  \"inbox\": { \"folder\": ${JSONObject.quote(inboxFolder)}, \"type\": \"note\", \"status\": \"inbox\", " +
                "\"datePrefix\": true, \"when\": \"quick thoughts, and anything that does not clearly fit another route\" }"
        )
        val used = hashSetOf("inbox")
        for (folder in topFolders) {
            if (folder == inboxFolder || skip.any { folder.contains(it, ignoreCase = true) }) continue
            val plain = folder.replace(Regex("^[0-9]+[-_. ]*"), "").ifBlank { folder }
            val key = Text.slugify(plain)
            if (key.isEmpty() || !used.add(key)) continue
            lines.add(
                "  ${JSONObject.quote(key)}: { \"folder\": ${JSONObject.quote(folder)}, \"type\": \"note\", \"status\": \"active\", " +
                    "\"when\": ${JSONObject.quote("notes that belong in $plain (rewrite this to say exactly what goes here)")} }"
            )
        }
        return "{\n" + lines.joinToString(",\n") + "\n}\n"
    }
}
