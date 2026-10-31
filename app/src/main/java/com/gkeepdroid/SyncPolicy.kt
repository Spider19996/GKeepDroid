package com.gkeepdroid

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Decides whether an automatic sync may run now. Unlocking the phone or opening the app many times a
 * minute must not hit Google's servers every time, so automatic syncs keep a minimum gap, and after a
 * failed sync they pause for a while. The refresh button and "Sync now" are user actions and skip these rules.
 */
object SyncPolicy {
    enum class Reason { UNLOCK, TIMER, APP_OPEN, WIDGET }

    private const val FAILURE_PAUSE_MS = 5 * 60_000L

    private fun prefs(context: Context) = context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)

    /** Whether the current connection may be used: any connection, or only an unmetered one (Wi-Fi). */
    fun networkAllowed(context: Context, wifiOnly: Boolean): Boolean {
        if (!wifiOnly) return true
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** True if a sync for [reason] should run now; the time is noted so the next request has to wait. */
    fun claim(context: Context, reason: Reason): Boolean {
        val settings = SettingsStore.loadGlobal(context)
        if (reason == Reason.UNLOCK && !settings.syncOnUnlock) return false
        if (reason == Reason.APP_OPEN && !settings.syncOnOpen) return false
        if (CredentialStore.accounts(context).isEmpty()) return false
        if (!networkAllowed(context, settings.wifiOnly)) return false
        // Syncing on its own is only useful while a widget shows the notes; the app syncs for itself.
        if (reason != Reason.APP_OPEN && !SyncAlarm.hasWidgets(context)) return false
        val now = System.currentTimeMillis()
        val p = prefs(context)
        val gap = maxOf(
            settings.minGapSec * 1000L,
            if (now - p.getLong("last_failure", 0) < FAILURE_PAUSE_MS) FAILURE_PAUSE_MS else 0L,
        )
        if (now - p.getLong("last_start", 0) < gap) return false
        p.edit().putLong("last_start", now).apply()
        return true
    }

    /** Starts a sync in the background if [claim] allows it. */
    fun request(context: Context, reason: Reason) {
        if (claim(context, reason)) SyncWorker.syncNow(context, manual = false, force = reason == Reason.APP_OPEN)
    }

    /** When the last sync of all accounts worked, or 0 if none did yet. */
    fun lastSuccess(context: Context): Long = prefs(context).getLong("last_ok", 0)

    /** Why the last sync failed, or null if it worked. */
    fun lastError(context: Context): String? = prefs(context).getString("last_error", null)

    /** The account whose sign-in Google no longer accepts, or null. */
    fun expiredAccount(context: Context): String? = prefs(context).getString("expired_account", null)

    /** True when [e] says that the stored login was refused, not that something else went wrong. */
    fun isAuthError(e: Exception): Boolean = Regex(
        "LoginException|BadAuthentication|Unauthorized|invalid_grant|AuthError|Failed to (authenticate|refresh)|NeedsBrowser|\\b401\\b",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(e.message.orEmpty())

    /** Remembers the outcome of a sync of all accounts for the status display. */
    fun recordSync(context: Context, error: String?, expiredAccount: String? = null) {
        val editor = prefs(context).edit()
        if (error == null) editor.putLong("last_ok", System.currentTimeMillis()).remove("last_error")
        else editor.putString("last_error", error)
        if (expiredAccount == null) editor.remove("expired_account") else editor.putString("expired_account", expiredAccount)
        editor.apply()
    }

    /** Every sync, also the manual ones, counts for the gap. */
    fun markStart(context: Context) {
        prefs(context).edit().putLong("last_start", System.currentTimeMillis()).apply()
    }

    fun markResult(context: Context, ok: Boolean) {
        prefs(context).edit().putLong("last_failure", if (ok) 0L else System.currentTimeMillis()).apply()
    }
}
