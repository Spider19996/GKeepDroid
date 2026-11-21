package com.gkeepdroid

import android.content.Context
import androidx.core.content.edit

/**
 * Whether notes are being fetched right now, for the refresh arrow of the widgets to turn while they are, and whether the last
 * refresh found no connection, for the arrow to be red until a fetch can run.
 */
object SyncState {
    private fun prefs(context: Context) = context.getSharedPreferences("sync_running", Context.MODE_PRIVATE)

    // A fetch that died without saying so (the process was killed) must not leave the arrow turning for good.
    private const val STALE_MS = 3 * 60_000L

    fun running(context: Context): Boolean {
        val since = prefs(context).getLong("since", 0L)
        return since > 0 && System.currentTimeMillis() - since < STALE_MS
    }

    /** Marks the fetch as running (or as over) and draws the widgets again if that changed what they show. */
    suspend fun set(context: Context, running: Boolean) {
        if (running(context) == running) return
        prefs(context).edit { putLong("since", if (running) System.currentTimeMillis() else 0L) }
        if (WidgetRefresh.hasWidgets(context)) WidgetRefresh.all(context)
    }

    /** True when a refresh was asked for without a connection; the fetch waits for one and clears this when it starts. */
    fun offline(context: Context): Boolean = prefs(context).getBoolean("offline", false)

    suspend fun setOffline(context: Context, offline: Boolean) {
        if (offline(context) == offline) return
        prefs(context).edit { putBoolean("offline", offline) }
        if (WidgetRefresh.hasWidgets(context)) WidgetRefresh.all(context)
    }

    /** Whether the device has a network that really reaches the internet now (a Wi-Fi with a login page does not). */
    fun connected(context: Context): Boolean {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        // VALIDATED: Android has tested that the network gets through to the internet, which a Wi-Fi without a connection or
        // behind a login page fails.
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
