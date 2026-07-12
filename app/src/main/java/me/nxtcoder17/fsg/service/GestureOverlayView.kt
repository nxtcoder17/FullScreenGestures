package me.nxtcoder17.fsg.service

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.min

@SuppressLint("ViewConstructor")
class GestureOverlayView(
    context: Context,
    val edge: Edge,
    private val onGestureTriggered: (Int) -> Unit
) : View(context) {

    enum class Edge { LEFT, RIGHT, BOTTOM }

    var windowLayoutParams: WindowManager.LayoutParams? = null
    var thicknessPx: Int = (15 * resources.displayMetrics.density).toInt()
    var gestureSensitivityDp: Int = 40
    var hapticsEnabled: Boolean = true
    var visualFeedbackEnabled: Boolean = true
    var isTouchableAllowed: Boolean = true

    private val density = resources.displayMetrics.density
    private var startX = 0f
    private var startY = 0f
    private var currentTouchX = 0f
    private var currentTouchY = 0f
    private var dragDistance = 0f
    var isGestureActive = false
    private var isThresholdCrossed = false
    private var isHoldTriggered = false

    // State Animators for fluid motion
    private var thresholdTransitionProgress = 0f
    private var thresholdAnimator: ValueAnimator? = null

    private var arrowBounceScale = 1.0f
    private var arrowBounceAnimator: ValueAnimator? = null

    private var holdProgress = 0f
    private var holdAnimator: ValueAnimator? = null

    private val holdHandler = Handler(Looper.getMainLooper())

    private val makeTouchableRunnable = Runnable {
        shrinkLayout(makeTouchable = true)
    }

    private val safetyShrinkRunnable = Runnable {
        shrinkLayout(makeTouchable = true)
        isGestureActive = false
        dragDistance = 0f
        isThresholdCrossed = false
        isHoldTriggered = false
        animateThresholdState(false)
        animateHoldState(false)
        (context as? GestureAccessibilityService)?.checkAndApplyOverlaysLifecycle()
    }

    private fun releaseTouchGrab() {
        shrinkLayout(makeTouchable = false)
        holdHandler.removeCallbacks(makeTouchableRunnable)
        holdHandler.postDelayed(makeTouchableRunnable, 250)
    }

    private val holdRunnable = Runnable {
        if (isGestureActive && isThresholdCrossed) {
            isHoldTriggered = true
            triggerHapticFeedback()
            animateHoldState(true)
            onGestureTriggered(AccessibilityService.GLOBAL_ACTION_RECENTS)
            releaseTouchGrab() // Shrink immediately and make completely touch-transparent
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    var activeColor = Color.parseColor("#B06650A4") // Theme purple
    private val inactiveColor = Color.parseColor("#601A1A1A") // Transparent dark

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    init {
        setWillNotDraw(false)
    }

    private fun triggerHapticFeedback() {
        if (!hapticsEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(35)
            }
        } catch (e: Exception) {
            // Safe catch
        }
    }

    private fun animateThresholdState(crossed: Boolean) {
        thresholdAnimator?.cancel()
        val target = if (crossed) 1f else 0f
        thresholdAnimator = ValueAnimator.ofFloat(thresholdTransitionProgress, target).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                thresholdTransitionProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun triggerArrowBounce() {
        arrowBounceAnimator?.cancel()
        arrowBounceAnimator = ValueAnimator.ofFloat(1.0f, 1.4f, 0.9f, 1.0f).apply {
            duration = 320
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                arrowBounceScale = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animateHoldState(active: Boolean) {
        holdAnimator?.cancel()
        val target = if (active) 1f else 0f
        holdAnimator = ValueAnimator.ofFloat(holdProgress, target).apply {
            duration = 250
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { animator ->
                holdProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun interpolateColor(colorStart: Int, colorEnd: Int, fraction: Float): Int {
        val startA = (colorStart shr 24) and 0xff
        val startR = (colorStart shr 16) and 0xff
        val startG = (colorStart shr 8) and 0xff
        val startB = colorStart and 0xff

        val endA = (colorEnd shr 24) and 0xff
        val endR = (colorEnd shr 16) and 0xff
        val endG = (colorEnd shr 8) and 0xff
        val endB = colorEnd and 0xff

        return ((startA + (fraction * (endA - startA)).toInt()) shl 24) or
               ((startR + (fraction * (endR - startR)).toInt()) shl 16) or
               ((startG + (fraction * (endG - startG)).toInt()) shl 8) or
               (startB + (fraction * (endB - startB)).toInt())
    }

    fun updateThickness(thicknessDp: Int) {
        thicknessPx = (thicknessDp * density).toInt()
        if (!isGestureActive) {
            shrinkLayout(makeTouchable = true)
        }
    }

    fun setTouchable(touchable: Boolean) {
        isTouchableAllowed = touchable
        if (!isGestureActive) {
            shrinkLayout(makeTouchable = touchable)
        }
    }

    private fun expandLayout() {
        if (edge == Edge.BOTTOM) return // Never expand bottom overlay! Keep it thin.
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = windowLayoutParams ?: return
        when (edge) {
            Edge.LEFT, Edge.RIGHT -> {
                params.width = WindowManager.LayoutParams.MATCH_PARENT
            }
            Edge.BOTTOM -> {}
        }
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                       WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                       WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        try {
            wm.updateViewLayout(this, params)
            
            // Safety timeout: shrink the layout if ACTION_UP or CANCEL is lost
            holdHandler.removeCallbacks(safetyShrinkRunnable)
            holdHandler.postDelayed(safetyShrinkRunnable, 800) // 800ms limit
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun shrinkLayout(makeTouchable: Boolean = true) {
        // Cancel safety timeout since we are shrinking normally
        holdHandler.removeCallbacks(safetyShrinkRunnable)
        
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = windowLayoutParams ?: return
        when (edge) {
            Edge.LEFT, Edge.RIGHT -> {
                params.width = thicknessPx
            }
            Edge.BOTTOM -> {
                params.height = thicknessPx
            }
        }
        var targetFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                          WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                          WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val finalTouchable = makeTouchable && isTouchableAllowed
        if (!finalTouchable) {
            targetFlags = targetFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        params.flags = targetFlags
        try {
            wm.updateViewLayout(this, params)
        } catch (e: Exception) {
            // Ignore
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rawX = event.rawX
        val rawY = event.rawY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = rawX
                startY = rawY
                currentTouchX = rawX
                currentTouchY = rawY
                isGestureActive = true
                isThresholdCrossed = false
                isHoldTriggered = false
                dragDistance = 0f
                thresholdTransitionProgress = 0f
                holdProgress = 0f

                if (edge == Edge.BOTTOM) {
                    holdHandler.postDelayed(holdRunnable, 280)
                }

                expandLayout()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isGestureActive) return false

                currentTouchX = rawX
                currentTouchY = rawY

                val thresholdPx = gestureSensitivityDp * density

                when (edge) {
                    Edge.LEFT -> {
                        dragDistance = rawX - startX
                        if (dragDistance < 0) dragDistance = 0f
                        
                        if (dragDistance >= thresholdPx) {
                            if (!isThresholdCrossed) {
                                isThresholdCrossed = true
                                triggerHapticFeedback()
                                animateThresholdState(true)
                                triggerArrowBounce()
                            }
                        } else {
                            if (isThresholdCrossed) {
                                isThresholdCrossed = false
                                animateThresholdState(false)
                            }
                        }
                    }
                    Edge.RIGHT -> {
                        dragDistance = startX - rawX
                        if (dragDistance < 0) dragDistance = 0f

                        if (dragDistance >= thresholdPx) {
                            if (!isThresholdCrossed) {
                                isThresholdCrossed = true
                                triggerHapticFeedback()
                                animateThresholdState(true)
                                triggerArrowBounce()
                            }
                        } else {
                            if (isThresholdCrossed) {
                                isThresholdCrossed = false
                                animateThresholdState(false)
                            }
                        }
                    }
                    Edge.BOTTOM -> {
                        dragDistance = startY - rawY
                        if (dragDistance < 0) dragDistance = 0f

                        if (dragDistance >= thresholdPx) {
                            if (!isThresholdCrossed) {
                                isThresholdCrossed = true
                            }
                        } else {
                            isThresholdCrossed = false
                        }
                    }
                }

                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isGestureActive) return false
                isGestureActive = false

                holdHandler.removeCallbacks(holdRunnable)

                if (isThresholdCrossed) {
                    if (edge == Edge.BOTTOM) {
                        if (!isHoldTriggered) {
                            onGestureTriggered(AccessibilityService.GLOBAL_ACTION_HOME)
                            triggerHapticFeedback()
                        }
                    } else {
                        onGestureTriggered(AccessibilityService.GLOBAL_ACTION_BACK)
                    }
                }

                dragDistance = 0f
                isThresholdCrossed = false
                isHoldTriggered = false
                
                animateThresholdState(false)
                animateHoldState(false)

                holdHandler.removeCallbacks(makeTouchableRunnable)
                holdHandler.removeCallbacks(safetyShrinkRunnable)
                shrinkLayout(makeTouchable = true)
                (context as? GestureAccessibilityService)?.checkAndApplyOverlaysLifecycle()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!visualFeedbackEnabled) return

        if (isGestureActive && dragDistance > 0) {
            when (edge) {
                Edge.LEFT -> drawLeftBulge(canvas)
                Edge.RIGHT -> drawRightBulge(canvas)
                Edge.BOTTOM -> drawBottomPill(canvas)
            }
        }
    }

    private fun drawLeftBulge(canvas: Canvas) {
        val maxStretch = 70f * density
        val bulgeDepth = min(dragDistance * 0.4f, maxStretch)
        val currentY = currentTouchY

        // Interpolate background color based on threshold transition
        paint.color = interpolateColor(inactiveColor, activeColor, thresholdTransitionProgress)

        val path = Path()
        // Height decreases as stretch tension increases, making the bulge pointier!
        val baseHalfHeight = 110f
        val currentHalfHeight = (baseHalfHeight - 25f * thresholdTransitionProgress) * density
        
        path.moveTo(0f, currentY - currentHalfHeight)
        path.cubicTo(
            0f, currentY - currentHalfHeight / 2f,
            bulgeDepth, currentY - currentHalfHeight / 3f,
            bulgeDepth, currentY
        )
        path.cubicTo(
            bulgeDepth, currentY + currentHalfHeight / 3f,
            0f, currentY + currentHalfHeight / 2f,
            0f, currentY + currentHalfHeight
        )
        path.lineTo(0f, currentY - currentHalfHeight)
        path.close()

        canvas.drawPath(path, paint)

        val thresholdPx = gestureSensitivityDp * density
        val alpha = (dragDistance / thresholdPx).coerceIn(0f, 1f)
        arrowPaint.alpha = (alpha * 255).toInt()

        if (alpha > 0.1f) {
            // Apply bounce scale to arrow drawing
            val arrowSize = 6f * density * arrowBounceScale
            val arrowX = (bulgeDepth - (14f + 3f * thresholdTransitionProgress) * density).coerceAtLeast(4f * density)
            val arrowPathInward = Path().apply {
                moveTo(arrowX - arrowSize, currentY - arrowSize)
                lineTo(arrowX, currentY)
                lineTo(arrowX - arrowSize, currentY + arrowSize)
            }
            canvas.drawPath(arrowPathInward, arrowPaint)
        }
    }

    private fun drawRightBulge(canvas: Canvas) {
        val screenWidth = width.toFloat()
        val maxStretch = 70f * density
        val bulgeDepth = min(dragDistance * 0.4f, maxStretch)
        val currentY = currentTouchY

        paint.color = interpolateColor(inactiveColor, activeColor, thresholdTransitionProgress)

        val path = Path()
        val baseHalfHeight = 110f
        val currentHalfHeight = (baseHalfHeight - 25f * thresholdTransitionProgress) * density
        
        path.moveTo(screenWidth, currentY - currentHalfHeight)
        path.cubicTo(
            screenWidth, currentY - currentHalfHeight / 2f,
            screenWidth - bulgeDepth, currentY - currentHalfHeight / 3f,
            screenWidth - bulgeDepth, currentY
        )
        path.cubicTo(
            screenWidth - bulgeDepth, currentY + currentHalfHeight / 3f,
            screenWidth, currentY + currentHalfHeight / 2f,
            screenWidth, currentY + currentHalfHeight
        )
        path.lineTo(screenWidth, currentY - currentHalfHeight)
        path.close()

        canvas.drawPath(path, paint)

        val thresholdPx = gestureSensitivityDp * density
        val alpha = (dragDistance / thresholdPx).coerceIn(0f, 1f)
        arrowPaint.alpha = (alpha * 255).toInt()

        if (alpha > 0.1f) {
            val arrowSize = 6f * density * arrowBounceScale
            val arrowX = (screenWidth - bulgeDepth + (14f + 3f * thresholdTransitionProgress) * density).coerceAtMost(screenWidth - 4f * density)
            val arrowPathInward = Path().apply {
                moveTo(arrowX + arrowSize, currentY - arrowSize)
                lineTo(arrowX, currentY)
                lineTo(arrowX + arrowSize, currentY + arrowSize)
            }
            canvas.drawPath(arrowPathInward, arrowPaint)
        }
    }

    private fun drawBottomPill(canvas: Canvas) {
        val thresholdPx = gestureSensitivityDp * density
        val progress = (dragDistance / thresholdPx).coerceIn(0f, 1f)

        // Pill width starts at 70dp, expands to 100dp as you drag
        val baseWidth = 70f * density
        val currentWidth = baseWidth + (progress * 30f * density)

        // Pill height starts at 3dp, grows to 4dp
        val pillHeight = (3f + progress * 1f) * density

        val centerX = width / 2f
        val left = centerX - currentWidth / 2f
        val right = centerX + currentWidth / 2f

        // Rises up from the bottom of the 15dp overlay
        val maxRise = 8f * density
        val bottomY = height.toFloat() - 2f * density
        val top = bottomY - pillHeight - (progress * maxRise)
        val bottom = bottomY - (progress * maxRise)

        // Gradient color based on selected active color
        paint.color = interpolateColor(inactiveColor, activeColor, progress)

        val rect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(rect, pillHeight / 2f, pillHeight / 2f, paint)

        // Draw soft breathing glow once swiped past 50% threshold
        if (progress > 0.5f) {
            glowPaint.color = activeColor
            glowPaint.alpha = ((progress - 0.5f) * 2f * 80).toInt() // Max alpha 80
            val glowRect = RectF(left - 6f * density, top - 3f * density, right + 6f * density, bottom + 3f * density)
            canvas.drawRoundRect(glowRect, (pillHeight + 6f * density) / 2f, (pillHeight + 6f * density) / 2f, glowPaint)
        }
    }
}
