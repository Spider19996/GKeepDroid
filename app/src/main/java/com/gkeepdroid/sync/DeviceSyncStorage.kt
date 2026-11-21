package com.gkeepdroid

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

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
