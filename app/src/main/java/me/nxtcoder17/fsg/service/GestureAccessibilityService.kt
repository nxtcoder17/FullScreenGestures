package me.nxtcoder17.fsg.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class GestureAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        var isRunning = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var leftOverlay: GestureOverlayView? = null
    private var rightOverlay: GestureOverlayView? = null
    private var bottomOverlay: GestureOverlayView? = null

    private lateinit var prefs: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("fsg_settings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)

        updateOverlays()
    }

    override fun onInterrupt() {
        // Required method by AccessibilityService
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for gesture capture
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
        }
        removeOverlays()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Redraw overlays on configuration or orientation change
        updateOverlays()
    }

    private fun updateOverlays() {
        removeOverlays()
        addOverlays()
    }

    private fun addOverlays() {
        val wm = windowManager ?: return

        val leftEnabled = prefs.getBoolean("left_edge_enabled", true)
        val rightEnabled = prefs.getBoolean("right_edge_enabled", true)
        val bottomEnabled = prefs.getBoolean("bottom_edge_enabled", true)

        val leftThickness = prefs.getInt("left_edge_thickness", 15)
        val rightThickness = prefs.getInt("right_edge_thickness", 15)
        val bottomThickness = prefs.getInt("bottom_edge_thickness", 15)

        val sensitivity = prefs.getInt("gesture_sensitivity", 40)
        val haptics = prefs.getBoolean("haptics_enabled", true)
        val visualFeedback = prefs.getBoolean("visual_feedback_enabled", true)

        val density = resources.displayMetrics.density

        if (leftEnabled) {
            val view = GestureOverlayView(this, GestureOverlayView.Edge.LEFT) { action ->
                performGlobalAction(action)
            }
            view.gestureSensitivityDp = sensitivity
            view.hapticsEnabled = haptics
            view.visualFeedbackEnabled = visualFeedback
            view.thicknessPx = (leftThickness * density).toInt()

            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                gravity = Gravity.LEFT or Gravity.TOP
                width = view.thicknessPx
                height = WindowManager.LayoutParams.MATCH_PARENT
                x = 0
                y = 0
            }
            view.windowLayoutParams = params
            try {
                wm.addView(view, params)
                leftOverlay = view
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (rightEnabled) {
            val view = GestureOverlayView(this, GestureOverlayView.Edge.RIGHT) { action ->
                performGlobalAction(action)
            }
            view.gestureSensitivityDp = sensitivity
            view.hapticsEnabled = haptics
            view.visualFeedbackEnabled = visualFeedback
            view.thicknessPx = (rightThickness * density).toInt()

            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                gravity = Gravity.RIGHT or Gravity.TOP
                width = view.thicknessPx
                height = WindowManager.LayoutParams.MATCH_PARENT
                x = 0
                y = 0
            }
            view.windowLayoutParams = params
            try {
                wm.addView(view, params)
                rightOverlay = view
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (bottomEnabled) {
            val view = GestureOverlayView(this, GestureOverlayView.Edge.BOTTOM) { action ->
                performGlobalAction(action)
            }
            view.gestureSensitivityDp = sensitivity
            view.hapticsEnabled = haptics
            view.visualFeedbackEnabled = visualFeedback
            view.thicknessPx = (bottomThickness * density).toInt()

            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                gravity = Gravity.BOTTOM or Gravity.LEFT
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = view.thicknessPx
                x = 0
                y = 0
            }
            view.windowLayoutParams = params
            try {
                wm.addView(view, params)
                bottomOverlay = view
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun removeOverlays() {
        val wm = windowManager ?: return
        
        leftOverlay?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
                // Ignore removal issues
            }
            leftOverlay = null
        }
        
        rightOverlay?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
                // Ignore removal issues
            }
            rightOverlay = null
        }
        
        bottomOverlay?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
                // Ignore removal issues
            }
            bottomOverlay = null
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Handler(Looper.getMainLooper()).post {
            updateOverlays()
        }
    }
}
