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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** A change that could not be sent to Keep yet, stored as the Python call that will send it. */
data class PendingEdit(
    val account: String,
    val function: String,
    val args: List<Any>,
    val attempts: Int = 0,
    val id: String = UUID.randomUUID().toString(),
)

/** Edits made without a connection, kept on the device until they can be sent. */
object PendingEdits {
    private fun prefs(context: Context) = context.getSharedPreferences("pending_edits", Context.MODE_PRIVATE)

    @Synchronized
    fun all(context: Context): List<PendingEdit> {
        val raw = prefs(context).getString("edits", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                val args = o.getJSONArray("args")
                PendingEdit(
                    account = o.getString("account"),
                    function = o.getString("function"),
                    args = (0 until args.length()).map { i -> args.get(i) },
                    attempts = o.optInt("attempts"),
                    id = o.getString("id"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun save(context: Context, edits: List<PendingEdit>) {
        val array = JSONArray()
        edits.forEach { edit ->
            array.put(
                JSONObject()
                    .put("id", edit.id).put("account", edit.account).put("function", edit.function)
                    .put("args", JSONArray().apply { edit.args.forEach { put(it) } })
                    .put("attempts", edit.attempts)
            )
        }
        prefs(context).edit().putString("edits", array.toString()).apply()
    }

    @Synchronized
    fun add(context: Context, edit: PendingEdit) = save(context, all(context) + edit)

    @Synchronized
    fun remove(context: Context, id: String) = save(context, all(context).filter { it.id != id })

    @Synchronized
    fun countAttempt(context: Context, id: String) =
        save(context, all(context).map { if (it.id == id) it.copy(attempts = it.attempts + 1) else it })

    /** True when [e] looks like a missing connection rather than a problem with the change itself. */
    fun isOffline(e: Exception): Boolean = Regex(
        "ConnectionError|Max retries|timed out|Timeout|resolve|unreachable|SSLError|ConnectTimeout",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(e.message.orEmpty())
}

/** Sends the stored edits once there is a connection; an edit that keeps failing is given up. */
class PendingEditsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        var retry = false
        PendingEdits.all(applicationContext).forEach { edit ->
            try {
                KeepRepository.run(applicationContext, edit.account, edit.function, edit.args)
                PendingEdits.remove(applicationContext, edit.id)
            } catch (e: Exception) {
                if (edit.attempts + 1 >= MAX_ATTEMPTS) {
                    PendingEdits.remove(applicationContext, edit.id)
                } else {
                    PendingEdits.countAttempt(applicationContext, edit.id)
                    retry = true
                }
            }
        }
        return if (retry) Result.retry() else Result.success()
    }

    companion object {
        private const val MAX_ATTEMPTS = 6

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<PendingEditsWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("pending_edits", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
