package app.danebrain.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.danebrain.App
import app.danebrain.data.CaptureRepo
import app.danebrain.net.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Works through pending captures whenever there is a network connection. */
class AnalyzeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as App
        if (!app.settings.canCallModel) return@withContext Result.success()
        val attemptsBefore = app.captures.list().associate { it.id to it.status }
        for (capture in app.captures.pending()) {
            try {
                val analysis = app.analyzer.analyze(capture)
                app.captures.setAnalysis(capture.id, analysis.toJson())
                if (!app.inForeground) Notifier.ready(app, capture.id, analysis.title)
            } catch (e: ApiException) {
                app.captures.recordFailure(capture.id, e.message ?: "Request failed", countAttempt = true)
            } catch (e: IOException) {
                // No connection or a timeout: not the capture's fault, so it does not use up an attempt.
                app.captures.recordFailure(capture.id, "Network problem: ${e.message}", countAttempt = false)
            } catch (e: Exception) {
                app.captures.recordFailure(capture.id, e.message ?: e.toString(), countAttempt = true)
            }
        }
        // A capture that has just used up its attempts will not be retried, so say so once.
        if (!app.inForeground) {
            val before = attemptsBefore
            app.captures.list()
                .filter { it.status == CaptureRepo.STATUS_FAILED && before[it.id] != null && before[it.id] != CaptureRepo.STATUS_FAILED }
                .forEach { Notifier.needsYou(app, it.id) }
        }
        if (app.captures.pending().isNotEmpty()) Result.retry() else Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<AnalyzeWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("analyze", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
