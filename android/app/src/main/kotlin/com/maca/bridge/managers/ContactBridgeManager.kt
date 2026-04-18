package com.maca.bridge.managers

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.maca.bridge.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages contact discovery and synchronization.
 * Queries the system Contacts content provider and broadcasts the address book
 * to the macOS client for name resolution and conversation identification.
 */
class ContactBridgeManager(private val context: Context, private val scope: CoroutineScope) {
    private val TAG = Constants.TAG_CONTACT
    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Queries all system contacts with at least one phone number.
     * Broadcasts the name and number pairs to the bridge.
     */
    fun sendContactsToMac() {
        scope.launch {
            val contacts = mutableListOf<Contact>()
            try {
                // Query only relevant fields: Display Name and Phone Number
                val cursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null,
                    null,
                    null
                )
                
                cursor?.use {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val name = it.getString(nameIndex) ?: "Unknown"
                        val number = it.getString(numberIndex) ?: ""
                        contacts.add(Contact(name, number))
                    }
                }
                
                if (contacts.isNotEmpty()) {
                    Log.d(TAG, "sendContactsToMac: Broadcasting ${contacts.size} contacts")
                    val json = Json.encodeToString(contacts)
                    BridgeManager.broadcastMessage(BridgeMessage(MessageTypes.CONTACTS, json))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Contacts access error: ${e.localizedMessage}")
            }
        }
    }
}
