package com.maca.bridge

import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.maca.bridge.databinding.ActivityTrackpadBinding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TrackpadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrackpadBinding
    private lateinit var gestureDetector: GestureDetector
    
    private var lastX = 0f
    private var lastY = 0f
    private var isTwoFingerTap = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackpadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }

        setupGestures()
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isTwoFingerTap) {
                    sendMouseEvent("LEFT_CLICK")
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (!isTwoFingerTap) {
                    sendMouseEvent("RIGHT_CLICK")
                }
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        val x = event.rawX
        val y = event.rawY

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x
                lastY = y
                isTwoFingerTap = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    isTwoFingerTap = true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2 && isTwoFingerTap) {
                    // It was a two-finger tap
                    sendMouseEvent("RIGHT_CLICK")
                    isTwoFingerTap = false // Reset
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY
                
                if (event.pointerCount == 1) {
                    // Normal Move
                    sendMouseEvent("MOVE", dx, dy)
                } else if (event.pointerCount == 2) {
                    // Two-finger scroll
                    // dy is the delta from the last move event
                    sendMouseEvent("SCROLL", 0f, dy)
                }

                lastX = x
                lastY = y
            }
        }
        return true
    }

    private fun sendMouseEvent(type: String, dx: Float = 0f, dy: Float = 0f) {
        val payload = MouseEventPayload(type, dx, dy)
        val message = BridgeMessage(MessageTypes.MOUSE_EVENT, Json.encodeToString(payload))
        BridgeManager.broadcastMessage(message)
    }
}
