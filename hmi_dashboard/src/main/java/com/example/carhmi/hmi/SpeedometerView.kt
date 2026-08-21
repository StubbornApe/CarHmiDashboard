package com.example.carhmi.hmi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 圆形速度表（Day 2 版）：
 * 外环 + 270° 量程弧 + 主/副刻度 + 0~240 数字 + 轴心中心点。
 * 角度约定：startAngle=135°（左下），顺时针扫 270° 到右下（405°），与蓝弧一致。
 */
class SpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ---- 量程配置（Day 4 将改为自定义属性） ----
    private companion object {
        const val MAX_SPEED = 240            // 量程上限 km/h
        const val START_ANGLE = 135f         // 表盘起点角（左下）
        const val SWEEP_ANGLE = 270f         // 表盘扫掠角
        const val MAJOR_STEP = 20            // 主刻度步进 km/h
        const val MINOR_STEP = 10            // 副刻度步进 km/h
    }

    // ---- 新增：尺寸换算（依赖 Context 的 DisplayMetrics） ----
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * scaledDensity

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(HmiDimens.OUTER_RING_WIDTH_DP)
        color = 0xFF3A3A3A.toInt()          // 新增：显式外环颜色（深灰）
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(HmiDimens.ARC_WIDTH_DP)
        color = 0xFF3F9BFF.toInt()
    }

    // 主刻度线
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(HmiDimens.MAJOR_TICK_WIDTH_DP)
        color = 0xFFFFFFFF.toInt()          // 白
    }

    // 副刻度线
    private val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(HmiDimens.MINOR_TICK_WIDTH_DP)
        color = 0xFF9E9E9E.toInt()          // 灰，弱于主刻度
    }

    // 数字
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = sp(HmiDimens.NUMBER_TEXT_SIZE_SP)
        textAlign = Paint.Align.CENTER      // 数字水平居中于定位点
    }

    // 中心轴帽（外深灰 + 内蓝，FILL 实心）
    private val centerOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF3F3F3F.toInt()
    }
    private val centerInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF3F9BFF.toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        android.util.Log.d("Speedometer", "mode=$mode size=$size")
        val resolved = resolveSize(dp(HmiDimens.SPEEDOMETER_SIZE_DP).toInt(), widthMeasureSpec)
        setMeasuredDimension(resolved, resolved)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = cx - dp(HmiDimens.OUTER_RING_MARGIN_DP)

        // 1) 外环
        canvas.drawCircle(cx, cy, radius, ringPaint)

        // 2) 270° 量程弧（135° 起顺时针）
        canvas.drawArc(
            cx - radius, cy - radius, cx + radius, cy + radius,
            START_ANGLE, SWEEP_ANGLE, false, arcPaint
        )

        // 3) 刻度盘：用 rotate 在圆周均匀画主/副刻度
        drawTicks(canvas, cx, cy, radius)

        // 4) 数字：三角函数定位，文本保持垂直不旋转
        drawNumbers(canvas, cx, cy, radius)

        // 5) 中心轴帽
        canvas.drawCircle(cx, cy, dp(HmiDimens.CENTER_CIRCLE_RADIUS_DP), centerOuterPaint)
        canvas.drawCircle(cx, cy, dp(HmiDimens.CENTER_CIRCLE_INNER_DP), centerInnerPaint)
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // 主刻度：0~240 步进 20，共 13 根，间隔 22.5°
        canvas.save()
        canvas.rotate(START_ANGLE, cx, cy)
        val majorTickCount = MAX_SPEED / MAJOR_STEP               // 12 段
        val majorStepAngle = SWEEP_ANGLE / majorTickCount         // 22.5°
        for (i in 0..majorTickCount) {
            drawOneTick(canvas, cx, cy, radius, HmiDimens.MAJOR_TICK_LENGTH_DP, majorTickPaint)
            canvas.rotate(majorStepAngle, cx, cy)
        }
        canvas.restore()

        // 副刻度：0~240 步进 10，跳过与主刻度重叠的位置，间隔 11.25°
        canvas.save()
        canvas.rotate(START_ANGLE, cx, cy)
        val minorTickCount = MAX_SPEED / MINOR_STEP               // 24 段
        val minorStepAngle = SWEEP_ANGLE / minorTickCount         // 11.25°
        for (i in 0..minorTickCount) {
            if (i % (MAJOR_STEP / MINOR_STEP) != 0) {             // i 为偶数时是主刻度位置，跳过
                drawOneTick(canvas, cx, cy, radius, HmiDimens.MINOR_TICK_LENGTH_DP, minorTickPaint)
            }
            canvas.rotate(minorStepAngle, cx, cy)
        }
        canvas.restore()
    }

    private fun drawNumbers(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // 文本垂直居中修正：基线 y = 目标圆心方向点 - (ascent + descent) / 2
        val fm = numberPaint.fontMetrics
        val baselineOffset = (fm.ascent + fm.descent) / 2f      // 负数，用于向下减

        val labelRadius = radius - dp(HmiDimens.MAJOR_TICK_LENGTH_DP) - dp(HmiDimens.NUMBER_GAP_DP)
        val majorTickCount = MAX_SPEED / MAJOR_STEP
        val majorStepAngle = SWEEP_ANGLE / majorTickCount

        for (i in 0..majorTickCount) {
            val speed = i * MAJOR_STEP
            val angleRad = Math.toRadians(START_ANGLE + i * majorStepAngle.toDouble())
            val x = cx + (labelRadius * Math.cos(angleRad)).toFloat()
            val targetY = cy + (labelRadius * Math.sin(angleRad)).toFloat()
            canvas.drawText(speed.toString(), x, targetY - baselineOffset, numberPaint)
        }
    }

    private fun drawOneTick(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        lengthDp: Float,
        paint: Paint
    ) {
        // 沿局部 +x（正右方）从外圈向内画线段：旋转后该方向 = rotate 角度
        val inner = radius - dp(lengthDp)
        canvas.drawLine(cx + radius, cy, cx + inner, cy, paint)
    }
}