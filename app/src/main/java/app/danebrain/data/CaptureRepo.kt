package app.danebrain.data

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

data class Capture(
    val id: Long,
    /** "scan" or "text". */
    val kind: String,
    val createdAt: Long,
    val userText: String,
    /** Staged page images in app-private storage. */
    val pages: List<String>,
    val status: String,
    val attempts: Int,
    val analysis: String?,
    val error: String?,
)

/** The capture queue. A capture stays here until it is written to the vault or discarded. */
class CaptureRepo(private val db: Db, private val stagingDir: File) {

    private val _changes = MutableStateFlow(0L)
    /** Ticks whenever the queue changes, so screens can re-read it. */
    val changes: StateFlow<Long> = _changes

    private fun touch() {
        _changes.value = _changes.value + 1
    }

    fun newStagingFile(): File {
        stagingDir.mkdirs()
        return File(stagingDir, "page-${System.currentTimeMillis()}-${(1000..9999).random()}.jpg")
    }

    fun add(kind: String, userText: String, pages: List<File>): Long {
        val v = ContentValues().apply {
            put("kind", kind)
            put("created_at", System.currentTimeMillis())
            put("user_text", userText)
            put("pages", JSONArray(pages.map { it.absolutePath }).toString())
            put("status", STATUS_PENDING)
        }
        val id = db.writableDatabase.insertOrThrow("captures", null, v)
        touch()
        return id
    }

    fun list(): List<Capture> =
        db.readableDatabase.rawQuery("SELECT * FROM captures ORDER BY created_at DESC", null).use { c ->
            val out = ArrayList<Capture>()
            while (c.moveToNext()) out.add(read(c))
            out
        }

    fun pending(): List<Capture> = list().filter { it.status == STATUS_PENDING }.reversed()

    fun get(id: Long): Capture? =
        db.readableDatabase.rawQuery("SELECT * FROM captures WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) read(c) else null
        }

    fun setAnalysis(id: Long, analysisJson: String) {
        val v = ContentValues().apply {
            put("analysis", analysisJson)
            put("status", STATUS_READY)
            putNull("error")
        }
        db.writableDatabase.update("captures", v, "id = ?", arrayOf(id.toString()))
        touch()
    }

    /** Records a failed analysis. After [MAX_ATTEMPTS] counted failures the capture needs manual filing. */
    fun recordFailure(id: Long, error: String, countAttempt: Boolean) {
        val current = get(id) ?: return
        val attempts = current.attempts + if (countAttempt) 1 else 0
        val v = ContentValues().apply {
            put("attempts", attempts)
            put("error", error.take(500))
            put("status", if (attempts >= MAX_ATTEMPTS) STATUS_FAILED else STATUS_PENDING)
        }
        db.writableDatabase.update("captures", v, "id = ?", arrayOf(id.toString()))
        touch()
    }

    fun retry(id: Long) {
        val v = ContentValues().apply {
            put("attempts", 0)
            put("status", STATUS_PENDING)
            putNull("error")
        }
        db.writableDatabase.update("captures", v, "id = ?", arrayOf(id.toString()))
        touch()
    }

    /** Removes a capture and its staged images. Only ever touches app-private files. */
    fun delete(id: Long) {
        val c = get(id) ?: return
        c.pages.forEach { path ->
            val f = File(path)
            if (f.parentFile?.canonicalPath == stagingDir.canonicalPath) f.delete()
        }
        db.writableDatabase.delete("captures", "id = ?", arrayOf(id.toString()))
        touch()
    }

    private fun read(c: Cursor): Capture {
        val pagesJson = JSONArray(c.getString(c.getColumnIndexOrThrow("pages")))
        return Capture(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            kind = c.getString(c.getColumnIndexOrThrow("kind")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            userText = c.getString(c.getColumnIndexOrThrow("user_text")),
            pages = (0 until pagesJson.length()).map { pagesJson.getString(it) },
            status = c.getString(c.getColumnIndexOrThrow("status")),
            attempts = c.getInt(c.getColumnIndexOrThrow("attempts")),
            analysis = c.getString(c.getColumnIndexOrThrow("analysis")),
            error = c.getString(c.getColumnIndexOrThrow("error")),
        )
    }

    companion object {
        const val KIND_SCAN = "scan"
        const val KIND_TEXT = "text"
        const val STATUS_PENDING = "pending"
        const val STATUS_READY = "ready"
        const val STATUS_FAILED = "failed"
        const val MAX_ATTEMPTS = 3
    }
}
