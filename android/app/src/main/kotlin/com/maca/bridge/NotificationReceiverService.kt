package com.maca.bridge

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NotificationReceiverService : NotificationListenerService() {

    companion object {
        private var instance: NotificationReceiverService? = null
        
        fun getActiveNotifications(): Array<StatusBarNotification> {
            return instance?.activeNotifications ?: emptyArray()
        }
        
        fun dismissNotification(key: String) {
            instance?.cancelNotification(key)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("NotificationReceiver", "Service Created - Ready to intercept")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d("NotificationReceiver", "Listener Connected - System has bound the service")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        broadcastNotification(sbn ?: return)
    }

    private fun broadcastNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras
        
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val packageName = sbn.packageName ?: ""
        val key = sbn.key // Unique key for dismissal
        
        Log.d("NotificationReceiver", "Incoming notification from: $packageName")

        // Filter: Skip empty notifications
        if (title.isEmpty() && text.isEmpty()) return

        // Allow our test notification even if it's our own package
        if (packageName == applicationContext.packageName && title != "Maca Bridge Test") return

        val payload = Json.encodeToString(NotificationPayload(packageName, title, text, key))
        BridgeManager.broadcastMessage(BridgeMessage(MessageTypes.NOTIFICATION, payload))
    }

    fun syncAllNotifications() {
        activeNotifications.forEach { broadcastNotification(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Optional: Send a message to remove the notification on macOS
    }
}
