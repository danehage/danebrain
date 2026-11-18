package app.danebrain.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.danebrain.App
import app.danebrain.work.Notifier

/**
 * Returns a function to call when something has been queued for the model. The first time, it asks
 * for permission to notify when the capture is ready; after that it does nothing.
 */
@Composable
fun rememberNotificationAsk(app: App): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= 33 && !app.settings.askedToNotify && !Notifier.allowed(context)) {
            app.settings.askedToNotify = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
