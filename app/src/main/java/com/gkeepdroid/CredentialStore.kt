package com.gkeepdroid

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Accounts and their master tokens. The tokens are encrypted with a key that lives in the Android Keystore (it never leaves
 * the chip or the secure system part; the app can only ask it to encrypt and decrypt). Nothing is hard-coded and tokens never
 * leave the device (they are not part of the settings export).
 */
object CredentialStore {
    private const val KEY_ALIAS = "gkeepdroid_tokens"

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Tokens decrypted once and kept in memory, so the keystore is not asked for every sync. */
    private val tokens = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun prefs(context: Context): SharedPreferences = prefs ?: synchronized(this) {
        prefs ?: context.applicationContext.let { app ->
            // The file of the library that was used before holds old tokens; it is not read any more, so it goes.
            app.deleteSharedPreferences("credentials")
            app.getSharedPreferences("accounts", Context.MODE_PRIVATE)
        }.also { prefs = it }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** The text as "iv + encrypted bytes" in Base64. */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    /** Null if the text cannot be decrypted (the key is gone, for example after a reset of the lock screen). */
    private fun decrypt(stored: String): String? = runCatching {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12)) }
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()

    fun accounts(context: Context): List<String> {
        val raw = prefs(context).getString("accounts", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun token(context: Context, email: String): String? {
        tokens[email]?.let { return it }
        val stored = prefs(context).getString("token_$email", null) ?: return null
        return decrypt(stored)?.also { tokens[email] = it }
    }

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
        if (token != null) {
            editor.putString("token_$email", encrypt(token))
            tokens[email] = token
        }
        editor.apply()
    }

    fun remove(context: Context, email: String) {
        val list = accounts(context).filter { it != email }
        prefs(context).edit()
            .putString("accounts", JSONArray(list).toString())
            .remove("token_$email")
            .apply()
        tokens.remove(email)
        NotesCache.delete(context, email)
        if (isLocal(email)) LocalNotes.deleteAll(context)
    }

    /** The account a widget actually shows: its own choice if it still exists, else the first one. */
    fun resolve(context: Context, wanted: String): String? {
        val all = accounts(context)
        return if (wanted in all) wanted else all.firstOrNull()
    }
}
