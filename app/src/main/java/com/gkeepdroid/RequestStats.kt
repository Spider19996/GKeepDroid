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

    @Synchronized
    fun last24h(context: Context): Int = times(context, System.currentTimeMillis()).size
}
