package com.gkeepdroid

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

/**
 * Demo mode, for screenshots: every account shows the same generic notes, the account names are
 * hidden, and nothing is sent to Keep, so the real notes never end up in a picture.
 */
object DemoNotes {
    fun enabled(context: Context): Boolean = SettingsStore.loadGlobal(context).demoMode

    /** Changes whenever the settings are saved, so screens can react to the demo mode being switched. */
    val version = mutableIntStateOf(0)

    const val ACCOUNT_NAME = "demo@example.com"

    private fun item(n: Int, text: String, checked: Boolean, indent: Boolean = false) =
        KeepItem("demo-item-$n-$text".hashCode().toString(), text, checked, indent)

    /** A note of the samples that has its content hidden. */
    private const val PRIVATE_ID = "demo-9"

    private const val HOUR = 3_600_000L

    private fun note(
        now: Long, n: Int, title: String, text: String = "", color: String = NoteColors.DEFAULT, pinned: Boolean = false,
        labels: List<String> = emptyList(), items: List<KeepItem>? = null, ageHours: Long = n.toLong(), state: String = "active",
    ) = KeepNote(
        id = "demo-$n", title = title, text = text, isList = items != null, items = items.orEmpty(),
        color = color, pinned = pinned, sort = 1_000_000L - n * 10_000L,
        updated = now - ageHours * HOUR, created = now - (ageHours + 48) * HOUR, labels = labels, state = state,
    )

    // A bold word in front of an item is its category.
    private val groceries = listOf(
        item(1, "Produce 6x Tomatoes", false), item(1, "Produce Basil", false), item(1, "Dairy 2x Oat milk", false),
        item(1, "Dairy Parmesan", true), item(1, "Pantry 3x Pasta", false), item(1, "Pantry Coffee beans", false),
    )
    private val categoryWords = listOf("Produce", "Dairy", "Pantry")

    private const val WEEKEND = "Weekend\nHike on Saturday morning\nMovie night on Sunday\nCall the grandparents"
    private const val BOOKS = "Reading list\n> Non-fiction\n> The Pragmatic Programmer\n> A Gentle Introduction to Algorithms\nSomething with dragons"

    /** The sample notes; the first account gets them, other accounts stay empty. */
    fun notes(): List<KeepNote> {
        val now = System.currentTimeMillis()
        return listOf(
            note(now, 1, "Groceries", color = "Green", pinned = true, labels = listOf("Home"), items = groceries),
            note(now, 2, "Weekend plans", WEEKEND, color = "Yellow", labels = listOf("Personal")),
            note(
                now, 3, "Project launch", color = "Blue", labels = listOf("Work"),
                items = listOf(
                    item(3, "Write release notes", true), item(3, "Test on two phones", false), item(3, "Widgets", false, indent = true),
                    item(3, "Reminders", true, indent = true), item(3, DIVIDER_TEXT, false), item(3, "Record demo video", false), item(3, "Announce", false),
                ),
            ),
            note(now, 4, "Book ideas", BOOKS, labels = listOf("Personal"), ageHours = 20),
            note(now, 5, "Pancake recipe", "2 cups flour, 2 eggs, 1½ cups milk, a pinch of salt. Whisk, rest for 10 minutes, fry in butter.", color = "Orange", labels = listOf("Home"), ageHours = 30),
            note(
                now, 6, "Packing list", color = "Teal", labels = listOf("Travel"),
                items = listOf(item(6, "Passport", true), item(6, "2x Charger", true), item(6, "Sunscreen", false), item(6, "Headphones", false), item(6, "Camera", false)),
                ageHours = 52,
            ),
            note(now, 7, "Quote of the day", "“Simplicity is the ultimate sophistication.”", color = "Red", ageHours = 70),
            note(now, 8, "Meeting notes", "Agree on the roadmap, share the draft by Friday, review budget next week.", labels = listOf("Work"), ageHours = 96),
            note(now, 9, "Gift ideas", "A surprise that stays one.", color = "Purple", labels = listOf("Personal"), ageHours = 120),
        )
    }

    /** Samples for the archive and the trash. */
    fun otherNotes(): List<KeepNote> {
        val now = System.currentTimeMillis()
        return listOf(
            note(now, 20, "Last year's trip", "Lisbon, five days, take the tram 28.", color = "Teal", labels = listOf("Travel"), ageHours = 800, state = "archived"),
            note(now, 21, "Old shopping list", "Candles\nWrapping paper", ageHours = 300, state = "trashed"),
        )
    }

    /** Formatting and background pictures, as Keep's editor would have given them. */
    fun rich(noteId: String): RichNote? {
        fun bold(text: String, word: String, flags: Int = RichNotes.BOLD): RichDoc {
            val from = text.indexOf(word)
            return RichDoc(text, listOf(intArrayOf(from, from + word.length, flags)))
        }
        return when (noteId) {
            "demo-1" -> RichNote("", groceries.associate { item -> item.id to bold(item.text, categoryWords.first { item.text.startsWith("$it ") }) })
            "demo-2" -> RichNote("", mapOf("text" to bold(WEEKEND, "Weekend", RichNotes.HEADING)))
            "demo-4" -> RichNote("", mapOf("text" to bold(BOOKS, "Reading list")))
            "demo-5" -> RichNote("food", emptyMap())
            "demo-6" -> RichNote("travel", emptyMap())
            else -> null
        }
    }

    /** Reminders of the samples: tomorrow and a few days ahead, at full hours. They are only shown, never scheduled. */
    fun reminders(noteId: String): List<Reminder> {
        fun at(days: Int, hour: Int) = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, days)
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return when (noteId) {
            "demo-1" -> listOf(Reminder("demo-reminder-1", at(1, 17)))
            "demo-8" -> listOf(Reminder("demo-reminder-2", at(3, 9)), Reminder("demo-reminder-3", at(4, 9)))
            else -> emptyList()
        }
    }

    /** The key of the sample with hidden content (see [PrivateNotes]). */
    fun privateKeys(context: Context): Set<String> =
        CredentialStore.accounts(context).firstOrNull()?.let { setOf(PrivateNotes.key(it, PRIVATE_ID)) }.orEmpty()
}
