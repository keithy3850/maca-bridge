package com.maca.bridge.managers

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.maca.bridge.BuildConfig
import com.maca.bridge.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

/**
 * Manages the self-hosted update lifecycle for the Android app.
 * Checks for new versions on GitHub, handles APK downloads, and launches the installer.
 */
class UpdateManager(private val context: Context, private val scope: CoroutineScope) {
    private val TAG = "MacaUpdateManager"

    fun checkForUpdates(silent: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            try {
                // In a real scenario, this would be your GitHub raw JSON URL
                val jsonString = URL(Constants.UPDATE_JSON_URL).readText()
                val updateInfo = Json.decodeFromString<UpdateInfo>(jsonString)

                if (updateInfo.versionCode > BuildConfig.VERSION_CODE) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(updateInfo)
                    }
                } else if (!silent) {
                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(context)
                            .setTitle("Up to Date")
                            .setMessage("You are running the latest version.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed: ${e.localizedMessage}")
            }
        }
    }

    private fun showUpdateDialog(info: UpdateInfo) {
        AlertDialog.Builder(context)
            .setTitle("New Version Available: ${info.versionName}")
            .setMessage(info.releaseNotes)
            .setPositiveButton("Update Now") { _, _ ->
                startDownload(info.apkUrl)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startDownload(url: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Maca Bridge Update")
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "maca-bridge-update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // Register receiver to trigger install when download completes
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk()
                    context?.unregisterReceiver(this)
                }
            }
        }
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private fun installApk() {
        val file = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "maca-bridge-update.apk")
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}