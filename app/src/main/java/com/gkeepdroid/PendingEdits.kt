package com.gkeepdroid

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** A change that could not be sent to Keep yet, stored as the Python call that will send it. */
data class PendingEdit(
    val account: String,
    val function: String,
    val args: List<Any>,
    /** Why the last attempt failed, empty while nothing went wrong besides a missing connection. */
    val error: String = "",
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
                    error = o.optString("error"),
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
                    .put("error", edit.error)
            )
        }
        prefs(context).edit().putString("edits", array.toString()).apply()
    }

    @Synchronized
    fun add(context: Context, edit: PendingEdit) = save(context, all(context) + edit)

    @Synchronized
    fun remove(context: Context, id: String) = save(context, all(context).filter { it.id != id })

    @Synchronized
    fun recordFailure(context: Context, id: String, error: String) =
        save(context, all(context).map { if (it.id == id) it.copy(error = error) else it })

    // Only one run at a time sends the stored edits, so none is sent twice.
    private val flushLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Sends the stored edits in the background right away; what cannot be sent now is retried by the worker. */
    fun sendNow(context: Context) {
        val appContext = context.applicationContext
        scope.launch { if (flush(appContext)) PendingEditsWorker.schedule(appContext) }
    }

    /**
     * Sends the stored edits in order. An edit is only ever removed once Keep has it (or the user discards
     * it), so a failure keeps it and tells the user. Returns true when something is left to try again.
     */
    suspend fun flush(context: Context): Boolean = flushLock.withLock {
        var retry = false
        // Later edits of a note wait for an earlier one that failed, so they never overtake it.
        val blocked = mutableSetOf<String>()
        for (edit in all(context)) {
            val key = blockKey(edit)
            if (key != null && key in blocked) {
                retry = true
                continue
            }
            try {
                KeepRepository.run(context, edit.account, edit.function, edit.args, edit.id)
                // Not cancellable: an edit that Keep already has must not stay stored and be sent twice.
                withContext(NonCancellable) {
                    remove(context, edit.id)
                    SaveFailures.clear(context, edit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                retry = true
                // Without a connection nothing else can be sent either, and nothing is wrong with the edit.
                if (isOffline(e)) return@withLock true
                if (key != null) blocked += key
                val reason = e.message?.lineSequence()?.firstOrNull()?.take(160) ?: e.javaClass.simpleName
                withContext(NonCancellable) {
                    recordFailure(context, edit.id, reason)
                    // The same problem is not announced again at every retry.
                    if (edit.error != reason) SaveFailures.show(context, edit, reason)
                }
            }
        }
        retry
    }

    /** Edits of the same note keep their order; a new note has nothing to wait for. */
    private fun blockKey(edit: PendingEdit): String? =
        if (edit.function == "create_note") null else edit.account + "|" + edit.args.firstOrNull()

    /** Gives up on an edit because the user asked to: the cache goes back to what Keep has. */
    fun discard(context: Context, id: String) {
        val appContext = context.applicationContext
        val edit = all(appContext).firstOrNull { it.id == id } ?: return
        remove(appContext, id)
        SaveFailures.clear(appContext, edit)
        scope.launch { runCatching { KeepRepository.sync(appContext, edit.account) } }
    }

    /** True when [e] looks like a missing connection rather than a problem with the change itself. */
    fun isOffline(e: Exception): Boolean = Regex(
        "ConnectionError|Max retries|timed out|Timeout|resolve|unreachable|SSLError|ConnectTimeout",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(e.message.orEmpty())
}

/** Sends the stored edits once there is a connection; an edit that keeps failing stays until the user discards it. */
class PendingEditsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        if (PendingEdits.flush(applicationContext)) Result.retry() else Result.success()

    companion object {
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<PendingEditsWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("pending_edits", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** The notification for a change that could not be saved: it stays until the change is sent or discarded. */
object SaveFailures {
    private const val CHANNEL = "save_failures"
    internal const val EXTRA_EDIT = "edit"

    private fun notificationId(edit: PendingEdit) = edit.id.hashCode()

    /** What the user knows the change by: the title of the note it belongs to. */
    private fun title(context: Context, edit: PendingEdit): String {
        val given = when (edit.function) {
            "update_note" -> edit.args.getOrNull(1)
            "create_note" -> edit.args.getOrNull(0)
            else -> null
        }?.toString().orEmpty()
        return given.ifBlank { context.getString(R.string.untitled_note) }
    }

    fun show(context: Context, edit: PendingEdit, reason: String) {
        val text = context.getString(R.string.save_pending_failed, title(context, edit), reason)
        if (!Reminders.canNotify(context)) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            }
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.save_failures_title), NotificationManager.IMPORTANCE_DEFAULT)
        )
        fun action(receiver: Class<*>, name: String) = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, receiver)
                .setData(Uri.parse("gkeepdroid://$name/" + Uri.encode(edit.id)))
                .putExtra(EXTRA_EDIT, edit.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val retry = action(RetryEditReceiver::class.java, "retry")
        manager.notify(
            notificationId(edit),
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_refresh)
                .setContentTitle(context.getString(R.string.save_failures_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(retry)
                .setOnlyAlertOnce(true)
                .addAction(0, context.getString(R.string.save_retry), retry)
                .addAction(0, context.getString(R.string.save_discard), action(DiscardEditReceiver::class.java, "discard"))
                .build()
        )
    }

    fun clear(context: Context, edit: PendingEdit) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(edit))
    }
}

/** "Try again" in the notification of a change that could not be saved. */
class RetryEditReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PendingEdits.sendNow(context)
    }
}

/** "Discard" in the notification: the only way a change is given up. */
class DiscardEditReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(SaveFailures.EXTRA_EDIT)?.let { PendingEdits.discard(context, it) }
    }
}
