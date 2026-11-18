package app.danebrain.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.danebrain.App
import app.danebrain.R
import app.danebrain.Tab

@Composable
fun Root(app: App) {
    val ui = app.ui
    val context = LocalContext.current
    DaneBrainTheme(ui.themeMode) {
        // Dictation lives here, above the tabs, so the mic works from every screen.
        // voiceBase: the draft before the phrase now being spoken. shown: what dictation last put on screen.
        val voiceBase = remember { arrayOf("") }
        val shown = remember { arrayOf("") }
        val dictation = remember {
            Dictation(
                context = context,
                onPartial = {
                    ui.draft = Dictation.join(voiceBase[0], it)
                    shown[0] = ui.draft
                },
                onFinal = {
                    // The last phrase can land just after stop; leave the draft alone if it was edited by hand since.
                    if (ui.draft == shown[0]) {
                        ui.draft = Dictation.join(voiceBase[0], it)
                        voiceBase[0] = ui.draft
                        shown[0] = ui.draft
                        app.settings.draft = ui.draft
                    }
                },
                onProblem = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
            )
        }
        DisposableEffect(Unit) {
            val lifecycle = (context as? ComponentActivity)?.lifecycle
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) dictation.stop()
            }
            lifecycle?.addObserver(observer)
            onDispose {
                lifecycle?.removeObserver(observer)
                dictation.release()
            }
        }

        fun startDictation() {
            voiceBase[0] = ui.draft
            shown[0] = ui.draft
            dictation.start()
        }

        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startDictation()
            else Toast.makeText(context, "Dictation needs the microphone permission", Toast.LENGTH_SHORT).show()
        }

        val toggleDictation: () -> Unit = {
            when {
                dictation.listening -> dictation.stop()
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED -> startDictation()
                else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // When dictation ends, land on the writing page so the words can be checked and saved.
        var wasListening by remember { mutableStateOf(false) }
        LaunchedEffect(dictation.listening) {
            if (wasListening && !dictation.listening && ui.draft.isNotBlank()) {
                ui.reviewId = null
                ui.tab = Tab.Capture
                ui.writing = true
            }
            wasListening = dictation.listening
        }

        val reviewId = ui.reviewId
        val listening = dictation.listening
        // Setup can be left with Back only once it has been finished at least once.
        BackHandler(enabled = ui.setup && app.settings.setupDone) { ui.setup = false }
        BackHandler(enabled = !ui.setup && (listening || reviewId != null || ui.writing || ui.tab != Tab.Capture)) {
            when {
                dictation.listening -> dictation.stop()
                ui.reviewId != null -> ui.reviewId = null
                ui.writing -> ui.writing = false
                else -> ui.tab = Tab.Capture
            }
        }
        Scaffold(
            // The whole frame, tab bar included, sits above the keyboard, so tabs stay reachable while typing.
            modifier = Modifier.imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!ui.setup && !listening && reviewId == null && !ui.writing) {
                    BottomBar(selected = ui.tab, onTab = { ui.tab = it }, onMic = toggleDictation)
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
            ) {
                when {
                    ui.setup -> SetupScreen(app)
                    listening -> ListeningScreen(dictation, text = ui.draft, onStop = { dictation.stop() })
                    reviewId != null -> ReviewScreen(app, reviewId)
                    ui.writing -> WriteScreen(app, onDictate = toggleDictation)
                    else -> when (ui.tab) {
                        Tab.Capture -> CaptureScreen(app, onSpeak = toggleDictation)
                        Tab.Search -> SearchScreen(app)
                        Tab.Chat -> ChatScreen(app)
                        Tab.Settings -> SettingsScreen(app)
                    }
                }
            }
        }
    }
}

/** Text tabs with the mic in the middle, so dictation is one tap away on every tab. */
@Composable
private fun BottomBar(selected: Tab, onTab: (Tab) -> Unit, onMic: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(68.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tabs = Tab.entries
                val half = tabs.size / 2
                tabs.take(half).forEach { NavLabel(it, it == selected, onTab, Modifier.weight(1f)) }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Palette.Teal)
                        .clickable(role = Role.Button, onClickLabel = "Dictate a note", onClick = onMic),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = "Dictate", tint = Color.White)
                }
                tabs.drop(half).forEach { NavLabel(it, it == selected, onTab, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun NavLabel(tab: Tab, selected: Boolean, onTab: (Tab) -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxHeight().clickable(role = Role.Tab) { onTab(tab) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spacer(Modifier.height(8.dp))
            Text(
                tab.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.size(4.dp).background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape,
                )
            )
        }
    }
}
