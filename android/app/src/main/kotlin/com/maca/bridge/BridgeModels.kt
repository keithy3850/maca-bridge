package com.maca.bridge

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val name: String,
    val number: String
)

@Serializable
data class HistoricalSms(
    val address: String,
    val body: String,
    val date: Long,
    val type: Int // 1 = received, 2 = sent
)

@Serializable
data class BatteryPayload(val level: Int, val isCharging: Boolean)

@Serializable
data class SmsPayload(val sender: String, val body: String)

@Serializable
data class NotificationPayload(val packageName: String, val title: String, val body: String, val key: String? = null)

@Serializable
data class PhotoMetadata(
    val id: Long,
    val name: String,
    val date: Long,
    val size: Long
)

@Serializable
data class MediaMetadataPayload(
    val title: String,
    val artist: String,
    val base64Art: String? = null
)

@Serializable
data class MouseEventPayload(
    val type: String, // "MOVE", "LEFT_CLICK", "RIGHT_CLICK", "SCROLL"
    val dx: Float = 0f,
    val dy: Float = 0f
)

@Serializable
data class BridgeMessage(
    val type: String,
    val payload: String, // Can be nested JSON or simple strings
    val timestamp: Long = System.currentTimeMillis()
)

object MessageTypes {
    const val PING = "PING"
    const val PONG = "PONG"
    const val NOTIFICATION = "NOTIFICATION"
    const val NOTIFICATION_DISMISS = "NOTIFICATION_DISMISS"
    const val NOTIFICATIONS_SYNC = "NOTIFICATIONS_SYNC"
    const val SMS = "SMS"
    const val CLIPBOARD = "CLIPBOARD"
    const val BATTERY = "BATTERY"
    const val MEDIA_CONTROL = "MEDIA_CONTROL"
    const val CONTACTS = "CONTACTS"
    const val URL = "URL"
    const val SMS_SYNC = "SMS_SYNC"
    const val MEDIA_METADATA = "MEDIA_METADATA"
    const val PHOTOS_LIST = "PHOTOS_LIST"
    const val PHOTOS_REFRESH = "PHOTOS_REFRESH"
    const val PHOTOS_LOAD_MORE = "PHOTOS_LOAD_MORE"
    const val AUTH = "AUTH"
    const val AUTH_RESPONSE = "AUTH_RESPONSE"
    const val MOUSE_EVENT = "MOUSE_EVENT"
}
