package com.gkeepdroid

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Takes sensitive things out of a text before it can reach a log: e-mail addresses, web addresses, and long
 * strings that look like tokens or ids. What is left is cut short.
 */
object Scrub {
    private val email = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")
    private val url = Regex("https?://\\S+")
    private val token = Regex("[A-Za-z0-9_\\-+/=.]{20,}")

    fun text(value: String?, max: Int = 160): String =
        (value ?: "").lineSequence().firstOrNull().orEmpty()
            .replace(email, "<email>").replace(url, "<url>").replace(token, "<token>")
            .take(max)
}

/**
 * The app's own short event log (syncs, sending, lock, crashes). It never holds note content, account
 * names or tokens: only what happened, and error texts that went through [Scrub].
 */
object AppLog {
    private const val MAX_LINES = 300
    private val time get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun file(context: Context) = File(context.applicationContext.filesDir, "app_log.txt")

    // The same message again within ten minutes is not written twice, so a problem that repeats cannot flood the log.
    private var lastKey = ""
    private var lastAt = 0L

    @Synchronized
    fun add(context: Context, level: Char, tag: String, message: String) {
        try {
            val clean = Scrub.text(message)
            val key = "$level/$tag $clean"
            val now = System.currentTimeMillis()
            if (key == lastKey && now - lastAt < 600_000) return
            lastKey = key
            lastAt = now
            val f = file(context)
            val line = "${time.format(Date())} $key\n"
            f.appendText(line)
            if (f.length() > 64_000) f.writeText(f.readLines().takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
        } catch (e: Exception) {
            // Logging must never break the app.
        }
    }

    fun i(context: Context, tag: String, message: String) = add(context, 'I', tag, message)
    fun e(context: Context, tag: String, message: String) = add(context, 'E', tag, message)

    @Synchronized
    fun lines(context: Context): List<String> = try {
        file(context).takeIf { it.exists() }?.readLines().orEmpty().takeLast(MAX_LINES)
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Why the last runs of the app ended, as the system knows it (Android 11 and newer): crashes of the
     * native part, ANRs and kills for lack of memory leave no trace in the app itself. Each one is logged
     * once; the ordinary endings (swiped away, finished by itself) are left out.
     */
    fun logExitReasons(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 30) return
        try {
            val manager = context.getSystemService(android.app.ActivityManager::class.java)
            val prefs = context.getSharedPreferences("app_log_state", Context.MODE_PRIVATE)
            val seen = prefs.getLong("exit_seen", 0)
            val reasons = manager.getHistoricalProcessExitReasons(context.packageName, 0, 10)
                .filter { it.timestamp > seen }.sortedBy { it.timestamp }
            val abnormal = mapOf(
                android.app.ApplicationExitInfo.REASON_CRASH to "crash",
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE to "native crash",
                android.app.ApplicationExitInfo.REASON_ANR to "ANR",
                android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE to "initialization failure",
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY to "killed, low memory",
                android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE to "killed, resource usage",
                android.app.ApplicationExitInfo.REASON_SIGNALED to "killed by signal",
            )
            reasons.forEach { info ->
                abnormal[info.reason]?.let { name ->
                    add(context, 'E', "exit", "previous run ended: $name (status ${info.status}, ${Scrub.text(info.description, 80)})")
                }
            }
            reasons.maxOfOrNull { it.timestamp }?.let { prefs.edit().putLong("exit_seen", it).apply() }
        } catch (e: Exception) {
            // The log is a help, never a risk.
        }
    }

    /** A crash, as class names and code positions plus a scrubbed message. */
    fun crash(context: Context, e: Throwable) {
        val frames = e.stackTrace.take(12).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        add(context, 'E', "crash", "${e.javaClass.name}: ${Scrub.text(e.message, 100)} at $frames")
    }
}

/** The text of a log file: the phone and app state without anything personal, and the recent events. */
object LogReport {
    fun build(context: Context): String {
        val global = SettingsStore.loadGlobal(context)
        val app = AppSettingsStore.load(context)
        val accounts = CredentialStore.accounts(context)
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        fun ago(ms: Long) = if (ms == 0L) "never" else date.format(Date(ms))
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val pending = PendingEdits.all(context)
        return buildString {
            appendLine("GKeepDroid log")
            appendLine("Created: ${date.format(Date())}")
            appendLine("Contains no note texts, titles, labels, account names or tokens.")
            appendLine()
            appendLine("== App and phone ==")
            appendLine("Version: ${info.versionName} (${info.longVersionCode})")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine()
            appendLine("== State ==")
            appendLine("Accounts: ${accounts.size} (signed in: ${accounts.count { CredentialStore.isUsable(context, it) }}, local account: ${accounts.any { CredentialStore.isLocal(it) }})")
            appendLine("Notes in cache: ${accounts.sumOf { NotesCache.readAll(context, it).size }}")
            appendLine("Widgets placed: ${SyncAlarm.hasWidgets(context)}")
            appendLine("Last sync ok: ${ago(SyncPolicy.lastSuccess(context))}")
            appendLine("Last sync error: ${SyncPolicy.lastError(context)?.let { Scrub.text(it) } ?: "none"}")
            appendLine("Sign-in expired: ${SyncPolicy.expiredAccount(context) != null}")
            appendLine("Requests to Keep in the last 24 h: ${RequestStats.last24h(context)}")
            appendLine("Notifications allowed: ${Reminders.canNotify(context)}")
            appendLine("Exact alarms allowed: ${Reminders.canScheduleExact(context)}")
            appendLine("Unsent changes: ${pending.size}")
            pending.forEach { appendLine("  - ${it.function}, error: ${it.error.ifBlank { "none" }.let { e -> Scrub.text(e) }}") }
            appendLine()
            appendLine("== Settings (no personal values) ==")
            appendLine("Theme: ${global.themeMode}, demo mode: ${global.demoMode}")
            appendLine("Sync: every ${global.syncIntervalMin} min, on unlock ${global.syncOnUnlock}, on open ${global.syncOnOpen}, min gap ${global.minGapSec} s, background ${global.backgroundSync} (${global.backgroundIntervalMin} min), Wi-Fi only ${global.wifiOnly}")
            appendLine("Locks: app ${app.lockApp}, edit ${app.lockEdit}, widget tick ${app.lockWidgetTick}, private notes ${app.lockPrivateNotes}, grace ${app.lockGraceSec} s")
            appendLine("Cards: layout ${app.layout}, sort ${app.sortMode}, lines ${app.previewLines}")
            appendLine()
            appendLine("== Events (newest last) ==")
            AppLog.lines(context).forEach { appendLine(it) }
        }
    }
}
