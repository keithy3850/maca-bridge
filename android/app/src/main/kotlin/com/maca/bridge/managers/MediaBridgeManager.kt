package com.maca.bridge.managers

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import com.maca.bridge.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

/**
 * Manages media session tracking and playback control.
 * Listens for active media sessions (Spotify, YouTube, etc.) and broadcasts
 * metadata (title, artist, album art) to the macOS client.
 */
class MediaBridgeManager(private val context: Context, private val scope: CoroutineScope) {
    private val TAG = Constants.TAG_MEDIA
    private var mediaSessionManager: MediaSessionManager? = null
    private val activeMediaControllers = mutableMapOf<MediaController, MediaController.Callback>()

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveMediaControllers(controllers)
    }

    init {
        mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        tryInitialize()
    }

    /**
     * Attempts to register the media session listener.
     * This may fail if the app does not have notification access.
     */
    fun tryInitialize() {
        val componentName = ComponentName(context, NotificationReceiverService::class.java)
        try {
            // Register for changes in active media sessions
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionListener, componentName)
            // Initialize with currently active sessions
            updateActiveMediaControllers(mediaSessionManager?.getActiveSessions(componentName))
            Log.d(TAG, "Media listener successfully registered")
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to register media listener: ${e.message}. Notification access probably missing.")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error initializing MediaBridgeManager: ${e.message}")
        }
    }

    /**
     * Cleans up listeners and callbacks to prevent memory leaks.
     */
    fun cleanup() {
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        activeMediaControllers.forEach { (controller, callback) ->
            controller.unregisterCallback(callback)
        }
        activeMediaControllers.clear()
    }

    /**
     * Updates the list of active media controllers and registers metadata callbacks for each.
     */
    private fun updateActiveMediaControllers(controllers: List<MediaController>?) {
        activeMediaControllers.forEach { (controller, callback) ->
            controller.unregisterCallback(callback)
        }
        activeMediaControllers.clear()

        controllers?.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    sendMediaMetadata(metadata)
                }
            }
            controller.registerCallback(callback)
            activeMediaControllers[controller] = callback
            // Send initial metadata for the newly discovered controller
            sendMediaMetadata(controller.metadata)
        }
    }

    /**
     * Extracts metadata and album art from a MediaMetadata object and broadcasts it to the bridge.
     */
    private fun sendMediaMetadata(metadata: MediaMetadata?) {
        if (metadata == null) return
        
        // Try various keys for title and artist as different apps use different standards
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "No Title"
            
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: "Unknown Artist"

        var base64Art: String? = null

        // 1. Try to get direct bitmap first
        var bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            
        // 2. If bitmap is missing, we could try to load from URI in future versions.
        // For now, let's at least ensure title/artist always sync.

        if (bitmap != null) {
            try {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                base64Art = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.e(TAG, "Bitmap compression failed: ${e.message}")
            }
        }

        val payload = MediaMetadataPayload(title, artist, base64Art)
        val message = BridgeMessage(MessageTypes.MEDIA_METADATA, Json.encodeToString(payload))
        BridgeManager.broadcastMessage(message)
    }

    /**
     * Dispatches media key events (Play/Pause, Next, Previous) to the system.
     */
    fun handleMediaControl(action: String) {
        scope.launch(Dispatchers.Main) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val keyEvent = when (action) {
                "PLAY_PAUSE" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                "NEXT" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "PREVIOUS" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> null
            }
            keyEvent?.let {
                // Dispatch DOWN and UP events to simulate a full button press
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, it))
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, it))
            }
        }
    }
}
