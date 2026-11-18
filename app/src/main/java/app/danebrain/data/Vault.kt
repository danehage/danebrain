package app.danebrain.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import android.provider.DocumentsContract
import app.danebrain.core.Routes
import app.danebrain.core.SyncLog
import app.danebrain.core.VaultLayout
import java.io.File
import java.io.IOException

/** Read-side helpers for the vault folder. */
class Vault(private val settings: Settings) {

    val root: File? get() = settings.vaultPath.takeIf { it.isNotBlank() }?.let { File(it) }

    val name: String get() = root?.name.orEmpty()

    /** True when Obsidian has been used on this folder. The app works without it; a few extras depend on it. */
    val isObsidianVault: Boolean get() = root?.let { File(it, ".obsidian").isDirectory } == true

    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    /** Null when everything is in place, otherwise what the user needs to fix. */
    fun problem(): String? {
        if (!hasAccess()) return "All-files access is off, so the vault cannot be read."
        val r = root ?: return "No vault folder chosen."
        if (!r.isDirectory) return "Vault folder not found: ${r.path}"
        return null
    }

    /** Obsidian vaults in shared storage (folders with a .obsidian folder), searched three levels deep. Other note folders are chosen by browsing. */
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

    /** Visible top-level folders of the vault, sorted by name. */
    fun topFolders(): List<String> =
        root?.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }.map { it.name }.sorted()

    /** Folder scans are kept under: Obsidian's attachment folder, or the nearest thing the vault has. */
    fun attachmentRoot(): String {
        val appJson = root?.let { File(it, ".obsidian/app.json") }?.takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
        return VaultLayout.attachmentRoot(appJson, topFolders())
    }

    val routesFile: File? get() = root?.let { File(it, ROUTES_FILE) }

    /**
     * Writes a first routes file built from the vault's own folders. Create-only: it refuses if
     * the file is already there. Returns the number of routes written.
     */
    fun createStarterRoutes(): Int {
        val file = routesFile ?: throw IOException("No vault folder chosen")
        if (file.exists()) throw IOException("$ROUTES_FILE already exists")
        val json = VaultLayout.starterRoutesJson(settings.inboxFolder.ifBlank { VaultLayout.DEFAULT_INBOX }, topFolders())
        val count = Routes.parse(json).size
        if (!file.createNewFile()) throw IOException("Could not create $ROUTES_FILE")
        file.writeText(json)
        return count
    }

    /** Turns a folder chosen in the system picker into a plain path, or null if it is not on device storage. */
    fun pathFromTreeUri(uri: Uri): String? {
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val volume = id.substringBefore(':', "")
        val relative = id.substringAfter(':', "")
        val base = if (volume.equals("primary", ignoreCase = true)) Environment.getExternalStorageDirectory() else File("/storage/$volume")
        if (volume.isEmpty() || !base.isDirectory) return null
        return if (relative.isEmpty()) base.path else File(base, relative).path
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

    /** Notes this app wrote since Obsidian was last open, which Obsidian Sync cannot have sent yet. Empty for a folder Obsidian does not use. */
    fun waitingForSync(): List<String> {
        // Without Obsidian there is no way to tell what has synced, so there is nothing to remind about.
        if (!isObsidianVault) return emptyList()
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

    /**
     * Finds an embedded file by the name used in `![[name]]`, the way Obsidian does: a path if one
     * was given, otherwise the first file with that name anywhere in the vault. Call off the main thread.
     */
    fun findAttachment(name: String): File? {
        val r = root ?: return null
        if (name.contains('/')) runCatching { resolve(name) }.getOrNull()?.takeIf { it.isFile }?.let { return it }
        val wanted = name.substringAfterLast('/')
        // Scans live under the attachment folder, so look there before walking everything.
        val starts = listOf(File(r, attachmentRoot()), r)
        for (start in starts) {
            start.walkTopDown().onEnter { !it.name.startsWith(".") }.firstOrNull { it.isFile && it.name == wanted }?.let { return it }
        }
        return null
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

    companion object {
        /** A route table in the vault with this name replaces the single-inbox default. */
        const val ROUTES_FILE = "danebrain-routes.json"
    }
}
