package app.danebrain

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.danebrain.data.CaptureRepo
import app.danebrain.data.ImageUtil
import app.danebrain.ui.Root
import app.danebrain.work.AnalyzeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private val app get() = application as App

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent { Root(app) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        app.ui.resumeTick++
        app.loadRoutes()
        app.refreshIndex()
        if (app.captures.pending().isNotEmpty()) AnalyzeWorker.enqueue(this)
    }

    override fun onPause() {
        super.onPause()
        app.settings.draft = app.ui.draft
    }

    private fun handleIntent(intent: Intent?) {
        val ui = app.ui
        when (intent?.action) {
            ACTION_SCAN -> {
                ui.viewing.clear()
                ui.writing = false
                ui.reviewId = null
                ui.tab = Tab.Capture
                ui.scanRequested = true
            }
            ACTION_SEARCH -> {
                ui.viewing.clear()
                ui.writing = false
                ui.reviewId = null
                ui.tab = Tab.Search
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                ui.reviewId = null
                ui.tab = Tab.Capture
                if (intent.type?.startsWith("image/") == true) {
                    importSharedImages(sharedUris(intent))
                } else {
                    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().trim()
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
                    val shared = listOf(subject, text).filter { it.isNotEmpty() }.distinct().joinToString("\n")
                    if (shared.isNotEmpty()) {
                        ui.draft = if (ui.draft.isBlank()) shared else ui.draft.trimEnd() + "\n\n" + shared
                        app.settings.draft = ui.draft
                        ui.writing = true
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun sharedUris(intent: Intent): List<Uri> =
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        } else {
            listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        }

    /** Shared images become one scan capture, staged in app storage and queued for analysis. */
    private fun importSharedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        app.scope.launch {
            val files = ArrayList<File>()
            try {
                withContext(Dispatchers.IO) {
                    for (uri in uris) {
                        val dest = app.captures.newStagingFile()
                        ImageUtil.importJpeg(this@MainActivity, uri, dest)
                        files.add(dest)
                    }
                }
                app.captures.add(CaptureRepo.KIND_SCAN, "", files)
                AnalyzeWorker.enqueue(this@MainActivity)
                Toast.makeText(this@MainActivity, "Added to the queue", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                files.forEach { it.delete() }
                Toast.makeText(this@MainActivity, "Could not import: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val ACTION_SCAN = "app.danebrain.action.SCAN"
        const val ACTION_SEARCH = "app.danebrain.action.SEARCH"
    }
}
