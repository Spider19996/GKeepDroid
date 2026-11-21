package com.gkeepdroid

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A file in the place where the phones meet. */
data class StoredFile(val name: String, val modified: Long)

/** The place where the phones leave their files for each other: one flat list of files. A folder today, other places can follow. */
interface SyncStorage {
    fun list(): List<StoredFile>
    fun read(name: String): ByteArray?
    fun write(name: String, bytes: ByteArray)
    fun delete(name: String)
}

/** A folder the user chose (through Android's folder picker), which another app keeps in sync between the phones. */
class FolderSyncStorage(private val context: Context, private val tree: Uri) : SyncStorage {
    private val rootId = DocumentsContract.getTreeDocumentId(tree)

    private class Entry(val docId: String, val name: String, val modified: Long, val isDir: Boolean)

    private fun docUri(docId: String) = DocumentsContract.buildDocumentUriUsingTree(tree, docId)

    private fun entries(): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_MIME_TYPE)
        return context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Entry(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getString(3) == Document.MIME_TYPE_DIR))
            }
        }.orEmpty().filter { !it.isDir }
    }

    override fun list() = entries().map { StoredFile(it.name, it.modified) }

    override fun read(name: String): ByteArray? {
        val entry = entries().firstOrNull { it.name == name } ?: return null
        return context.contentResolver.openInputStream(docUri(entry.docId))?.use { it.readBytes() }
    }

    override fun write(name: String, bytes: ByteArray) {
        // An existing file is overwritten in place: creating a second one would get another name from the provider.
        val existing = entries().firstOrNull { it.name == name }
        val uri = existing?.let { docUri(it.docId) }
            ?: DocumentsContract.createDocument(context.contentResolver, docUri(rootId), if (name.endsWith(".json")) "application/json" else "image/jpeg", name)
            ?: error("Cannot create $name")
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Cannot write $name")
    }

    override fun delete(name: String) {
        val entry = entries().firstOrNull { it.name == name } ?: return
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, docUri(entry.docId)) }
    }
}

/**
 * The hidden app folder of the account's Google Drive: nothing the user sees or can tidy up in Drive, and no folder to set up.
 * The access token comes from the account's master token (see `drive_token` in the bridge) and lasts about an hour.
 */
class DriveSyncStorage(private val context: Context, private val account: String) : SyncStorage {
    private val ids = HashMap<String, String>()

    private fun token(fresh: Boolean = false): String {
        synchronized(cache) {
            cache[account]?.let { (token, at) -> if (!fresh && System.currentTimeMillis() - at < 45 * 60_000L) return token }
            val master = CredentialStore.token(context, account) ?: error("Not signed in")
            val token = PythonRuntime.get(context).getModule("keep_bridge").callAttr("drive_token", account, master).toString()
            cache[account] = token to System.currentTimeMillis()
            return token
        }
    }

    /** One call to the Drive API; a token that has run out is renewed once. */
    private fun call(method: String, url: String, body: ByteArray? = null, contentType: String? = null): Pair<Int, ByteArray> {
        for (attempt in 0..1) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 30_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Authorization", "Bearer ${token(fresh = attempt == 1)}")
                // HttpURLConnection has no PATCH: Google's servers take it as a header of a POST.
                if (method == "PATCH") {
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                } else {
                    connection.requestMethod = method
                }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", contentType)
                    connection.outputStream.use { it.write(body) }
                }
                val code = connection.responseCode
                if (code == 401 && attempt == 0) continue
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                return code to (stream?.use { it.readBytes() } ?: ByteArray(0))
            } finally {
                connection.disconnect()
            }
        }
        error("unreachable")
    }

    override fun list(): List<StoredFile> {
        ids.clear()
        val out = mutableListOf<StoredFile>()
        var page: String? = null
        do {
            val url = "$API/files?spaces=appDataFolder&pageSize=1000&fields=nextPageToken,files(id,name,modifiedTime)" +
                (page?.let { "&pageToken=" + Uri.encode(it) } ?: "")
            val (code, body) = call("GET", url)
            check(code == 200) { "Drive: list failed ($code)" }
            val json = JSONObject(body.decodeToString())
            val files = json.optJSONArray("files")
            for (i in 0 until (files?.length() ?: 0)) {
                val f = files!!.getJSONObject(i)
                ids[f.getString("name")] = f.getString("id")
                out += StoredFile(f.getString("name"), runCatching { java.time.Instant.parse(f.getString("modifiedTime")).toEpochMilli() }.getOrDefault(0L))
            }
            page = json.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        return out
    }

    override fun read(name: String): ByteArray? {
        val id = ids[name] ?: return null
        val (code, body) = call("GET", "$API/files/$id?alt=media")
        return if (code == 200) body else null
    }

    override fun write(name: String, bytes: ByteArray) {
        val type = if (name.endsWith(".json")) "application/json" else "image/jpeg"
        val existing = ids[name]
        if (existing != null) {
            val (code, _) = call("PATCH", "$UPLOAD/files/$existing?uploadType=media", bytes, type)
            check(code == 200) { "Drive: write failed ($code)" }
            return
        }
        val boundary = "gkd" + java.util.UUID.randomUUID().toString().replace("-", "")
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
        val body = java.io.ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: $type\r\n\r\n".toByteArray())
            write(bytes)
            write("\r\n--$boundary--".toByteArray())
        }.toByteArray()
        val (code, answer) = call("POST", "$UPLOAD/files?uploadType=multipart&fields=id", body, "multipart/related; boundary=$boundary")
        check(code == 200) { "Drive: write failed ($code)" }
        ids[name] = JSONObject(answer.decodeToString()).getString("id")
    }

    override fun delete(name: String) {
        val id = ids.remove(name) ?: return
        call("DELETE", "$API/files/$id")
    }

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        val cache = HashMap<String, Pair<String, Long>>()
    }
}
