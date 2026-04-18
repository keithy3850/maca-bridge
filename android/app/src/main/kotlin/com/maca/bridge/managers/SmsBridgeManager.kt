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
     * Uses the Conversations URI for better performance.
     */
    fun sendSmsHistoryToMac() {
        scope.launch {
            val history = mutableMapOf<String, List<HistoricalSms>>()
            try {
                // 1. Get the 5 most recent thread IDs
                val threadIds = mutableListOf<Long>()
                val threadCursor = contentResolver.query(
                    Telephony.Sms.Conversations.CONTENT_URI,
                    arrayOf(Telephony.Sms.Conversations.THREAD_ID),
                    null,
                    null,
                    "date DESC LIMIT 5"
                )
                
                threadCursor?.use {
                    val idIndex = it.getColumnIndex(Telephony.Sms.Conversations.THREAD_ID)
                    while (it.moveToNext()) {
                        threadIds.add(it.getLong(idIndex))
                    }
                }
                
                // 2. Fetch messages for each thread
                for (threadId in threadIds) {
                    val messages = mutableListOf<HistoricalSms>()
                    val smsCursor = contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                        "thread_id = ?",
                        arrayOf(threadId.toString()),
                        "date DESC LIMIT 20"
                    )
                    
                    var threadAddress: String? = null
                    smsCursor?.use {
                        val addrIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                        val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                        val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                        val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                        while (it.moveToNext()) {
                            val addr = it.getString(addrIndex) ?: "Unknown"
                            if (threadAddress == null) threadAddress = addr
                            val body = it.getString(bodyIndex) ?: ""
                            val date = it.getLong(dateIndex)
                            val type = it.getInt(typeIndex)
                            messages.add(HistoricalSms(addr, body, date, type))
                        }
                    }
                    if (messages.isNotEmpty() && threadAddress != null) {
                        history[threadAddress!!] = messages.reversed()
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
            val recipient = sms.sender
            val body = sms.body
            try {
                // Android 12+ requires specific SmsManager resolution via Subscription ID or Context
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault() 
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                
                val parts = smsManager.divideMessage(body)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(recipient, null, body, null, null)
                }
                Log.d(TAG, "SMS queued for sending to $recipient")
            } catch (e: Exception) { 
                Log.e(TAG, "System SMS error: ${e.localizedMessage}") 
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS payload decode error: ${e.localizedMessage}")
        }
    }
}
