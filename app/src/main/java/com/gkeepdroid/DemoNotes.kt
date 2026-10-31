package com.gkeepdroid

import android.content.Context

/**
 * Demo mode, for screenshots: every account shows the same generic notes, the account names are
 * hidden, and nothing is sent to Keep, so the real notes never end up in a picture.
 */
object DemoNotes {
    fun enabled(context: Context): Boolean = SettingsStore.loadGlobal(context).demoMode

    const val ACCOUNT_NAME = "demo@example.com"

    private fun item(n: Int, text: String, checked: Boolean) = KeepItem("demo-item-$n-$text".hashCode().toString(), text, checked)

    /** The sample notes; the first account gets them, other accounts stay empty. */
    fun notes(): List<KeepNote> {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        fun note(
            n: Int, title: String, text: String = "", color: String = NoteColors.DEFAULT, pinned: Boolean = false,
            labels: List<String> = emptyList(), items: List<KeepItem>? = null, ageHours: Long = n.toLong(),
        ) = KeepNote(
            id = "demo-$n", title = title, text = text, isList = items != null, items = items.orEmpty(),
            color = color, pinned = pinned, sort = 1_000_000L - n * 10_000L,
            updated = now - ageHours * hour, created = now - (ageHours + 48) * hour, labels = labels,
        )
        return listOf(
            note(
                1, "Groceries", color = "Green", pinned = true, labels = listOf("Home"),
                items = listOf(item(1, "Oat milk", false), item(2, "Tomatoes", false), item(3, "Pasta", true), item(4, "Coffee beans", false)),
            ),
            note(2, "Weekend plans", "Hike on Saturday morning\nMovie night on Sunday\nCall the grandparents", color = "Yellow", labels = listOf("Personal")),
            note(
                3, "Project launch", color = "Blue", labels = listOf("Work"),
                items = listOf(item(5, "Write release notes", true), item(6, "Test on two phones", true), item(7, "Record demo video", false), item(8, "Announce", false)),
            ),
            note(4, "Book ideas", "The Pragmatic Programmer\nA Gentle Introduction to Algorithms\nSomething with dragons", labels = listOf("Personal"), ageHours = 20),
            note(5, "Pancake recipe", "2 cups flour, 2 eggs, 1½ cups milk, a pinch of salt. Whisk, rest for 10 minutes, fry in butter.", color = "Orange", labels = listOf("Home"), ageHours = 30),
            note(
                6, "Packing list", color = "Teal", labels = listOf("Travel"),
                items = listOf(item(9, "Passport", true), item(10, "Charger", true), item(11, "Sunscreen", false), item(12, "Headphones", false), item(13, "Camera", false)),
                ageHours = 52,
            ),
            note(7, "Quote of the day", "“Simplicity is the ultimate sophistication.”", color = "Red", ageHours = 70),
            note(8, "Meeting notes", "Agree on the roadmap, share the draft by Friday, review budget next week.", labels = listOf("Work"), ageHours = 96),
        )
    }
}
