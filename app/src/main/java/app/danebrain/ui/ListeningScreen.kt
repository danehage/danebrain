package app.danebrain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danebrain.R
import kotlinx.coroutines.delay

private const val BARS = 32

/** Full-screen dictation: the words so far, a live level meter, and one big stop button. */
@Composable
fun ListeningScreen(dictation: Dictation, text: String, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }

    // A rolling window of recent loudness, newest on the right.
    val levels = remember { mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } } }
    LaunchedEffect(Unit) {
        while (true) {
            delay(70)
            levels.removeAt(0)
            levels.add(dictation.level)
        }
    }

    val scroll = rememberScrollState()
    LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LISTENING",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.5.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "%d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Text(
                text.ifBlank { "Start talking…" },
                style = MaterialTheme.typography.headlineSmall,
                color = if (text.isBlank()) colors.onSurfaceVariant else colors.onBackground,
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.height(60.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            levels.forEach { level ->
                Box(
                    Modifier
                        .width(4.dp)
                        .height((4 + 56 * level).dp)
                        .background(colors.primary, RoundedCornerShape(2.dp))
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        val ring = (6 + 10 * dictation.level).dp
        Box(
            modifier = Modifier
                .size(112.dp)
                .border(ring, Palette.Teal.copy(alpha = 0.22f), CircleShape)
                .padding(16.dp)
                .clip(CircleShape)
                .background(Palette.Teal)
                .clickable(role = Role.Button, onClickLabel = "Stop dictation", onClick = onStop),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_stop),
                contentDescription = "Stop dictation",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Tap to stop", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}
