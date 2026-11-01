package com.gkeepdroid

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        SyncPolicy.markStart(applicationContext)
        // Picks up edits that other apps made to the Markdown files, whether or not a widget is placed.
        if (MarkdownSync.isEnabled(applicationContext)) {
            try { MarkdownSync.syncNow(applicationContext) } catch (_: Exception) {}
        }
        if (PendingEdits.all(applicationContext).isNotEmpty() && PendingEdits.flush(applicationContext)) {
            PendingEditsWorker.schedule(applicationContext)
        }
        // Nothing to update when no widget is placed, unless the app itself asked.
        if (!inputData.getBoolean(FORCE, false) && !WidgetRefresh.hasWidgets(applicationContext)) return Result.success()
        val ok = KeepRepository.syncAll(applicationContext)
        SyncPolicy.markResult(applicationContext, ok)
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC_NAME = "keep_sync"
        private const val ONCE_NAME = "keep_sync_now"
        private const val FORCE = "force"

        private fun constraints(wifiOnly: Boolean) = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        /** (Re)schedules the background sync from the saved settings; without it nothing runs while the display is off. */
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val settings = SettingsStore.loadGlobal(context)
            val minutes = if (settings.backgroundSync) settings.backgroundIntervalMin else 0
            if (minutes in 1..14) {
                // Too short for WorkManager: use an alarm chain instead.
                workManager.cancelUniqueWork(PERIODIC_NAME)
                SyncAlarm.schedule(context, minutes)
                return
            }
            SyncAlarm.cancel(context)
            if (minutes <= 0) {
                workManager.cancelUniqueWork(PERIODIC_NAME)
                return
            }
            // WorkManager enforces a minimum period of 15 minutes.
            val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints(settings.wifiOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /**
         * Syncs once now. A manual sync (the refresh button, "Sync now") works on any connection; an
         * automatic one obeys the Wi-Fi-only setting. [force] syncs even if no widget is placed.
         */
        fun syncNow(context: Context, manual: Boolean = true, force: Boolean = false) {
            val wifiOnly = !manual && SettingsStore.loadGlobal(context).wifiOnly
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints(wifiOnly))
                .setInputData(workDataOf(FORCE to force))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
