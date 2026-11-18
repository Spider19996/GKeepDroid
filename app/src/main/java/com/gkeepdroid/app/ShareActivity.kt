package com.gkeepdroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Receives text and links shared from other apps and opens the note editor with them filled in.
 * Only a hand-over: it shows nothing itself.
 */
class ShareActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val account = CredentialStore.accounts(this).firstOrNull()
        val target = if (account == null) {
            // Nothing to save to yet: let the user add an account first.
            Intent(this, SettingsActivity::class.java)
        } else {
            Intent(this, EditNoteActivity::class.java)
                .putExtra(EditNoteActivity.EXTRA_ACCOUNT, account)
                .putExtra(EditNoteActivity.EXTRA_NEW_LIST, false)
                .putExtra(
                    EditNoteActivity.EXTRA_PREFILL_TITLE,
                    intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE).orEmpty(),
                )
                .putExtra(EditNoteActivity.EXTRA_PREFILL_TEXT, intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
        }
        startActivity(target)
        finish()
    }
}
