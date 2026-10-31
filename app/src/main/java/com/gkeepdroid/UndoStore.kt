package com.gkeepdroid

/**
 * The last archive or trash action made in the editor. The editor closes right after it, so the notes
 * screen picks it up and offers "Undo" for a few seconds.
 */
object UndoStore {
    enum class Kind { ARCHIVE, TRASH }

    data class Undo(val account: String, val noteId: String, val kind: Kind, val at: Long)

    private const val WINDOW_MS = 15_000L

    @Volatile
    private var pending: Undo? = null

    fun set(account: String, noteId: String, kind: Kind) {
        pending = Undo(account, noteId, kind, System.currentTimeMillis())
    }

    /** The action to offer undo for, once; null if there is none or it is too old. */
    fun take(): Undo? {
        val undo = pending
        pending = null
        return undo?.takeIf { System.currentTimeMillis() - it.at < WINDOW_MS }
    }
}
