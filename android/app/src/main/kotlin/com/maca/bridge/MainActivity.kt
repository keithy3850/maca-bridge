package com.maca.bridge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.maca.bridge.managers.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MainActivity : AppCompatActivity() {

    private val TAG = "MacaBridgeUI"
    private lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: BOOTING")
        
        try {
            // 1. Initialize logic
            SecurityManager.initialize(this)
            updateManager = UpdateManager(this, CoroutineScope(Dispatchers.Main))
            
            // 2. Set View
            setContentView(R.layout.activity_main)

            // 3. Late bind buttons with null safety
            setupUI()
            
            // 4. Update Check
            updateManager.checkForUpdates(silent = true)
            
            // 5. Permission check with delay
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) {
                    if (checkPermissions()) {
                        startBridgeService()
                    } else {
                        requestPermissions()
                    }
                }
            }, 1000)
            
            Log.d(TAG, "onCreate: BOOT SUCCESS")
        } catch (t: Throwable) {
            Log.e(TAG, "FATAL CRASH: ${t.localizedMessage}")
        }
    }

    private fun setupUI() {
        val btnNotif = findViewById<Button>(R.id.btnNotificationSettings)
        val btnPhoto = findViewById<Button>(R.id.btnPhotoSettings)
        val btnTrack = findViewById<Button>(R.id.btnTrackpad)

        btnNotif?.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        btnPhoto?.setOnClickListener {
            requestPermissions()
        }
        btnTrack?.setOnClickListener {
            startActivity(Intent(this, TrackpadActivity::class.java))
        }
        
        findViewById<TextView>(R.id.textView)?.setOnLongClickListener {
            updateManager.checkForUpdates(silent = false)
            true
        }
        
        refreshPinDisplay()
    }

    private fun refreshPinDisplay() {
        val pin = SecurityManager.getPin(this)
        val tv = findViewById<TextView>(R.id.textView)
        tv?.text = getString(R.string.bridge_active_pin, pin)
    }

    override fun onResume() {
        super.onResume()
        updateNotificationStatus()
        updatePhotoStatus()
        refreshPinDisplay()
    }

    private fun updateNotificationStatus() {
        val isEnabled = isNotificationServiceEnabled()
        val tv = findViewById<TextView>(R.id.tvNotificationStatus)
        val btn = findViewById<Button>(R.id.btnNotificationSettings)
        
        if (isEnabled) {
            tv?.text = getString(R.string.notification_listener_enabled)
            tv?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            btn?.visibility = View.GONE
        } else {
            tv?.text = getString(R.string.notification_listener_disabled)
            tv?.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btn?.visibility = View.VISIBLE
        }
    }

    private fun updatePhotoStatus() {
        val isGranted = isPhotoPermissionGranted()
        val tv = findViewById<TextView>(R.id.tvPhotoStatus)
        val btn = findViewById<Button>(R.id.btnPhotoSettings)
        
        if (isGranted) {
            tv?.text = getString(R.string.gallery_access_granted)
            tv?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            btn?.visibility = View.GONE
        } else {
            tv?.text = getString(R.string.gallery_access_denied)
            tv?.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btn?.visibility = View.VISIBLE
        }
    }

    private fun isPhotoPermissionGranted(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val cn = android.content.ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) return true
            }
        }
        return false
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS); permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS); permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) startBridgeService()
    }

    private fun startBridgeService() {
        val intent = Intent(this, BridgeService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) { Log.e(TAG, "Start Error: ${e.localizedMessage}") }
    }
}
