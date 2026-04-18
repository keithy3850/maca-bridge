package com.maca.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val bundle = intent.extras
            if (bundle != null) {
                @Suppress("DEPRECATION")
                val pdus = bundle["pdus"] as Array<*>
                val format = bundle.getString("format")
                for (pdu in pdus) {
                    val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        SmsMessage.createFromPdu(pdu as ByteArray, format)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsMessage.createFromPdu(pdu as ByteArray)
                    }
                    val sender = sms.originatingAddress ?: "Unknown"
                    val body = sms.messageBody ?: ""
                    
                    // Notify BridgeService with structured payload
                    val serviceIntent = Intent(context, BridgeService::class.java).apply {
                        action = Constants.ACTION_SMS_RECEIVED
                        putExtra("sender", sender)
                        putExtra("body", body)
                        // We actually need the service to use the same logic as onStartCommand
                    }
                    context?.startService(serviceIntent)
                }
            }
        }
    }
}
