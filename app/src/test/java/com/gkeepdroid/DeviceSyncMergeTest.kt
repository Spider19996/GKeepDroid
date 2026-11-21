package com.gkeepdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceSyncMergeTest {
    private fun e(value: String?, time: Long) = SyncEntry(value, time)

    @Test fun newerWins() {
        assertEquals(e("b", 5), DeviceSyncMerge.newer(e("a", 3), e("b", 5)))
        assertEquals(e("b", 5), DeviceSyncMerge.newer(e("b", 5), e("a", 3)))
    }

    @Test fun sameTimeIsDecidedTheSameOnEveryPhone() {
        assertEquals(DeviceSyncMerge.newer(e("a", 4), e("b", 4)), DeviceSyncMerge.newer(e("b", 4), e("a", 4)))
        assertEquals(DeviceSyncMerge.newer(e(null, 4), e("a", 4)), DeviceSyncMerge.newer(e("a", 4), e(null, 4)))
    }

    @Test fun localChangesAreStamped() {
        val state = mutableMapOf<String, SyncEntry>()
        DeviceSyncMerge.stampLocalChanges(mapOf("x" to "1", "gone" to "1"), mapOf("x" to "2", "new" to "1"), state, 100, first = false)
        assertEquals(e("2", 100), state["x"])
        assertEquals(e("1", 100), state["new"])
        assertEquals(e(null, 100), state["gone"])
    }

    @Test fun unchangedValuesAreNotStamped() {
        val state = mutableMapOf<String, SyncEntry>()
        DeviceSyncMerge.stampLocalChanges(mapOf("x" to "1"), mapOf("x" to "1"), state, 100, first = false)
        assertEquals(emptyMap<String, SyncEntry>(), state)
    }

    @Test fun firstRunLosesAgainstOtherPhonesButStillSpreads() {
        val state = mutableMapOf<String, SyncEntry>()
        DeviceSyncMerge.stampLocalChanges(emptyMap(), mapOf("both" to "mine", "mineOnly" to "1"), state, 100, first = true)
        DeviceSyncMerge.mergeRemote(state, mapOf("both" to e("theirs", 50), "theirOnly" to e("1", 50)))
        assertEquals(e("theirs", 50), state["both"])
        assertEquals(e("1", 1), state["mineOnly"])
        assertEquals(e("1", 50), state["theirOnly"])
    }

    @Test fun aDeletionReachesTheOtherPhone() {
        // Phone A removed the value later than phone B set it.
        val b = mutableMapOf("x" to e("1", 10))
        DeviceSyncMerge.mergeRemote(b, mapOf("x" to e(null, 20)))
        val todo = DeviceSyncMerge.toApply(b, mapOf("x" to "1"))
        assertEquals(mapOf<String, String?>("x" to null), todo)
    }

    @Test fun nothingToApplyWhenPhoneAlreadyHasIt() {
        val state = mapOf("x" to e("1", 10), "y" to e(null, 10))
        assertEquals(emptyMap<String, String?>(), DeviceSyncMerge.toApply(state, mapOf("x" to "1")))
    }

    @Test fun mergingTwiceChangesNothing() {
        val state = mutableMapOf("x" to e("1", 10))
        val remote = mapOf("x" to e("2", 12), "y" to e(null, 3))
        DeviceSyncMerge.mergeRemote(state, remote)
        val once = state.toMap()
        DeviceSyncMerge.mergeRemote(state, remote)
        assertEquals(once, state)
    }

    @Test fun oldDeletionsArePruned() {
        val state = mutableMapOf("old" to e(null, 1), "recent" to e(null, 90), "kept" to e("1", 1))
        DeviceSyncMerge.prune(state, 50)
        assertNull(state["old"])
        assertEquals(e(null, 90), state["recent"])
        assertEquals(e("1", 1), state["kept"])
    }
}
