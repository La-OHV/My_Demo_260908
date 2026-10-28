package com.example.mydemo260908

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onMove: ((x: Int, y: Int) -> Unit)? = null

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.joystick_surface)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.joystick_border)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.joystick_guide)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.joystick_knob)
        style = Paint.Style.FILL
    }
    private val knobHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private var knobX = 0f
    private var knobY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        resetKnob(notify = false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = baseRadius()

        canvas.drawCircle(centerX, centerY, radius, basePaint)
        canvas.drawCircle(centerX, centerY, radius, borderPaint)
        canvas.drawCircle(centerX, centerY, radius * 0.66f, guidePaint)
        canvas.drawCircle(centerX, centerY, radius * 0.33f, guidePaint)
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, guidePaint)
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, guidePaint)

        val knobRadius = knobRadius()
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint)
        canvas.drawOval(
            RectF(
                knobX - knobRadius * 0.45f,
                knobY - knobRadius * 0.52f,
                knobX + knobRadius * 0.25f,
                knobY - knobRadius * 0.05f,
            ),
            knobHighlightPaint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                updateKnob(event.x, event.y)
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex >= 0) {
                    updateKnob(event.getX(pointerIndex), event.getY(pointerIndex))
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                resetKnob(notify = true)
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                resetKnob(notify = true)
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun center() {
        resetKnob(notify = true)
    }

    private fun updateKnob(touchX: Float, touchY: Float) {
        val centerX = width / 2f
        val centerY = height / 2f
        val deltaX = touchX - centerX
        val deltaY = touchY - centerY
        val distance = hypot(deltaX, deltaY)
        val travelRadius = travelRadius()
        val scale = if (distance > travelRadius && distance > 0f) travelRadius / distance else 1f

        knobX = centerX + deltaX * scale
        knobY = centerY + deltaY * scale

        val normalizedX = ((knobX - centerX) / travelRadius * 100f).roundToInt()
        val normalizedY = ((centerY - knobY) / travelRadius * 100f).roundToInt()
        onMove?.invoke(normalizedX, normalizedY)
        invalidate()
    }

    private fun resetKnob(notify: Boolean) {
        knobX = width / 2f
        knobY = height / 2f
        if (notify) onMove?.invoke(0, 0)
        invalidate()
    }

    private fun baseRadius(): Float = min(width, height) * 0.49f

    private fun knobRadius(): Float = baseRadius() * 0.24f

    private fun travelRadius(): Float = (baseRadius() - knobRadius()).coerceAtLeast(1f)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
