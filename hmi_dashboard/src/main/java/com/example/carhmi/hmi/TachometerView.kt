package com.example.carhmi.hmi

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.roundToInt

/**
 * 转速表（Day 7 新增）：与 SpeedometerView 风格统一的第二块表盘。
 * 0~8000 RPM、270° 量程（135° 起）、红区 7000 起、×1000 数字、
 * drawArc 环形进度环 + 红色甩动指针 + 档位标签。
 * 数据由 ViewModel 单向注入：setRpm() / setGear()。
 */
class TachometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var maxRpm = 8000        // 量程上限 RPM
    private var startAngle = 135f    // 表盘起点角（与速度表一致）
    private var sweepAngle = 270f    // 表盘扫掠角

    private companion object {
        const val MAJOR_STEP = 1000      // 主刻度步进（RPM）
        const val MINOR_STEP = 500       // 副刻度步进（RPM）
        const val REDLINE = 7000         // 红区起点
        const val SIZE_DP = 600f         // 组件测量尺寸（比速度表略小，双表同屏用）
        const val ANIM_DURATION_MS = 380L
        const val RED = 0xFFFF3B30.toInt()
        const val OUTER_MARGIN_DP = 60f;   const val RING_WIDTH_DP = 40f
        const val ARC_WIDTH_DP = 12f;      const val PROGRESS_WIDTH_DP = 7f
        const val MAJOR_TICK_LEN_DP = 30f; const val MAJOR_TICK_W_DP = 5f
        const val MINOR_TICK_LEN_DP = 14f; const val MINOR_TICK_W_DP = 2f
        const val NUMBER_SP = 24f;         const val NUMBER_GAP_DP = 14f
        const val VALUE_SP = 52f;          const val UNIT_SP = 18f
        const val GEAR_SP = 26f;           const val POINTER_LEN_DP = 190f
        const val POINTER_TAIL_DP = 38f;   const val POINTER_W_DP = 5f
    }

    // ---- 数据（ViewModel 单向注入） ----
    private var currentRpm = 0f
    private var gearText = "D1"
    private var animator: ValueAnimator? = null
    private var pointerInterpolator: TimeInterpolator = OvershootInterpolator(2f)

    private val angleMapper by lazy { RpmAngleMapper(maxRpm, startAngle, sweepAngle) }
    private val tickMapper by lazy { TickMapper(maxRpm, startAngle, sweepAngle) }

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.TachometerView, defStyleAttr, 0)
        try {
            maxRpm = ta.getInt(R.styleable.TachometerView_maxRpm, 8000)
            startAngle = ta.getFloat(R.styleable.TachometerView_startAngle, 135f)
            sweepAngle = ta.getFloat(R.styleable.TachometerView_sweepAngle, 270f)
        } finally {
            ta.recycle()
        }
    }

    fun setRpm(rpm: Float) {
        val target = rpm.coerceIn(0f, maxRpm.toFloat())
        if (target == currentRpm) return                  // 与当前一致：不启动动画
        animator?.cancel()                                // 中断旧动画，从当前值续接
        val curve = pointerInterpolator
        animator = ValueAnimator.ofFloat(currentRpm, target).apply {
            duration = ANIM_DURATION_MS
            interpolator = curve                          // Overshoot：换挡甩动手感
            addUpdateListener { va ->
                currentRpm = va.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setGear(text: String) {
        if (text != gearText) { gearText = text; invalidate() }
    }

    fun setInterpolator(curve: TimeInterpolator) { pointerInterpolator = curve }

    // ---- 尺寸换算 / 画笔 ----
    private fun center(of: Int): Float = of / 2f
    private val density get() = resources.displayMetrics.density
    private val scaledDensity get() = resources.displayMetrics.scaledDensity
    private fun dp(v: Float): Float = v * density
    private fun sp(v: Float): Float = v * scaledDensity
    private val fontScale = SIZE_DP / HmiDimens.SPEEDOMETER_SIZE_DP  // 0.75：字号随表盘等比例缩小（对齐速度表 800dp 基准）

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(RING_WIDTH_DP); color = 0xFF3A3A3A.toInt()
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(ARC_WIDTH_DP)
    }
    private val redlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(ARC_WIDTH_DP); color = RED
    }
    private val progressRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(PROGRESS_WIDTH_DP)
        strokeCap = Paint.Cap.ROUND
    }
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(MAJOR_TICK_W_DP); color = 0xFFFFFFFF.toInt()
    }
    private val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(MINOR_TICK_W_DP); color = 0xFF9E9E9E.toInt()
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textSize = sp(NUMBER_SP); textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // 转速大数字字号保持随表盘等比例缩小（×0.75），避免在小表盘里拥挤
        color = 0xFFF7F7F7.toInt(); textSize = sp(VALUE_SP) * fontScale; textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt(); textSize = sp(UNIT_SP); textAlign = Paint.Align.CENTER
    }
    private val gearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD54F.toInt(); textSize = sp(GEAR_SP); textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = RED
    }
    private val pointerEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2f); color = 0xFFB71C1C.toInt()
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = 0xFF3F3F3F.toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Day 7 修复：宽/高双向取最小，横屏纵向空间不足时表盘自动收缩（demo 按钮常驻可见）
        val desired = dp(SIZE_DP).toInt()
        val edge = minOf(resolveSize(desired, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec))
        setMeasuredDimension(edge, edge)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildArcShader(center(w), center(h))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = center(width); val cy = center(height)
        val radius = cx - dp(OUTER_MARGIN_DP)
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // 1) 外环
        canvas.drawArc(oval, startAngle, sweepAngle, false, outerRingPaint)

        // 2) 量程弧（渐变）+ 红区固定段加深（drawArc 直接给角度，不进状态栈）
        canvas.drawArc(oval, startAngle, sweepAngle, false, arcPaint)
        val redFrom = startAngle + (REDLINE.toFloat() / maxRpm) * sweepAngle
        canvas.drawArc(oval, redFrom, startAngle + sweepAngle - redFrom, false, redlinePaint)

        // 3) 刻度（状态栈：save -> rotate 逐根画 -> restore）
        drawTicks(canvas, cx, cy, radius)

        // 4) 数字 0/2/4/6/8（×1000 简读）
        drawNumbers(canvas, cx, cy, radius)

        // 5) 环形进度环（drawArc 环形进度：sweep = fraction × sweepAngle）
        val fraction = (currentRpm / maxRpm).coerceIn(0f, 1f)
        val ringR = radius - dp(ARC_WIDTH_DP) - dp(MAJOR_TICK_LEN_DP) - dp(16f)
        val ringOval = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        progressRingPaint.color = GradientColors.interpolate(fraction)
        canvas.drawArc(ringOval, startAngle, sweepAngle * fraction, false, progressRingPaint)

        // 6) 指针（状态栈：translate 到轴心 + rotate 到 rpm 角 + 局部坐标画梭形）
        drawPointer(canvas, cx, cy)

        // 7) 单位 + 转速大数字（参考速度表：单位在轴心下方、大数字在 90° 弧内侧）+ 档位 + 轴心帽
        drawValues(canvas, cx, cy, radius)
    }

    /** 刻度：副刻度每 500（跳过主刻度位），主刻度每 1000（进入红区的标红） */
    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        canvas.save()
        canvas.rotate(startAngle, cx, cy)
        val minorStep = tickMapper.stepAngle(MINOR_STEP)
        for (i in 0..tickMapper.tickCount(MINOR_STEP)) {
            if (!tickMapper.isMajorIndex(i, MAJOR_STEP, MINOR_STEP)) {
                drawOneTick(canvas, cx, cy, radius, MINOR_TICK_LEN_DP, minorTickPaint)
            }
            canvas.rotate(minorStep, cx, cy)
        }
        canvas.restore()

        canvas.save()
        canvas.rotate(startAngle, cx, cy)
        val majorStep = tickMapper.stepAngle(MAJOR_STEP)
        for (i in 0..tickMapper.tickCount(MAJOR_STEP)) {
            val rpm = tickMapper.speedAtTick(i, MAJOR_STEP)
            majorTickPaint.color = if (rpm >= REDLINE) RED else 0xFFFFFFFF.toInt()
            drawOneTick(canvas, cx, cy, radius, MAJOR_TICK_LEN_DP, majorTickPaint)
            canvas.rotate(majorStep, cx, cy)
        }
        canvas.restore()
    }

    /** 沿当前局部 +x（= rotate 后的角度方向）从外向内画一根刻度线 */
    private fun drawOneTick(
        canvas: Canvas, cx: Float, cy: Float, radius: Float,
        lengthDp: Float, paint: Paint
    ) {
        val outer = radius
        val inner = outer - dp(lengthDp)
        canvas.drawLine(cx + outer, cy, cx + inner, cy, paint)
    }

    /** 数字 0/2/4/6/8：×1000 简读，沿弧垂直正立（不旋转文字） */
    private fun drawNumbers(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val fm = numberPaint.fontMetrics
        val baselineOffset = (fm.ascent + fm.descent) / 2f
        val labelRadius = radius - dp(30f) - dp(NUMBER_GAP_DP)
        val majorTickCount = maxRpm / MAJOR_STEP
        val majorStepAngle = sweepAngle / majorTickCount
        for (i in 0..majorTickCount step 2) {
            val angleRad = Math.toRadians((startAngle + i * majorStepAngle).toDouble())
            val x = cx + (labelRadius * Math.cos(angleRad)).toFloat()
            val targetY = cy + (labelRadius * Math.sin(angleRad)).toFloat()
            canvas.drawText((i * MAJOR_STEP / 1000).toString(), x, targetY - baselineOffset, numberPaint)
        }
    }

    /** 红色梭形指针：translate 到轴心 + rotate 到 rpm 角 + 局部坐标画梭形（状态栈三段式） */
    private fun drawPointer(canvas: Canvas, cx: Float, cy: Float) {
        val angle = angleMapper.rpmToAngle(currentRpm)
        val head = dp(POINTER_LEN_DP)
        val tail = dp(POINTER_TAIL_DP)
        val halfW = dp(POINTER_W_DP)
        val sideX = head * 0.55f

        val path = Path().apply {
            moveTo(head, 0f)
            lineTo(sideX, halfW)
            lineTo(-tail, 0f)
            lineTo(sideX, -halfW)
            close()
        }

        canvas.save()
        canvas.translate(cx, cy)          // 1) 原点移到轴心
        canvas.rotate(angle)              // 2) 局部 +x 指向 rpm 角
        canvas.drawPath(path, pointerPaint)
        canvas.drawPath(path, pointerEdgePaint)
        canvas.restore()                  // 3) 归还画布
    }

    /**
     * 单位 + 转速大数字 + 档位 + 轴心帽（Day 7 优化）
     * 布局完全对照速度表（SpeedometerView）：
     * - 单位 ×1000RPM：画在轴心正下方（cy + dp(UNIT_CENTER_OFFSET_DP)），水平居中；
     * - 转速大数字：挂在 90°（正下方缺口中央）弧内侧偏上，
     *   r = radius - dp(MAJOR_TICK_LEN_DP + NUMBER_GAP_DP + SPEED_VALUE_ARC_GAP_DP + SPEED_VALUE_LIFT_DP)，
     *   再用 fontMetrics 垂直居中（与速度表 drawCurrentSpeed 同一算法）。
     * 档位保留表盘上部。仅转速大数字字号随表盘等比例缩小（×0.75），其余字体原大小。
     */
    private fun drawValues(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // 轴心帽
        canvas.drawCircle(cx, cy, dp(10f), centerPaint)

        // 档位（表盘上部，琥珀色高亮）
        canvas.drawText(gearText, cx, cy - dp(88f), gearPaint)

        // 转速大数字：90°（正下方）弧内侧偏上，与速度表 drawCurrentSpeed 同源
        // 钳到 [0,maxRpm]，避免 Overshoot 越界显示负数/超 8000
        val text = currentRpm.roundToInt().coerceIn(0, maxRpm).toString()
        val rad = Math.toRadians(90.0)
        val r = radius - dp(
            MAJOR_TICK_LEN_DP + NUMBER_GAP_DP +
                HmiDimens.SPEED_VALUE_ARC_GAP_DP + HmiDimens.SPEED_VALUE_LIFT_DP
        )
        val x = cx + (r * Math.cos(rad)).toFloat()
        val y = cy + (r * Math.sin(rad)).toFloat()
        val fm = valuePaint.fontMetrics
        val baseline = y - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, x, baseline, valuePaint)

        // 单位 ×1000RPM：轴心正下方（与速度表 km/h 同位置），水平居中
        canvas.drawText("×1000RPM", cx, cy + dp(HmiDimens.UNIT_CENTER_OFFSET_DP), unitPaint)
    }

    /** 量程弧渐变（绿→黄→红，对齐弧起点）：与 SpeedometerView 同样的 SweepGradient 方案 */
    private fun buildArcShader(cx: Float, cy: Float) {
        val coverage = sweepAngle / 360f
        val sweep = SweepGradient(
            cx, cy,
            GradientColors.COLORS,
            floatArrayOf(0f, 0.5f * coverage, coverage)
        )
        val matrix = Matrix().apply { postRotate(startAngle, cx, cy) }
        sweep.setLocalMatrix(matrix)
        arcPaint.shader = sweep
    }
}