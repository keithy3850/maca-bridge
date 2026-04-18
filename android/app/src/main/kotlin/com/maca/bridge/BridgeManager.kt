package com.maca.bridge

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object BridgeManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessions = ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>()
    private val authenticatedIps = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val json = Json { encodeDefaults = true }

    fun addSession(session: DefaultWebSocketServerSession) {
        sessions[session] = false // Initial state: Unauthenticated
    }

    fun removeSession(session: DefaultWebSocketServerSession) {
        sessions.remove(session)
    }

    fun authenticateSession(session: DefaultWebSocketServerSession) {
        sessions[session] = true
        val ip = session.call.request.local.remoteHost
        authenticatedIps.add(ip)
    }

    fun isIpAuthenticated(ip: String): Boolean {
        return authenticatedIps.contains(ip)
    }

    fun broadcastMessage(message: BridgeMessage) {
        val jsonString = json.encodeToString(message)
        if (message.type == MessageTypes.MOUSE_EVENT) {
            // Log once per second to avoid flooding but still confirm activity
            // Actually, let's just log every message for this critical debug phase
            Log.d("BridgeManager", "Broadcasting ${message.type} to ${sessions.size} sessions")
        }
        scope.launch {
            sessions.forEach { (session, isAuthenticated) ->
                try {
                    if (session.isActive) {
                        if (isAuthenticated || message.type == MessageTypes.AUTH_RESPONSE) {
                            session.send(jsonString)
                        } else {
                            Log.w("BridgeManager", "Refusing to send ${message.type} to unauthenticated session")
                        }
                    } else {
                        sessions.remove(session)
                    }
                } catch (e: Exception) {
                    sessions.remove(session)
                }
            }
        }
    }
}
