package com.gkeepdroid

import android.content.Context

/** Counts the requests sent to Keep, to show how many there were in the last 24 hours. */
object RequestStats {
    private const val DAY_MS = 24 * 3_600_000L
    private const val MAX_KEPT = 5000

    private fun prefs(context: Context) = context.getSharedPreferences("request_stats", Context.MODE_PRIVATE)

    private fun times(context: Context, now: Long): List<Long> =
        prefs(context).getString("times", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < DAY_MS }

    @Synchronized
    fun record(context: Context) {
        val now = System.currentTimeMillis()
        val kept = (times(context, now) + now).takeLast(MAX_KEPT)
        prefs(context).edit().putString("times", kept.joinToString(",")).apply()
    }

    /** The count for the screen: updates as soon as a request is recorded, and as old ones drop out of the 24 hours. */
    @androidx.compose.runtime.Composable
    fun rememberLast24h(context: Context): Int {
        val count = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(last24h(context)) }
        androidx.compose.runtime.DisposableEffect(Unit) {
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> count.intValue = last24h(context) }
            prefs(context).registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs(context).unregisterOnSharedPreferenceChangeListener(listener) }
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                count.intValue = last24h(context)
            }
        }
        return count.intValue
    }

    @Synchronized
    fun last24h(context: Context): Int = times(context, System.currentTimeMillis()).size
}
