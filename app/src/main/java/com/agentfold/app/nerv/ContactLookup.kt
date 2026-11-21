package com.agentfold.app.nerv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract

class ContactLookup(private val context: Context) {

    data class Contact(
        val name: String,
        val number: String,
        val typeLabel: String
    )

    companion object {

        const val MAX_RESULTS = 10

        fun typeLabel(type: Int, customLabel: String?): String = when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "Fax (work)"
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "Fax (home)"
            ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "Pager"
            ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Other"
            ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM ->
                customLabel?.trim().orEmpty().ifBlank { "Other" }
            else -> "Other"
        }

        fun lineFor(contact: Contact): String = "${contact.name} — ${contact.number} (${contact.typeLabel})"
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun lookup(queryRaw: String): List<Contact> {
        val query = queryRaw.trim()
        if (query.isEmpty() || !hasPermission()) return emptyList()
        val out = mutableListOf<Contact>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%$query%")
            val sort = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            context.contentResolver.query(uri, projection, selection, args, sort)?.use { cursor ->
                val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                while (cursor.moveToNext() && out.size < MAX_RESULTS) {
                    val name = if (nameCol >= 0) cursor.getString(nameCol).orEmpty().trim() else ""
                    val number = if (numberCol >= 0) cursor.getString(numberCol).orEmpty().trim() else ""
                    if (name.isEmpty() || number.isEmpty()) continue
                    val type = if (typeCol >= 0) cursor.getInt(typeCol) else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
                    val custom = if (labelCol >= 0) cursor.getString(labelCol) else null
                    out.add(Contact(name, number, typeLabel(type, custom)))
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        return out
    }

    fun firstMatch(queryRaw: String): Contact? = lookup(queryRaw).firstOrNull()
}
