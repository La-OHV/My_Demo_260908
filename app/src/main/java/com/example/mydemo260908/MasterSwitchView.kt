package com.example.mydemo260908

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

class MasterSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onStateChanged: ((Boolean) -> Unit)? = null

    var isOn: Boolean = false
        private set

    private val diamondPath = Path()
    private val powerArc = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(3f)
    }

    init {
        isClickable = true
        isFocusable = true
        updateDescription()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val halfSize = min(width, height) * 0.43f
        diamondPath.reset()
        diamondPath.moveTo(centerX, centerY - halfSize)
        diamondPath.lineTo(centerX + halfSize, centerY)
        diamondPath.lineTo(centerX, centerY + halfSize)
        diamondPath.lineTo(centerX - halfSize, centerY)
        diamondPath.close()

        fillPaint.color = ContextCompat.getColor(
            context,
            if (isOn) R.color.master_on else R.color.master_off,
        )
        borderPaint.color = ContextCompat.getColor(
            context,
            if (isOn) R.color.master_on_border else R.color.master_off_border,
        )
        iconPaint.color = ContextCompat.getColor(
            context,
            if (isOn) R.color.white else R.color.remote_text_secondary,
        )
        canvas.drawPath(diamondPath, fillPaint)
        canvas.drawPath(diamondPath, borderPaint)

        val iconRadius = halfSize * 0.27f
        powerArc.set(
            centerX - iconRadius,
            centerY - iconRadius,
            centerX + iconRadius,
            centerY + iconRadius,
        )
        canvas.drawArc(powerArc, -55f, 290f, false, iconPaint)
        canvas.drawLine(
            centerX,
            centerY - iconRadius * 1.35f,
            centerX,
            centerY - iconRadius * 0.05f,
            iconPaint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                alpha = 0.78f
                true
            }

            MotionEvent.ACTION_UP -> {
                val shouldClick = isPressed
                isPressed = false
                alpha = 1f
                if (shouldClick) performClick()
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                alpha = 1f
                true
            }

            else -> true
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        isOn = !isOn
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        updateDescription()
        onStateChanged?.invoke(isOn)
        invalidate()
        return true
    }

    private fun updateDescription() {
        contentDescription = context.getString(
            if (isOn) R.string.master_switch_on else R.string.master_switch_off,
        )
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
