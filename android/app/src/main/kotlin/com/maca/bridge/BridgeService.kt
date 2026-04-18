package com.maca.bridge

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.maca.bridge.managers.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.content.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import android.content.ContentUris
import android.graphics.Bitmap

/**
 * Main background service that hosts the Ktor WebSocket server.
 * Manages the connection lifecycle, device discovery (NSD), and coordinates
 * between various feature managers (SMS, Media, Photos).
 */
class BridgeService : Service() {

    private val TAG = "MacaBridgeService"
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var server: ApplicationEngine? = null
    private var nsdManager: NsdManager? = null
    private val serviceType = "_maca-bridge._tcp."
    private val serviceName = "MacaBridge-${Build.MODEL}"
    
    private lateinit var mediaManager: MediaBridgeManager
    private lateinit var photoManager: PhotoBridgeManager
    private lateinit var smsManager: SmsBridgeManager
    private lateinit var contactManager: ContactBridgeManager

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "onServiceRegistered: ${serviceInfo.serviceName}")
        }
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "onRegistrationFailed: $errorCode")
        }
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service starting...")
        SecurityManager.initialize(this)
        
        mediaManager = MediaBridgeManager(this, scope)
        photoManager = PhotoBridgeManager(this, scope)
        smsManager = SmsBridgeManager(this, scope)
        contactManager = ContactBridgeManager(this, scope)

        try {
            startForegroundService()
            scope.launch {
                try {
                    startWebSocketServer()
                    registerService(8443)
                } catch (e: Exception) {
                    Log.e(TAG, "Startup Error: ${e.localizedMessage}")
                }
            }
            
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Constants.ACTION_SEND_CLIPBOARD)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(commonReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(commonReceiver, filter)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "FATAL: ${t.localizedMessage}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Constants.ACTION_SMS_RECEIVED) {
            val sender = intent.getStringExtra("sender") ?: "Unknown"
            val body = intent.getStringExtra("body") ?: ""
            val payload = Json.encodeToString(SmsPayload(sender, body))
            BridgeManager.broadcastMessage(BridgeMessage(MessageTypes.SMS, payload))
        }
        return START_STICKY
    }

    private val commonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_SEND_CLIPBOARD -> sendClipboardToMac()
                Intent.ACTION_BATTERY_CHANGED -> handleBatteryChange(intent)
            }
        }
    }

    private fun handleBatteryChange(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val payload = Json.encodeToString(BatteryPayload(level, isCharging))
        BridgeManager.broadcastMessage(BridgeMessage(MessageTypes.BATTERY, payload))
    }

    private fun sendClipboardToMac() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text.toString()
            if (!isSensitiveData(text)) {
                BridgeManager.broadcastMessage(BridgeMessage(MessageTypes.CLIPBOARD, text))
            }
        }
    }

    private fun isSensitiveData(text: String): Boolean {
        val ccRegex = Regex("\\b(?:\\d[ -]*?){13,16}\\b")
        if (ccRegex.containsMatchIn(text)) return true
        if (text.length in 8..32 && text.any { it.isDigit() } && text.any { !it.isLetterOrDigit() }) return true
        return false
    }

    private fun startWebSocketServer() {
        try {
            val keyStore = CertificateManager.getOrCreateKeyStore(applicationContext)
            val password = CertificateManager.getKeyPassword()

            val env = applicationEngineEnvironment {
                sslConnector(
                    keyStore = keyStore,
                    keyAlias = "maca_bridge_ssl",
                    keyStorePassword = { password.toCharArray() },
                    privateKeyPassword = { password.toCharArray() }
                ) {
                    port = 8443
                }

                module {
                    install(WebSockets)
                    install(ContentNegotiation) { json(Json { prettyPrint = true; isLenient = true; encodeDefaults = true }) }
                    
                    routing {
                        webSocket("/bridge") {
                            BridgeManager.addSession(this)
                            try {
                                for (frame in incoming) {
                                    if (frame is Frame.Text) handleIncomingMessage(frame.readText(), this)
                                }
                            } finally {
                                BridgeManager.removeSession(this)
                            }
                        }
                        
                        get("/photo/{id}") {
                            val token = call.request.headers["X-Bridge-Token"] ?: call.request.queryParameters["token"]
                            if (token.isNullOrEmpty() || token != SecurityManager.getSessionToken(applicationContext)) {
                                call.respond(io.ktor.http.HttpStatusCode.Unauthorized)
                            } else {
                                val id = call.parameters["id"]?.toLongOrNull()
                                if (id != null) {
                                    try {
                                        val uri = ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                                        contentResolver.openInputStream(uri)?.use { input ->
                                            call.respondOutputStream(io.ktor.http.ContentType.Image.JPEG) { input.copyTo(this) }
                                        }
                                    } catch (e: Exception) { call.respond(io.ktor.http.HttpStatusCode.NotFound) }
                                } else { call.respond(io.ktor.http.HttpStatusCode.BadRequest) }
                            }
                        }

                        get("/thumbnail/{id}") {
                            val token = call.request.headers["X-Bridge-Token"] ?: call.request.queryParameters["token"]
                            if (token.isNullOrEmpty() || token != SecurityManager.getSessionToken(applicationContext)) {
                                call.respond(io.ktor.http.HttpStatusCode.Unauthorized)
                            } else {
                                val id = call.parameters["id"]?.toLongOrNull()
                                if (id != null) {
                                    try {
                                        val uri = ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                                        val thumbnail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            contentResolver.loadThumbnail(uri, android.util.Size(300, 300), null)
                                        } else {
                                            @Suppress("DEPRECATION")
                                            android.provider.MediaStore.Images.Thumbnails.getThumbnail(contentResolver, id, android.provider.MediaStore.Images.Thumbnails.MINI_KIND, null)
                                        }
                                        
                                        if (thumbnail != null) {
                                            call.respondOutputStream(io.ktor.http.ContentType.Image.JPEG) {
                                                thumbnail.compress(Bitmap.CompressFormat.JPEG, 75, this)
                                            }
                                        } else {
                                            call.respond(io.ktor.http.HttpStatusCode.NotFound)
                                        }
                                    } catch (e: Exception) { call.respond(io.ktor.http.HttpStatusCode.NotFound) }
                                } else { call.respond(io.ktor.http.HttpStatusCode.BadRequest) }
                            }
                        }
                        
                        post("/upload") {
                            val token = call.request.headers["X-Bridge-Token"]
                            if (token.isNullOrEmpty() || token != SecurityManager.getSessionToken(applicationContext)) {
                                call.respond(io.ktor.http.HttpStatusCode.Unauthorized)
                            } else {
                                call.receiveMultipart().forEachPart { part ->
                                    if (part is PartData.FileItem) {
                                        val safeFileName = java.io.File(part.originalFileName ?: "file").name
                                        val file = java.io.File(getExternalFilesDir(null), safeFileName)
                                        part.streamProvider().use { input -> file.outputStream().buffered().use { output -> input.copyTo(output) } }
                                        showFileUploadNotification(safeFileName)
                                    }
                                    part.dispose()
                                }
                                call.respondText("File uploaded successfully")
                            }
                        }
                    }
                }
            }

            server = embeddedServer(Netty, env)
            server?.start(wait = false)        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Ktor server: ${e.localizedMessage}")
        }
    }

    private fun handleIncomingMessage(json: String, session: DefaultWebSocketServerSession) {
        try {
            val message = Json.decodeFromString<BridgeMessage>(json)

            if (message.type == MessageTypes.AUTH) {
                val pin = SecurityManager.getPin(applicationContext)
                val token = SecurityManager.getSessionToken(applicationContext)

                // Allow pairing with PIN OR re-auth with previous Token
                if (message.payload == pin || message.payload == token) {
                    BridgeManager.authenticateSession(session)
                    scope.launch {
                        session.send(Json.encodeToString(BridgeMessage(MessageTypes.AUTH_RESPONSE, token)))
                        sendInitialSync()
                    }
                } else {
                    scope.launch { session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid Auth")) }
                }
                return
            }

            when (message.type) {
                MessageTypes.PING -> {
                    scope.launch { session.send(Json.encodeToString(BridgeMessage(MessageTypes.PONG, "PONG"))) }
                    scope.launch(Dispatchers.Main) {
                        try {
                            val intent = Intent(this@BridgeService, FindPhoneActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            }
                            startActivity(intent)
                        } catch (e: Exception) { Log.e(TAG, "Ping Error: ${e.localizedMessage}") }
                    }
                }
                MessageTypes.CLIPBOARD -> {
                    scope.launch(Dispatchers.Main) {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Maca Bridge", message.payload))
                    }
                }
                MessageTypes.SMS -> smsManager.handleSendSms(message.payload)
                MessageTypes.MEDIA_CONTROL -> mediaManager.handleMediaControl(message.payload)
                MessageTypes.PHOTOS_REFRESH -> photoManager.sendRecentPhotosToMac()
                MessageTypes.PHOTOS_LOAD_MORE -> {
                    val offset = message.payload.toIntOrNull() ?: 0
                    photoManager.sendRecentPhotosToMac(offset)
                }
                MessageTypes.NOTIFICATION_DISMISS -> {
                    NotificationReceiverService.dismissNotification(message.payload)
                }
                MessageTypes.URL -> openUrl(message.payload)
            }
        } catch (e: Exception) { Log.e(TAG, "Parse error: ${e.localizedMessage}") }
    }

    private fun sendInitialSync() {
        mediaManager.tryInitialize()
        sendCurrentBatteryStatus()
        contactManager.sendContactsToMac()
        smsManager.sendSmsHistoryToMac()
        photoManager.sendRecentPhotosToMac()
        
        try {
            val active = NotificationReceiverService.getActiveNotifications()
            active.forEach { sbn ->
                val notification = sbn.notification ?: return@forEach
                val extras = notification.extras
                val title = extras.getString("android.title") ?: ""
                val text = extras.getCharSequence("android.text")?.toString() ?: ""
                val packageName = sbn.packageName ?: ""
                
                val payload = Json.encodeToString(NotificationPayload(packageName, title, text, sbn.key))
                BridgeManager.broadcastMessage(BridgeMessage(MessageTypes.NOTIFICATION, payload))
            }
        } catch (e: Exception) { Log.e(TAG, "Initial Notification Sync Error: ${e.localizedMessage}") }
    }

    private fun openUrl(url: String) {
        val uri = android.net.Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        
        if (scheme != "http" && scheme != "https") {
            Log.w(TAG, "Blocked dangerous URL scheme: $scheme")
            return
        }

        scope.launch(Dispatchers.Main) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) { Log.e(TAG, "URL Error: ${e.localizedMessage}") }
        }
    }

    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = this@BridgeService.serviceName
            this.serviceType = this@BridgeService.serviceType
            this.setPort(port)
        }
        nsdManager = (getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }
    }

    private fun startForegroundService() {
        val channelId = Constants.CHANNEL_ID_SERVICE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(channelId, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH))
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title_active))
            .setContentText(getString(R.string.notification_text_listening))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_send, getString(R.string.notification_action_send_clipboard), pendingIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    private fun showFileUploadNotification(fileName: String) {
        val channelId = Constants.CHANNEL_ID_FILE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(channelId, getString(R.string.file_transfer_channel_name), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.file_received_title))
            .setContentText(getString(R.string.file_received_text, fileName))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(2, notification)
    }

    private fun sendCurrentBatteryStatus() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, filter)
        handleBatteryChange(batteryStatus ?: return)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        server?.stop(1000, 2000)
        unregisterReceiver(commonReceiver)
        nsdManager?.unregisterService(registrationListener)
        mediaManager.cleanup()
        job.cancel()
    }
}