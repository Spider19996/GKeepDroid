package com.gkeepdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Opened by a tap on a checkbox in a widget while the edit lock is on: asks for the lock in a small
 * window above the home screen, then ticks the item. Shortly after a pass, further ticks go through unasked.
 */
class UnlockTickActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private val credential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        AppLock.credentialResult(it.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val account = intent.getStringExtra(EditNoteActivity.EXTRA_ACCOUNT)
        val noteId = intent.getStringExtra(EditNoteActivity.EXTRA_NOTE_ID)
        val itemId = intent.getStringExtra("item_id")
        if (account == null || noteId == null || itemId == null) {
            finish()
            return
        }
        val checked = intent.getBooleanExtra("checked", true)
        val delete = intent.getBooleanExtra("delete", false)
        fun tick() {
            lifecycleScope.launch {
                applyTick(applicationContext, account, noteId, itemId, checked, delete)
                finish()
            }
        }
        if (AppLock.widgetTickAllowed(this)) {
            tick()
        } else {
            AppLock.prompt(this, R.string.lock_prompt_edit, { credential.launch(it) }) { result ->
                if (result == AppLock.Result.OK) {
                    AppLock.markWidgetUnlocked()
                    tick()
                } else {
                    finish()
                }
            }
        }
    }
}
