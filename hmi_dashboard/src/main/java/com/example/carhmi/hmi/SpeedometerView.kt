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
    private var maxSpeed: Int = 240         // 量程上限 km/h
    private var startAngle: Float = 135f    // 表盘起点角（左下）
    private var sweepAngle: Float = 270f    // 表盘扫掠角

    fun setRange(maxSpeed: Int, startAngle: Float, sweepAngle: Float) {
        this.maxSpeed = maxSpeed
        this.startAngle = startAngle
        this.sweepAngle = sweepAngle
        invalidate()   // 配置变了必须请求重绘，否则画面不更新
    }
    private companion object {
        const val MAJOR_STEP = 20            // 主刻度步进 km/h
        const val MINOR_STEP = 5            // 副刻度步进 km/h
        const val USE_ARC_NUMBERS = true    // true=沿弧排布, false=垂直正立（3.4 样式）
        const val DEMO_HALF_RANGE = false   // true=120 半量程(180°), false=240 全量程(270°)
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

    init {
        if (DEMO_HALF_RANGE) {
            setRange(maxSpeed = 120, startAngle = 180f, sweepAngle = 180f)
        }
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
            startAngle, sweepAngle, false, arcPaint
        )

        // 3) 刻度盘：用 rotate 在圆周均匀画主/副刻度
        drawTicks(canvas, cx, cy, radius)

        // 4) 数字：根据开关选择排布方式
        if (USE_ARC_NUMBERS) {
            drawNumbersOnArc(canvas, cx, cy, radius)
        } else {
            drawNumbers(canvas, cx, cy, radius) // 三角函数定位，文本保持垂直不旋转
        }

        // 5) 中心轴帽
        canvas.drawCircle(cx, cy, dp(HmiDimens.CENTER_CIRCLE_RADIUS_DP), centerOuterPaint)
        canvas.drawCircle(cx, cy, dp(HmiDimens.CENTER_CIRCLE_INNER_DP), centerInnerPaint)
    }

    private fun shouldDrawMinor(i: Int): Boolean = i % (MAJOR_STEP / MINOR_STEP) != 0
    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // 主刻度：0~240 步进 20，共 13 根，间隔 22.5°
        canvas.save()
        canvas.rotate(startAngle, cx, cy)
        val majorTickCount = maxSpeed / MAJOR_STEP               // 12 段
        val majorStepAngle = sweepAngle / majorTickCount         // 22.5°
        for (i in 0..majorTickCount) {
            drawOneTick(canvas, cx, cy, radius, HmiDimens.MAJOR_TICK_LENGTH_DP, majorTickPaint)
            canvas.rotate(majorStepAngle, cx, cy)
        }
        canvas.restore()

        // 副刻度：0~240 步进 10，跳过与主刻度重叠的位置，间隔 11.25°
        canvas.save()
        canvas.rotate(startAngle, cx, cy)
        val minorTickCount = maxSpeed / MINOR_STEP               // 24 段
        val minorStepAngle = sweepAngle / minorTickCount         // 11.25°
        for (i in 0..minorTickCount) {
            if (shouldDrawMinor(i)) {             // 跳过主刻度位置
                drawOneTick(canvas, cx, cy, radius, HmiDimens.MINOR_TICK_LENGTH_DP, minorTickPaint)
            }
            canvas.rotate(minorStepAngle, cx, cy)
        }
        canvas.restore()
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
    private fun drawNumbers(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // 文本垂直居中修正：基线 y = 目标圆心方向点 - (ascent + descent) / 2
        val fm = numberPaint.fontMetrics
        val baselineOffset = (fm.ascent + fm.descent) / 2f      // 负数，用于向下减

        val labelRadius = radius - dp(HmiDimens.MAJOR_TICK_LENGTH_DP) - dp(HmiDimens.NUMBER_GAP_DP)
        val majorTickCount = maxSpeed / MAJOR_STEP
        val majorStepAngle = sweepAngle / majorTickCount

        for (i in 0..majorTickCount) {
            val speed = i * MAJOR_STEP
            val angleRad = Math.toRadians(startAngle + i * majorStepAngle.toDouble())
            val x = cx + (labelRadius * Math.cos(angleRad)).toFloat()
            val targetY = cy + (labelRadius * Math.sin(angleRad)).toFloat()
            canvas.drawText(speed.toString(), x, targetY - baselineOffset, numberPaint)
        }
    }

    private fun drawNumbersOnArc(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val labelRadius = radius - dp(HmiDimens.MAJOR_TICK_LENGTH_DP) - dp(HmiDimens.NUMBER_GAP_DP)
        val fm = numberPaint.fontMetrics
        val baselineOffset = (fm.ascent + fm.descent) / 2f
        val majorTickCount = maxSpeed / MAJOR_STEP
        val majorStepAngle = sweepAngle / majorTickCount

        for (i in 0..majorTickCount) {
            val angle = startAngle + i * majorStepAngle
            canvas.save()
            canvas.rotate(angle, cx, cy)                 // 1) 转到该刻度方向：局部 +x 即该角度
            canvas.rotate(90f, cx + labelRadius, cy)     // 2) 绕文字锚点再转 90°：基线转到切线方向
            canvas.drawText(
                (i * MAJOR_STEP).toString(),
                cx + labelRadius,
                cy - baselineOffset,
                numberPaint
            )
            canvas.restore()
        }
    }
}