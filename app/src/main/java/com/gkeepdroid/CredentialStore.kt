package com.gkeepdroid

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONException

/**
 * Accounts and their master tokens, kept in encrypted preferences. Nothing is hard-coded and
 * tokens never leave the device (they are not part of the settings export).
 */
object CredentialStore {
    @Volatile
    private var prefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences = prefs ?: synchronized(this) {
        prefs ?: EncryptedSharedPreferences.create(
            context.applicationContext,
            "credentials",
            MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).also { prefs = it }
    }

    fun accounts(context: Context): List<String> {
        val raw = prefs(context).getString("accounts", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun token(context: Context, email: String): String? = prefs(context).getString("token_$email", null)

    fun isLocal(email: String) = email == LOCAL_ACCOUNT

    /** A Keep account needs its token; the local account needs nothing. */
    fun isUsable(context: Context, email: String) = isLocal(email) || token(context, email) != null

    /** What the user sees for an account: the e-mail, or a name for the local account. */
    fun displayName(context: Context, email: String, demo: Boolean = DemoNotes.enabled(context)) = when {
        isLocal(email) -> context.getString(R.string.local_account_name)
        demo -> DemoNotes.ACCOUNT_NAME
        else -> email
    }

    /** Adds the account or updates its token; a null token keeps an existing one. */
    fun put(context: Context, email: String, token: String?) {
        val p = prefs(context)
        val list = accounts(context).toMutableList()
        if (email !in list) list.add(email)
        val editor = p.edit().putString("accounts", JSONArray(list).toString())
        if (token != null) editor.putString("token_$email", token)
        editor.apply()
    }

    fun remove(context: Context, email: String) {
        val list = accounts(context).filter { it != email }
        prefs(context).edit()
            .putString("accounts", JSONArray(list).toString())
            .remove("token_$email")
            .apply()
        NotesCache.delete(context, email)
        if (isLocal(email)) LocalNotes.deleteAll(context)
    }

    /** The account a widget actually shows: its own choice if it still exists, else the first one. */
    fun resolve(context: Context, wanted: String): String? {
        val all = accounts(context)
        return if (wanted in all) wanted else all.firstOrNull()
    }
}
