package com.maca.bridge

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log

import android.widget.Toast
import android.os.Handler
import android.os.Looper

class ClipboardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Short delay to ensure focus
        Handler(Looper.getMainLooper()).postDelayed({
            grabClipboard()
        }, 200)
    }

    private fun grabClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text.toString()
                Log.d("MacaBridge", "ClipboardActivity: Grabbed text: $text")
                BridgeManager.broadcastMessage(BridgeMessage(MessageTypes.CLIPBOARD, text))
                Toast.makeText(this, "Clipboard sent to Mac", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("MacaBridge", "ClipboardActivity: Clipboard was empty")
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MacaBridge", "ClipboardActivity Error: ${e.localizedMessage}")
            Toast.makeText(this, "Failed to grab clipboard", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
