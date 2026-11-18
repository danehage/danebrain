package app.danebrain

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import app.danebrain.chat.ChatEngine
import app.danebrain.core.NoteDraft
import app.danebrain.core.Route
import app.danebrain.core.Routes
import app.danebrain.core.VaultWriter
import app.danebrain.data.CaptureRepo
import app.danebrain.data.Db
import app.danebrain.data.SearchIndex
import app.danebrain.data.Settings
import app.danebrain.data.Vault
import app.danebrain.net.OpenRouter
import app.danebrain.work.Analyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class Tab(val label: String, val icon: ImageVector) {
    Capture("Capture", Icons.Filled.Edit),
    Search("Search", Icons.Filled.Search),
    Chat("Ask", Icons.AutoMirrored.Filled.Send),
    Settings("Settings", Icons.Filled.Settings),
}

data class ChatMsg(val fromUser: Boolean, val text: String, val isError: Boolean = false)

/** Screen state that should survive tab switches and configuration changes. */
class UiState(initialDraft: String, initialTheme: String) {
    /** Light, dark or follow the phone; see [Settings.themeMode]. */
    var themeMode by mutableStateOf(initialTheme)
    var tab by mutableStateOf(Tab.Capture)
    var reviewId by mutableStateOf<Long?>(null)
    var draft by mutableStateOf(initialDraft)
    /** True while the full-page draft editor is open. */
    var writing by mutableStateOf(false)
    var scanRequested by mutableStateOf(false)
    val chat = mutableStateListOf<ChatMsg>()
    var chatBusy by mutableStateOf(false)
    var chatStatus by mutableStateOf("")
    var indexStatus by mutableStateOf("")
    var indexing by mutableStateOf(false)
    /** Bumped each time the activity resumes, so screens re-check permissions and the vault. */
    var resumeTick by mutableStateOf(0)
    /** Bumped after each note is written, so the sync reminder recounts. */
    var syncTick by mutableStateOf(0)
}

class App : Application() {
    lateinit var settings: Settings
    lateinit var captures: CaptureRepo
    lateinit var index: SearchIndex
    lateinit var vault: Vault
    /** The filing destinations in use: the vault's own table when it has one, else the bundled sample. */
    var routes by mutableStateOf(emptyList<Route>())
        private set
    /** One line for Settings saying where [routes] came from. */
    var routesSource by mutableStateOf("")
        private set
    lateinit var analyzer: Analyzer
    lateinit var chatEngine: ChatEngine
    lateinit var ui: UiState

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        val db = Db(this)
        captures = CaptureRepo(db, File(filesDir, "staging"))
        index = SearchIndex(db)
        vault = Vault(settings)
        loadRoutes()
        val api = OpenRouter { settings.apiKey }
        analyzer = Analyzer(api, { routes }) { settings.visionModel }
        chatEngine = ChatEngine(api, settings, vault, index)
        ui = UiState(settings.draft, settings.themeMode)
    }

    /**
     * Loads the route table. A `danebrain-routes.json` at the top of the vault replaces the bundled
     * sample, so the folders someone files into are their own data, not part of the app.
     */
    fun loadRoutes() {
        val bundled = Routes.parse(assets.open("routes.json").bufferedReader().use { it.readText() })
        val file = vault.root?.takeIf { vault.problem() == null }?.let { File(it, ROUTES_FILE) }
        if (file == null || !file.isFile) {
            routes = bundled
            routesSource = "Built-in sample (${bundled.size} routes). Add $ROUTES_FILE to the vault to use your own."
            return
        }
        try {
            val own = Routes.parse(file.readText())
            routes = own
            routesSource = "$ROUTES_FILE in the vault (${own.size} routes)"
        } catch (e: Exception) {
            routes = bundled
            routesSource = "$ROUTES_FILE could not be read (${e.message}). Using the built-in sample."
        }
    }

    /** Re-reads the vault into the search index in the background. */
    fun refreshIndex() {
        if (ui.indexing) return
        val root = vault.root
        if (vault.problem() != null || root == null) {
            ui.indexStatus = vault.problem() ?: "No vault folder chosen."
            return
        }
        ui.indexing = true
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { index.refresh(root) }
                ui.indexStatus = "${r.total} notes indexed"
            } catch (e: Exception) {
                ui.indexStatus = "Indexing failed: ${e.message}"
            } finally {
                ui.indexing = false
            }
        }
    }

    companion object {
        const val ROUTES_FILE = "danebrain-routes.json"
    }

    /** Writes a reviewed note and its page images into the vault. Call off the main thread. */
    fun writeToVault(draft: NoteDraft, pages: List<File>): VaultWriter.Result {
        vault.problem()?.let { throw IOException(it) }
        val root = vault.root ?: throw IOException("No vault folder chosen")
        val result = VaultWriter(root).write(draft, pages) { stem -> index.stemExists(stem) }
        vault.recordWrite(result.notePath)
        ui.syncTick++
        return result
    }
}
