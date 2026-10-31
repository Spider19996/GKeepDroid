package com.gkeepdroid

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PendingToggle(
    val account: String,
    val noteId: String,
    val itemId: String,
    val checked: Boolean,
    /** Delete the item instead of ticking it. */
    val delete: Boolean = false,
)

/** Checklist ticks that are shown in the widget but not sent to Keep yet. */
object PendingToggles {
    private fun prefs(context: Context) = context.getSharedPreferences("pending_toggles", Context.MODE_PRIVATE)

    @Synchronized
    private fun all(context: Context): MutableList<PendingToggle> {
        val raw = prefs(context).getString("ops", null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                PendingToggle(
                    o.getString("account"), o.getString("note"), o.getString("item"),
                    o.getBoolean("checked"), o.optBoolean("delete"),
                )
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun save(context: Context, ops: List<PendingToggle>) {
        val array = JSONArray()
        ops.forEach {
            array.put(
                JSONObject().put("account", it.account).put("note", it.noteId).put("item", it.itemId)
                    .put("checked", it.checked).put("delete", it.delete)
            )
        }
        prefs(context).edit().putString("ops", array.toString()).apply()
    }

    /** Queues a tick; ticking the same item again replaces the earlier one. */
    @Synchronized
    fun add(context: Context, op: PendingToggle) {
        val ops = all(context)
        ops.removeAll { it.account == op.account && it.itemId == op.itemId }
        ops.add(op)
        save(context, ops)
    }

    @Synchronized
    fun list(context: Context, account: String): List<PendingToggle> = all(context).filter { it.account == account }

    /** Removes sent ticks, but keeps one that the user changed again while it was being sent. */
    @Synchronized
    fun remove(context: Context, sent: List<PendingToggle>) {
        val ops = all(context)
        ops.removeAll { op -> sent.any { it == op } }
        save(context, ops)
    }
}

/**
 * Sends queued checklist ticks to Keep a few seconds after the last tick, so ticking several
 * items causes one sync. It runs as work instead of inside the widget's click callback, which may
 * be cut short, and is retried when the network is missing.
 */
class ToggleItemWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val account = inputData.getString(KEY_ACCOUNT) ?: return Result.failure()
        val ops = PendingToggles.list(applicationContext, account)
        if (ops.isEmpty()) return Result.success()
        return try {
            KeepRepository.sendToggles(applicationContext, account, ops)
            PendingToggles.remove(applicationContext, ops)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                // Give up and show what Keep really has instead of the unsent changes.
                PendingToggles.remove(applicationContext, ops)
                try { KeepRepository.sync(applicationContext, account) } catch (_: Exception) {}
                Result.failure()
            }
        }
    }

    companion object {
        private const val KEY_ACCOUNT = "account"
        private const val MAX_ATTEMPTS = 3
        private const val DELAY_SECONDS = 4L

        /** (Re)starts the countdown; every new tick postpones the sync by a few seconds. */
        fun schedule(context: Context, account: String) {
            val request = OneTimeWorkRequestBuilder<ToggleItemWorker>()
                .setInitialDelay(DELAY_SECONDS, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_ACCOUNT to account))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("toggle_$account", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
