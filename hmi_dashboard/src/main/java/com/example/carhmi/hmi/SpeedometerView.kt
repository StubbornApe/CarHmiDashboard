package com.example.carhmi.hmi

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.animation.ValueAnimator
import android.animation.TimeInterpolator
import android.view.animation.AccelerateDecelerateInterpolator

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
    private var maxSpeed = 0         // 量程上限 km/h
    private var startAngle = 0f    // 表盘起点角（左下）
    private var sweepAngle = 0f    // 表盘扫掠角
    private var numberFontFamily = "sans-serif-condensed"   // 数字字体族（attrs 可覆盖，Day 5 新增）

    private companion object {
        const val MAJOR_STEP = 20            // 主刻度步进 km/h
        const val MINOR_STEP = 5            // 副刻度步进 km/h
        const val USE_ARC_NUMBERS = false    // true=沿弧排布, false=垂直正立（3.4 样式）
        const val DEMO_HALF_RANGE = false   // true=120 半量程(180°), false=240 全量程(270°)
    }

    // ---- 调试触摸开关（Day 4 新增）：true=可拖动定位（开发用），false=正式数据只读 ----
    private var touchEnabled = false

    fun setTouchEnabled(enabled: Boolean) {
        touchEnabled = enabled
    }

    init {
        // 从 XML 读取自定义属性；未写则回落默认值（旧布局因此保持兼容）
        // 注意：不用 .use{}（TypedArray 的 AutoCloseable 需要 API 31，minSdk 28 不兼容），
        // 改用 try/finally + recycle()（recycle 自 API 16 可用）
        val typedArray = context.obtainStyledAttributes(
            attrs, R.styleable.SpeedometerView, defStyleAttr, 0
        )
        try {
            maxSpeed = typedArray.getInt(R.styleable.SpeedometerView_maxSpeed, 240)
            startAngle = typedArray.getFloat(R.styleable.SpeedometerView_startAngle, 135f)
            sweepAngle = typedArray.getFloat(R.styleable.SpeedometerView_sweepAngle, 270f)
            touchEnabled = typedArray.getBoolean(R.styleable.SpeedometerView_touchEnabled, true)
            numberFontFamily = typedArray.getString(R.styleable.SpeedometerView_numberFontFamily)
                ?: "sans-serif-condensed"
        } finally {
            typedArray.recycle()
        }
        if (DEMO_HALF_RANGE) {
            setRange(maxSpeed = 120, startAngle = 180f, sweepAngle = 180f)
        }
    }

    fun setRange(maxSpeed: Int, startAngle: Float, sweepAngle: Float) {
        this.maxSpeed = maxSpeed
        this.startAngle = startAngle
        this.sweepAngle = sweepAngle
        invalidate()   // 配置变了必须请求重绘，否则画面不更新
        if (width > 0 && height > 0) {      // 已有尺寸才重建（首次 layout 前 onSizeChanged 会做）
            buildGradientShaders(center(width), center(height))
        }
    }
    // ---- 当前速度（Day 3 新增；Day 4 将改由 ViewModel 的 StateFlow 驱动） ----
    private var currentSpeed: Float = 0f

    // ---- 指针过渡动画（Day 6 新增）：默认平滑曲线，可切换 Interpolator ----
    private var animator: ValueAnimator? = null
    private var interpolator: TimeInterpolator = AccelerateDecelerateInterpolator()
    private var animDurationMs = 900L                 // 单次平滑过渡时长

    /** 设置指针过渡曲线（如 Accelerate/Overshoot/Anticipate/PathInterpolator），下次动画生效 */
    fun setInterpolator(curve: TimeInterpolator) {
        interpolator = curve
    }

    /**
     * 设置当前速度：越界自动夹取到 [0, maxSpeed]，值有变化才重绘。
     */
    fun setSpeed(speed: Float) {
        val target = speed.coerceIn(0f, maxSpeed.toFloat())
        if (target == currentSpeed) return        // 与当前一致：不启动动画（避免空转）
        // 动画中断：取消旧动画，从「当前实际动画帧的值」起一段新动画，保证指针连续不倒退
        animator?.cancel()
        val curve = interpolator                  // 提前取出，避免在 apply 块里语义混淆
        animator = ValueAnimator.ofFloat(currentSpeed, target).apply {
            duration = animDurationMs
            interpolator = curve                 // 用 View 配置好的过渡曲线
            addUpdateListener { va ->
                currentSpeed = va.animatedValue as Float   // 每帧的瞬时速度
                speedListener?.onSpeedChanged(angleMapper.displaySpeed(currentSpeed))
                invalidate()                             // 重画指针（与 onDraw 同步）
            }
            start()
        }
    }

    /** 瞬间设定当前速度（不带动画）：用于演示前复位到统一起点，避免"目标==当前"导致动画空转 */
    fun setSpeedImmediate(speed: Float) {
        animator?.cancel()                              // 先停掉可能存在的动画，避免残留
        currentSpeed = speed.coerceIn(0f, maxSpeed.toFloat())
        speedListener?.onSpeedChanged(angleMapper.displaySpeed(currentSpeed))   // 同步刷新外部数字
        invalidate()
    }

    fun getSpeed(): Float = currentSpeed



    /** 速度变化的监听回调（进阶挑战 1：让外部 TextView 跟着刷新；Day 4 会换成 StateFlow） */
    fun interface OnSpeedChangedListener {
        fun onSpeedChanged(displaySpeed: Int)
    }

    private var speedListener: OnSpeedChangedListener? = null

    fun setSpeedListener(listener: OnSpeedChangedListener?) {
        speedListener = listener
    }

    private fun center(of: Int): Float = of / 2f

    // ---- 新增：尺寸换算（依赖 Context 的 DisplayMetrics） ----
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * scaledDensity

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(HmiDimens.OUTER_RING_WIDTH_DP)
        color = 0xFF3A3A3A.toInt()          // 显式外环颜色（深灰）
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
        typeface = Typeface.create(numberFontFamily, Typeface.BOLD)   // Day 5：数字窄体加粗
    }

    // 当前车速大数字（Day 5 v3.1）：白色 condensed 加粗，显示在下方 45°~135° 缺口区
    private val speedValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF7F7F7.toInt()              // 近白，比刻度数字更亮、主次分明
        textSize = sp(HmiDimens.SPEED_VALUE_TEXT_SIZE_SP)
        textAlign = Paint.Align.CENTER          // 水平居中于表盘中轴
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)  // 仪表数字专用窄体
    }

    // 中心轴帽（外深灰 + 内蓝，FILL 实心）
    private val centerOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF3F3F3F.toInt()         // 内芯深灰
    }
    // 轴心金属环：STROKE 灰渐变由 buildGradientShaders 设置
    private val centerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(HmiDimens.CENTER_RING_WIDTH_DP)
    }
    private val centerHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x99FFFFFF.toInt()         // 60% 白，表冠反光
    }
    private val pointerBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFF2F2F2.toInt()         // 与指针主体一致的亮银白，根部底衬
    }

    // 表盘内单位 "km/h"（Day 3 新增）：半透明白，弱于指针/刻度
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()              // 60% 透明白
        textSize = sp(HmiDimens.UNIT_TEXT_SIZE_SP)
        textAlign = Paint.Align.CENTER          // 水平居中于中轴
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)   // Day 5：单位中等字重
    }

    // ---- 角度换算纯逻辑（改为 by lazy：等量程参数在 init 赋值后再创建） ----
    private val angleMapper by lazy { SpeedAngleMapper(maxSpeed, startAngle, sweepAngle) }

    // ---- 刻度布局纯逻辑（Day 5 新增，by lazy：量程参数在 init 赋值后再创建） ----
    private val tickMapper by lazy { TickMapper(maxSpeed, startAngle, sweepAngle) }

    // ---- 拖动状态（Day 3 新增） ----
    private var isDragging = false
    private var downTouchRadius = 0f       // DOWN 到手拉半径，供 3.5 环带判定

    // 指针（Day 5 v3）：亮银白主体 FILL + 浅灰细描边（深色表盘用白指针，与渐变弧零冲突）
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFF2F2F2.toInt()         // 亮银白，车载仪表标准指针色
    }
    private val pointerEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)               // 细描边压出轮廓
        color = 0xFFBDBDBD.toInt()         // 浅灰描边，柔和收边
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        android.util.Log.d("Speedometer", "mode=$mode size=$size")
        // Day 7 修复：宽/高双向取最小，横屏纵向空间不足时表盘自动收缩（demo 按钮常驻可见）
        val desired = dp(HmiDimens.SPEEDOMETER_SIZE_DP).toInt()
        val edge = minOf(resolveSize(desired, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec))
        setMeasuredDimension(edge, edge)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildGradientShaders(center(w), center(h))   // cx/cy 此时已确定，构建一次即可
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = center(width)
        val cy = center(height)
        val radius = cx - dp(HmiDimens.OUTER_RING_MARGIN_DP)

        // 1) 外环：开口弧，端口向死区方向延伸至 0/240 刻度线处齐平（封口）
        canvas.drawArc(
            cx - radius, cy - radius, cx + radius, cy + radius,
            startAngle, sweepAngle, false, ringPaint
        )

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

        // 5) 指针（Day 3 新增）：必须在中心轴帽之前画，让轴帽压住指针尾端
        drawPointer(canvas, cx, cy)

        // 6) 指针根部底衬（亮银白，尾端过渡进轴心）
        canvas.drawCircle(cx, cy, dp(HmiDimens.CENTER_BASE_RADIUS_DP), pointerBasePaint)
        // 7) 轴心金属外环（灰渐变机芯环）
        canvas.drawCircle(cx, cy, dp(HmiDimens.CENTER_CIRCLE_RADIUS_DP), centerRingPaint)
        // 8) 轴心内芯（深灰实心）
        canvas.drawCircle(cx, cy, dp(HmiDimens.CENTER_INNER_RADIUS_DP), centerOuterPaint)
        // 9) 内芯高光（左上一小点，模拟表冠反光）
        canvas.drawCircle(
            cx - dp(HmiDimens.CENTER_HIGHLIGHT_DX_DP),
            cy - dp(HmiDimens.CENTER_HIGHLIGHT_DY_DP),
            dp(HmiDimens.CENTER_HIGHLIGHT_RADIUS_DP),
            centerHighlightPaint
        )

        // 10) 当前车速大数字（量程弧中点 270° 正上方内侧，文字保持水平）
        drawCurrentSpeed(canvas, cx, cy, radius)

        // 11) 表盘内单位 "km/h"（轴心下方），0x99 = 半透明白
        canvas.drawText(
            "km/h",
            cx,
            cy + dp(HmiDimens.UNIT_CENTER_OFFSET_DP),
            unitPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!touchEnabled) return false                       // 调试开关关闭：不响应触摸
                if (onDownInterceptor(event.x, event.y)) return true  // 有效拖动：消费并继续收后续事件
                return false                                          // 环带外 / 死区内：不消费，放行
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) updateFromTouch(event.getX(0), event.getY(0))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false          // 必须复位，否则下一指被"鬼拖动"
            }
        }
        return isDragging || super.onTouchEvent(event)
    }
    /** 原 DOWN 分支体抽出的方法：环带判定 + 置拖动态 + 换算速度；true=本次 DOWN 被消费（进入拖动） */
    private fun onDownInterceptor(x: Float, y: Float): Boolean {
        val cx = center(width); val cy = center(height)
        downTouchRadius = kotlin.math.hypot((x - cx).toDouble(), (y - cy).toDouble()).toFloat()
        if (!isInTouchZone(x, y, cx, cy)) return false
        isDragging = true
        updateFromTouch(x, y)
        return true
    }

    /** 触摸点 → 换算速度 → 重绘 */
    private fun updateFromTouch(x: Float, y: Float) {
        val cx = center(width); val cy = center(height)
        val angle = angleFromTouch(x, y, cx, cy)
        val speed = angleMapper.angleToSpeed(angle)
        setSpeed(speed)                     // 内部做 clamp + 脏值检查 + invalidate
    }

    private fun shouldDrawMinor(i: Int): Boolean = i % (MAJOR_STEP / MINOR_STEP) != 0

    /**
     * 构建并设置两个 Shader（Day 5 新增）：
     * 1) 量程弧 SweepGradient：绿→黄→红，用 positions 压缩到量程覆盖比，setLocalMatrix 对齐弧起点；
     * 2) 单位文字 LinearGradient：上白→下半透明的纵向渐变。
     */
    private fun buildGradientShaders(cx: Float, cy: Float) {
        // —— 量程弧：SweepGradient（起点=绿、弧正中=黄、终点=红）——
        val coverage = sweepAngle / 360f                       // 0.75：弧占圆周比
        val sweep = SweepGradient(
            cx, cy,
            GradientColors.COLORS,                             // [绿, 黄, 红]
            floatArrayOf(0f, 0.5f * coverage, coverage)        // [0, 0.375, 0.75]
        )
        val matrix = Matrix().apply { postRotate(startAngle, cx, cy) }  // 0°→startAngle
        sweep.setLocalMatrix(matrix)
        arcPaint.shader = sweep

        // —— 单位文字 "km/h"：LinearGradient 纵向 白→半透明白 ——
        val top = cy - dp(HmiDimens.UNIT_CENTER_OFFSET_DP) - sp(HmiDimens.UNIT_TEXT_SIZE_SP)
        val bottom = cy + dp(HmiDimens.UNIT_CENTER_OFFSET_DP) + sp(HmiDimens.UNIT_TEXT_SIZE_SP)
        unitPaint.shader = LinearGradient(
            cx, top, cx, bottom,
            intArrayOf(0xFFFFFFFF.toInt(), 0x66FFFFFF.toInt()),  // 白 → 40% 透明白
            null,
            Shader.TileMode.CLAMP
        )

        // —— 轴心金属环：SweepGradient 灰渐变（机芯质感）——
        centerRingPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                0xFFE8E8E8.toInt(),   // 亮灰（0°）
                0xFF8E8E8E.toInt(),   // 中灰（90°）
                0xFF424242.toInt(),   // 深灰（180°）
                0xFFE8E8E8.toInt()    // 回亮（270° 起闭合）
            ),
            null
        )
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // 副刻度（底层）：每 5 km/h，灰色弱化，跳过主刻度位置
        canvas.save()
        canvas.rotate(startAngle, cx, cy)
        val minorStep = tickMapper.stepAngle(MINOR_STEP)
        for (i in 0..tickMapper.tickCount(MINOR_STEP)) {
            if (!tickMapper.isMajorIndex(i, MAJOR_STEP, MINOR_STEP)) {
                drawOneTick(canvas, cx, cy, radius, HmiDimens.MINOR_TICK_LENGTH_DP, minorTickPaint)
            }
            canvas.rotate(minorStep, cx, cy)
        }
        canvas.restore()

        // 主刻度（上层）：每 20 km/h，颜色按渐变分档（低速绿 → 中速黄 → 高速红）
        canvas.save()
        canvas.rotate(startAngle, cx, cy)
        val majorStep = tickMapper.stepAngle(MAJOR_STEP)
        val majorTickCount = tickMapper.tickCount(MAJOR_STEP)
        for (i in 0..majorTickCount) {
            val speed = tickMapper.speedAtTick(i, MAJOR_STEP).toFloat()
            majorTickPaint.color = GradientColors.interpolate(
                tickMapper.fractionForSpeed(speed)
            )
            // 0/240 端点刻度：外端向表盘外伸出至色环外径（radius+6），叠在灰环端口上形成封口
            val outerAdjustDp = if (i == 0 || i == majorTickCount) {
                -HmiDimens.ARC_WIDTH_DP / 2f
            } else 0f
            drawOneTick(
                canvas, cx, cy, radius,
                HmiDimens.MAJOR_TICK_LENGTH_DP, majorTickPaint, outerAdjustDp
            )
            canvas.rotate(majorStep, cx, cy)
        }
        canvas.restore()
    }
    private fun drawOneTick(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        lengthDp: Float,
        paint: Paint,
        outerAdjustDp: Float = 0f
    ) {
        // 沿局部 +x（正右方）从外圈向内画线段：旋转后该方向 = rotate 角度
        // outerAdjustDp 表示外端偏移：正数=向圆心内缩，负数=向表盘外伸出（用于 0/240 封住灰环端口）
        val outer = radius - dp(outerAdjustDp)
        val inner = outer - dp(lengthDp)
        canvas.drawLine(cx + outer, cy, cx + inner, cy, paint)
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

    /** 当前车速大数字（Day 5 v3.3）：显示在 90°（正下方）弧内侧偏上，文字保持水平 */
    private fun drawCurrentSpeed(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // 仅钳数字到 [0,maxSpeed]：避免 Overshoot/Anticipate 越界时显示负数/超240。
        // 指针仍按插值器原始值摆动，保留"冲出再回弹/先退后冲"的手感。
        val text = currentSpeed.coerceIn(0f, maxSpeed.toFloat()).toInt().toString()
        // 90° = 正下方（Android 角度：0° 三点、顺时针旋转，90° 六点即正下）
        val rad = Math.toRadians(90.0)
        // 半径：比刻度数字环内缩一格（36+16+28=80dp）再上提 16dp → 弧内侧偏上
        val r = radius - dp(
            HmiDimens.MAJOR_TICK_LENGTH_DP + HmiDimens.NUMBER_GAP_DP +
                HmiDimens.SPEED_VALUE_ARC_GAP_DP + HmiDimens.SPEED_VALUE_LIFT_DP
        )
        val x = cx + (r * Math.cos(rad)).toFloat()
        val y = cy + (r * Math.sin(rad)).toFloat()
        val fm = speedValuePaint.fontMetrics
        // baseline = 顶底中线修正，保证文字垂直居中于定位点
        val baseline = y - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, x, baseline, speedValuePaint)
    }

    private fun drawPointer(canvas: Canvas, cx: Float, cy: Float) {
        val angle = speedToAngle(currentSpeed)
        val head = dp(HmiDimens.POINTER_LENGTH_DP)        // 尖端径向距离
        val tail = dp(HmiDimens.POINTER_TAIL_LENGTH_DP)   // 尾部径向距离
        val halfW = dp(HmiDimens.POINTER_WIDTH_DP)        // 最宽处半宽（总宽 = 2×halfW）
        val sideX = head * 0.55f                          // 最宽处 x：轴心前中段，前后都收尖

        // 梭形指针：尖端(head,0) → 最宽处(sideX,±halfW) → 尾点(-tail,0)
        val path = Path()
        path.moveTo(head, 0f)
        path.lineTo(sideX, halfW)
        path.lineTo(-tail, 0f)
        path.lineTo(sideX, -halfW)
        path.close()

        canvas.save()
        canvas.translate(cx, cy)            // 1) 原点移到轴心（Path 用局部坐标，必须先平移！）
        canvas.rotate(angle)                // 2) 旋转：局部 +x 指向目标角度
        canvas.drawPath(path, pointerPaint)      // 头尖指向目标刻度
        canvas.drawPath(path, pointerEdgePaint)  // 细描边压轮廓，避免纯色平涂发闷
        canvas.restore()
    }

    /** 速度 → 表盘角度（委托 Mapper） */
    private fun speedToAngle(speed: Float): Float = angleMapper.speedToAngle(speed)

    /** 这一步临时保留接口名，为 3.5 把触摸换算统一走 Mapper 做铺垫 */
    private fun angleFromTouch(x: Float, y: Float, cx: Float, cy: Float): Float =
        angleMapper.angleFromTouch(x, y, cx, cy)

    /** 触摸是否落在可拖动的有效区：半径在环带内 且 角度在量程内（跨0°拆分判定） */
    private fun isInTouchZone(x: Float, y: Float, cx: Float, cy: Float): Boolean {
        val inner = dp(HmiDimens.TOUCH_RING_INNER_DP)
        val outer = center(width) - dp(HmiDimens.OUTER_RING_MARGIN_DP) + dp(HmiDimens.TOUCH_RING_OUTER_SLOP_DP)
        if (!angleMapper.isInTouchRing(x, y, cx, cy, inner, outer)) return false

        val deg = angleMapper.angleFromTouch(x, y, cx, cy)
        return isInSweepRange(deg)
    }

    /** 量程是否为跨 0°（如 135→405）；是则两段式判定，否则直接区间比较 */
    private fun isInSweepRange(deg: Float): Boolean {
        val from = startAngle
        val to = (startAngle + sweepAngle) % 360f
        return if (from <= to) deg in from..to else (deg >= from || deg <= to)
    }
}