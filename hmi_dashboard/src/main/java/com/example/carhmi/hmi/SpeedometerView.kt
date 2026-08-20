package com.example.carhmi.hmi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class SpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 40f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        android.util.Log.d("Speedometer", "mode=$mode size=$size")
        val resolved = resolveSize(800, widthMeasureSpec)
        setMeasuredDimension(resolved, resolved)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = cx - 60f

        canvas.drawCircle(cx, cy, radius, ringPaint)

        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 12f
            color = 0xFF3F9BFF.toInt()
        }
        canvas.drawArc(
            cx - radius, cy - radius, cx + radius, cy + radius,
            135f, 270f, false, arcPaint
        )
    }
}