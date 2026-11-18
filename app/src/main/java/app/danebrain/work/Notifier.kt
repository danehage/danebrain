package app.danebrain.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.danebrain.MainActivity
import app.danebrain.R

/** Tells the user when a queued capture has been read and is waiting for their review. */
object Notifier {
    private const val CHANNEL = "captures"

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** "Ready to review" for a capture the model has finished with. Tapping it opens that review. */
    fun ready(context: Context, captureId: Long, title: String) =
        post(context, captureId, "Ready to review", title.ifBlank { "A capture has been read." })

    /** For a capture the model could not read after its retries. */
    fun needsYou(context: Context, captureId: Long) =
        post(context, captureId, "Needs filing by hand", "A capture could not be read. Open it to file it yourself.")

    /** Clears every notification; called when the app comes to the front, where the queue is visible anyway. */
    fun clear(context: Context) = NotificationManagerCompat.from(context).cancelAll()

    private fun post(context: Context, captureId: Long, title: String, text: String) {
        if (!allowed(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Captures ready to review", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_REVIEW)
            .putExtra(MainActivity.EXTRA_CAPTURE_ID, captureId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context, captureId.toInt(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_scan)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(captureId.toInt(), notification)
        } catch (e: SecurityException) {
            // Permission was withdrawn between the check and the post; nothing to do.
        }
    }
}
