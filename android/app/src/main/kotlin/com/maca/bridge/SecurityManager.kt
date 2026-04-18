package com.maca.bridge

import android.content.Context
import android.util.Log
import java.security.SecureRandom
import java.util.*

object SecurityManager {
    private const val TAG = "SecurityManager"
    private const val PREFS_NAME = "maca_bridge_security"
    private const val KEY_PIN = "pairing_pin"
    private const val KEY_TOKEN = "session_token"

    fun initialize(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.contains(KEY_PIN)) {
                val sr = SecureRandom()
                val randomPin = (100000 + sr.nextInt(900000)).toString()
                val sessionToken = UUID.randomUUID().toString()
                prefs.edit()
                    .putString(KEY_PIN, randomPin)
                    .putString(KEY_TOKEN, sessionToken)
                    .apply()
                Log.d(TAG, "SecurityManager: Initialized new pairing PIN and Token")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SecurityManager: Initialization failed: ${e.localizedMessage}")
        }
    }

    fun getPin(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PIN, "000000") ?: "000000"
    }

    fun getSessionToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, "") ?: ""
    }
}
