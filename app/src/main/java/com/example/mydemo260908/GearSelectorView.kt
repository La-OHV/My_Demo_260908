package com.example.mydemo260908

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

class GearSelectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onGearChanged: ((Int) -> Unit)? = null

    var gear: Int = 1
        private set

    private val selectedColor: Int
    private val circlePath = Path()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gear_background)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gear_border)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gear_divider)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(15f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    init {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.GearSelectorView)
        selectedColor = attributes.getColor(
            R.styleable.GearSelectorView_selectedColor,
            ContextCompat.getColor(context, R.color.joystick_knob),
        )
        attributes.recycle()
        selectedPaint.color = selectedColor
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(width, height) / 2f - dp(2f)
        circlePath.reset()
        circlePath.addCircle(centerX, centerY, radius, Path.Direction.CW)

        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
        canvas.save()
        canvas.clipPath(circlePath)
        if (gear == 1) {
            canvas.drawRect(centerX - radius, centerY - radius, centerX + radius, centerY, selectedPaint)
        } else {
            canvas.drawRect(centerX - radius, centerY, centerX + radius, centerY + radius, selectedPaint)
        }
        canvas.restore()
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, dividerPaint)
        canvas.drawCircle(centerX, centerY, radius, borderPaint)

        val fontMetrics = textPaint.fontMetrics
        val textOffset = -(fontMetrics.ascent + fontMetrics.descent) / 2f
        for (position in 1..2) {
            textPaint.color = if (position == gear) {
                ContextCompat.getColor(context, R.color.white)
            } else {
                ContextCompat.getColor(context, R.color.remote_text)
            }
            val labelY = centerY + (if (position == 1) -radius / 2f else radius / 2f)
            canvas.drawText(position.toString(), centerX, labelY + textOffset, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                invalidate()
                true
            }

            MotionEvent.ACTION_UP -> {
                val shouldClick = isPressed
                isPressed = false
                invalidate()
                if (shouldClick) performClick()
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                invalidate()
                true
            }

            else -> true
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        gear = if (gear == 1) 2 else 1
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onGearChanged?.invoke(gear)
        contentDescription = context.getString(R.string.gear_accessibility, gear)
        invalidate()
        return true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics,
    )
}
