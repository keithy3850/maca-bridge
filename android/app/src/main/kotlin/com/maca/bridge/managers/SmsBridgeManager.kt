package com.maca.bridge.managers

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.maca.bridge.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages SMS history synchronization and sending.
 * Interfaces with the Telephony content provider to fetch threads and the
 * system SmsManager to dispatch new messages.
 */
class SmsBridgeManager(private val context: Context, private val scope: CoroutineScope) {
    private val TAG = Constants.TAG_SMS
    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Fetches the 5 most recently active SMS threads and their last 20 messages.
     * Groups them by sender and broadcasts the history to the bridge.
     */
    fun sendSmsHistoryToMac() {
        scope.launch {
            val history = mutableMapOf<String, List<HistoricalSms>>()
            try {
                // 1. Identify the 5 most recent active unique addresses
                val addresses = mutableListOf<String>()
                val addressCursor = contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )
                
                addressCursor?.use {
                    val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    while (it.moveToNext() && addresses.size < 5) {
                        val addr = it.getString(addressIndex) ?: continue
                        if (!addresses.contains(addr)) {
                            addresses.add(addr)
                        }
                    }
                }
                
                // 2. Fetch the last 20 messages for each identified address
                for (address in addresses) {
                    val messages = mutableListOf<HistoricalSms>()
                    val smsCursor = contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                        "${Telephony.Sms.ADDRESS} = ?",
                        arrayOf(address),
                        "${Telephony.Sms.DATE} DESC LIMIT 20"
                    )
                    
                    smsCursor?.use {
                        val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                        val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                        val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                        while (it.moveToNext()) {
                            val body = it.getString(bodyIndex) ?: ""
                            val date = it.getLong(dateIndex)
                            val type = it.getInt(typeIndex)
                            messages.add(HistoricalSms(address, body, date, type))
                        }
                    }
                    if (messages.isNotEmpty()) {
                        // Reverse history so it arrives in chronological order (oldest first)
                        history[address] = messages.reversed()
                    }
                }
                
                if (history.isNotEmpty()) {
                    val json = Json.encodeToString(history)
                    BridgeManager.broadcastMessage(BridgeMessage(MessageTypes.SMS_SYNC, json))
                }
            } catch (e: Exception) {
                Log.e(TAG, "SMS History error: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Decodes an SmsPayload and dispatches a system SMS to the specified recipient.
     */
    fun handleSendSms(payload: String) {
        try {
            val sms = Json.decodeFromString<SmsPayload>(payload)
            val recipient = sms.sender // 'sender' field holds the recipient when sending from Mac
            val body = sms.body
            try {
                // Use API-specific SmsManager resolution
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager?.sendTextMessage(recipient, null, body, null, null)
                Log.d(TAG, "SMS sent successfully to $recipient")
            } catch (e: Exception) { 
                Log.e(TAG, "System SMS error: ${e.localizedMessage}") 
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS payload decode error: ${e.localizedMessage}")
        }
    }
}
