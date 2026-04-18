package com.maca.bridge.managers

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.maca.bridge.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages photo gallery access and synchronization.
 * Queries the device's MediaStore for recent images and beams their metadata
 * to the macOS client, which can then fetch full-res data via HTTP.
 */
class PhotoBridgeManager(private val context: Context, private val scope: CoroutineScope) {
    private val TAG = Constants.TAG_PHOTO
    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Queries the system MediaStore for a page of 50 photos.
     * @param offset the starting position in the query results
     */
    fun sendRecentPhotosToMac(offset: Int = 0) {
        Log.d(TAG, "sendRecentPhotosToMac: Starting refined query (offset: $offset)...")
        scope.launch {
            val photos = mutableListOf<PhotoMetadata>()
            // Always check external first as that's where user photos live
            val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            
            try {
                val projection = arrayOf(
                    android.provider.MediaStore.Images.Media._ID,
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    android.provider.MediaStore.Images.Media.DATE_ADDED,
                    android.provider.MediaStore.Images.Media.SIZE
                )
                
                val cursor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val queryArgs = android.os.Bundle().apply {
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 50)
                        putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
                        putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(android.provider.MediaStore.Images.Media.DATE_ADDED))
                        putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    }
                    contentResolver.query(uri, projection, queryArgs, null)
                } else {
                    val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 50 OFFSET $offset"
                    contentResolver.query(uri, projection, null, null, sortOrder)
                }

                cursor?.use { c ->
                    Log.d(TAG, "Querying $uri: Found ${c.count} items in current page")
                    
                    val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                    val dateCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_ADDED)
                    val sizeCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.SIZE)
                    
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol) ?: "image_$id.jpg"
                        val date = c.getLong(dateCol)
                        val size = c.getLong(sizeCol)
                        photos.add(PhotoMetadata(id, name, date, size))
                    }
                }
                
                if (photos.isNotEmpty()) {
                    Log.d(TAG, "sendRecentPhotosToMac: Sending ${photos.size} photos.")
                    val json = Json.encodeToString(photos)
                    // If offset is 0, this is a fresh list. Otherwise, it's a page of more photos.
                    val type = if (offset == 0) MessageTypes.PHOTOS_LIST else MessageTypes.PHOTOS_LOAD_MORE
                    BridgeManager.broadcastMessage(BridgeMessage(type, json))
                    
                    if (offset == 0) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Syncing latest photos...", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.d(TAG, "sendRecentPhotosToMac: No more photos found.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Photos Query Error: ${e.localizedMessage}")
            }
        }
    }
}
