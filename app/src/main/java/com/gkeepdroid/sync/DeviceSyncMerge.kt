package com.gkeepdroid

/** One synced value; a null [value] means "deleted" and is kept for a while so the deletion reaches the other phones. */
data class SyncEntry(val value: String?, val time: Long)

/**
 * The rules for combining what several phones know about the things Keep has no place for (private notes, reminders, own
 * photos, folded notes, ...). No Android in here, so the rules can be tested on their own.
 *
 * Each value has the time it was last changed; the newest change wins, per value. What changed on this phone is found by
 * comparing the phone's current values with the ones at the end of the last sync.
 */
object DeviceSyncMerge {
    /** The newer entry wins; at the same time the larger value does, so that every phone picks the same one. */
    fun newer(a: SyncEntry, b: SyncEntry): SyncEntry = when {
        a.time != b.time -> if (a.time > b.time) a else b
        rank(a) >= rank(b) -> a
        else -> b
    }

    private fun rank(e: SyncEntry) = e.value?.let { "1$it" } ?: "0"

    /**
     * Stamps what changed on this phone since the last sync: a value that differs from [last], or one that is gone. On the very
     * first run ([first]) nothing is known yet, so everything gets the oldest possible time: what other phones already have then
     * wins over what this phone brings along, and what only this phone has still spreads.
     */
    fun stampLocalChanges(last: Map<String, String>, now: Map<String, String>, state: MutableMap<String, SyncEntry>, time: Long, first: Boolean) {
        for (key in last.keys + now.keys) {
            if (last[key] != now[key]) state[key] = SyncEntry(now[key], if (first) 1L else time)
        }
    }

    /** Takes over what another phone knows, where it is newer. */
    fun mergeRemote(state: MutableMap<String, SyncEntry>, remote: Map<String, SyncEntry>) {
        for ((key, entry) in remote) {
            val own = state[key]
            state[key] = if (own == null) entry else newer(own, entry)
        }
    }

    /** What has to change on this phone: the merged values that differ from the ones it has now. */
    fun toApply(state: Map<String, SyncEntry>, now: Map<String, String>): Map<String, String?> =
        state.filter { (key, entry) -> entry.value != now[key] }.mapValues { it.value.value }

    /** Forgets deletions that every phone has long had the time to see. */
    fun prune(state: MutableMap<String, SyncEntry>, olderThan: Long) {
        state.entries.removeAll { it.value.value == null && it.value.time < olderThan }
    }
}
