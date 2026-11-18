package app.danebrain.core

import java.io.File
import java.io.IOException

/**
 * Creates new files in the vault. It never overwrites, edits, moves or deletes anything that was
 * there before the call; on failure it removes only the files this call created.
 */
class VaultWriter(private val vaultRoot: File, private val scansRoot: String = NoteBuilder.IMAGE_ROOT) {

    data class Result(val notePath: String, val imagePaths: List<String>)

    /**
     * Writes the page images, then the note, so the note never points at a missing image.
     * [pages] are staged JPEGs outside the vault. Returns vault-relative paths.
     */
    fun write(draft: NoteDraft, pages: List<File>, noteExists: (String) -> Boolean = { false }): Result {
        if (!vaultRoot.isDirectory) throw IOException("Vault folder not found: ${vaultRoot.path}")
        val noteDir = resolve(draft.route.folder)
        val imageRel = NoteBuilder.imageFolder(draft.date, scansRoot)
        val imageDir = resolve(imageRel)
        val stem = freeStem(NoteBuilder.stem(draft), noteDir, imageDir, pages.size)

        val created = ArrayList<File>()
        try {
            val imagePaths = ArrayList<String>()
            if (pages.isNotEmpty()) mkdirs(imageDir)
            pages.forEachIndexed { i, src ->
                val name = NoteBuilder.imageName(stem, i + 1)
                val dest = File(imageDir, name)
                copyNew(src, dest)
                created.add(dest)
                imagePaths.add("$imageRel/$name")
            }
            mkdirs(noteDir)
            val note = File(noteDir, "$stem.md")
            val text = NoteBuilder.render(draft.copy(pageCount = pages.size), stem, noteExists)
            writeNew(note, text.toByteArray(Charsets.UTF_8))
            created.add(note)
            return Result("${draft.route.folder}/$stem.md", imagePaths)
        } catch (e: Exception) {
            created.forEach { it.delete() }
            throw if (e is IOException) e else IOException(e.message ?: e.toString(), e)
        }
    }

    /** A vault-relative folder as a File, refusing anything that escapes the vault. */
    private fun resolve(relative: String): File {
        val dir = File(vaultRoot, relative)
        val rootPath = vaultRoot.canonicalPath
        val dirPath = dir.canonicalPath
        if (dirPath != rootPath && !dirPath.startsWith(rootPath + File.separator)) {
            throw IOException("Path leaves the vault: $relative")
        }
        return dir
    }

    /** First of stem, stem-2, stem-3... whose note and image names are all unused. */
    private fun freeStem(base: String, noteDir: File, imageDir: File, pageCount: Int): String {
        var n = 1
        while (true) {
            val stem = if (n == 1) base else "$base-$n"
            val noteTaken = File(noteDir, "$stem.md").exists()
            val imageTaken = (1..pageCount).any { File(imageDir, NoteBuilder.imageName(stem, it)).exists() }
            if (!noteTaken && !imageTaken) return stem
            n++
            if (n > 500) throw IOException("No free filename for $base")
        }
    }

    private fun mkdirs(dir: File) {
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
            throw IOException("Could not create folder: ${dir.path}")
        }
    }

    private fun copyNew(src: File, dest: File) = writeNew(dest, src.readBytes())

    /** Writes through a dot-prefixed temp file (Obsidian ignores dotfiles), then renames. */
    private fun writeNew(dest: File, bytes: ByteArray) {
        if (dest.exists()) throw IOException("File already exists: ${dest.name}")
        val tmp = File(dest.parentFile, ".${dest.name}.tmp")
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(dest)) throw IOException("Could not write ${dest.name}")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
