package app.danebrain.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import app.danebrain.core.SyncLog
import java.io.File
import java.io.IOException

/** Read-side helpers for the vault folder. */
class Vault(private val settings: Settings) {

    val root: File? get() = settings.vaultPath.takeIf { it.isNotBlank() }?.let { File(it) }

    val name: String get() = root?.name.orEmpty()

    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    /** Null when everything is in place, otherwise what the user needs to fix. */
    fun problem(): String? {
        if (!hasAccess()) return "All-files access is off, so the vault cannot be read."
        val r = root ?: return "No vault folder chosen."
        if (!r.isDirectory) return "Vault folder not found: ${r.path}"
        if (!File(r, ".obsidian").isDirectory) return "That folder has no .obsidian folder, so it is not an Obsidian vault."
        return null
    }

    /** Folders in shared storage that contain a .obsidian folder, searched three levels deep. */
    fun detect(): List<File> {
        val base = Environment.getExternalStorageDirectory()
        val found = ArrayList<File>()
        fun visit(dir: File, depth: Int) {
            if (File(dir, ".obsidian").isDirectory) {
                found.add(dir)
                return
            }
            if (depth == 0) return
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory && !child.name.startsWith(".") && child.name != "Android") visit(child, depth - 1)
            }
        }
        visit(base, 3)
        return found
    }

    /**
     * When Obsidian on this phone was last in use, in epoch milliseconds, or 0 if unknown. Obsidian
     * rewrites files in its `.obsidian` folder (the mobile workspace, mostly) whenever it is open,
     * so the newest of them is a good stand-in. Read-only; the app never writes there.
     */
    fun obsidianLastOpen(): Long {
        val r = root ?: return 0
        return File(r, ".obsidian").listFiles()?.filter { it.isFile }?.maxOfOrNull { it.lastModified() } ?: 0
    }

    /** Notes this app wrote since Obsidian was last open, which Obsidian Sync cannot have sent yet. */
    fun waitingForSync(): List<String> {
        val lastOpen = obsidianLastOpen()
        val pruned = SyncLog.prune(settings.syncLog, lastOpen)
        if (pruned != settings.syncLog) settings.syncLog = pruned
        return SyncLog.waiting(pruned, lastOpen)
    }

    fun recordWrite(notePath: String) {
        settings.syncLog = SyncLog.add(settings.syncLog, notePath, System.currentTimeMillis())
    }

    /** Brings Obsidian to the front on this vault, which is what lets Obsidian Sync run. */
    fun openObsidian(context: Context) {
        val uri = Uri.parse("obsidian://open?vault=${Uri.encode(name)}")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Obsidian is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    /** Resolves a vault-relative path, refusing anything that escapes the vault. */
    fun resolve(relative: String): File {
        val r = root ?: throw IOException("No vault folder chosen")
        val f = File(r, relative.trim().trimStart('/'))
        val rootPath = r.canonicalPath
        val path = f.canonicalPath
        if (path != rootPath && !path.startsWith(rootPath + File.separator)) throw IOException("Path is outside the vault")
        return f
    }

    /** Opens a note in Obsidian by vault-relative path or by bare note name. */
    fun openInObsidian(context: Context, pathOrName: String) {
        val file = pathOrName.removeSuffix(".md")
        val uri = Uri.parse("obsidian://open?vault=${Uri.encode(name)}&file=${Uri.encode(file)}")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Obsidian is not installed", Toast.LENGTH_SHORT).show()
        }
    }
}
