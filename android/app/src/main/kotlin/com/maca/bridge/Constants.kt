package com.maca.bridge

object Constants {
    const val TAG_SERVICE = "MacaBridgeService"
    const val TAG_UI = "MacaBridgeUI"
    const val TAG_MEDIA = "MediaBridgeManager"
    const val TAG_PHOTO = "PhotoBridgeManager"
    const val TAG_SMS = "SmsBridgeManager"
    const val TAG_CONTACT = "ContactBridgeManager"

    const val CHANNEL_ID_SERVICE = "maca_bridge_channel"
    const val CHANNEL_NAME_SERVICE = "Maca Bridge"
    
    const val CHANNEL_ID_FILE = "maca_bridge_file_channel"
    const val CHANNEL_NAME_FILE = "File Transfers"

    const val ACTION_SEND_CLIPBOARD = "com.maca.bridge.ACTION_SEND_CLIPBOARD"
    const val ACTION_SMS_RECEIVED = "com.maca.bridge.ACTION_SMS_RECEIVED"
    
    const val PREFS_SECURITY = "maca_bridge_security"
    const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/keithy3850/maca-bridge/main/update.json"
}
