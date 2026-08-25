package com.example.carhmi.hmi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
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
    private var maxSpeed = 0         // 量程上限 km/h
    private var startAngle = 0f    // 表盘起点角（左下）
    private var sweepAngle = 0f    // 表盘扫掠角

    private companion object {
        const val MAJOR_STEP = 20            // 主刻度步进 km/h
        const val MINOR_STEP = 5            // 副刻度步进 km/h
        const val USE_ARC_NUMBERS = true    // true=沿弧排布, false=垂直正立（3.4 样式）
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
    }
    // ---- 当前速度（Day 3 新增；Day 4 将改由 ViewModel 的 StateFlow 驱动） ----
    private var currentSpeed: Float = 60f

    /**
     * 设置当前速度：越界自动夹取到 [0, maxSpeed]，值有变化才重绘。
     */
    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0f, maxSpeed.toFloat())
        if (clamped == currentSpeed) return
        currentSpeed = clamped
        invalidate()
        speedListener?.onSpeedChanged(angleMapper.displaySpeed(currentSpeed))
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

    // 表盘内单位 "km/h"（Day 3 新增）：半透明白，弱于指针/刻度
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()              // 60% 透明白
        textSize = sp(HmiDimens.UNIT_TEXT_SIZE_SP)
        textAlign = Paint.Align.CENTER          // 水平居中于中轴
    }

    // ---- 角度换算纯逻辑（改为 by lazy：等量程参数在 init 赋值后再创建） ----
    private val angleMapper by lazy { SpeedAngleMapper(maxSpeed, startAngle, sweepAngle) }

    // ---- 拖动状态（Day 3 新增） ----
    private var isDragging = false
    private var downTouchRadius = 0f       // DOWN 到手拉半径，供 3.5 环带判定

    // 指针（Day 3 新增）：醒目橙红 + 圆端帽
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(HmiDimens.POINTER_WIDTH_DP)
        strokeCap = Paint.Cap.ROUND        // 线端收圆，避免尖锐锯齿
        color = 0xFFFF5722.toInt()         // 橙红，与蓝弧/白刻度形成对比
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
        val cx = center(width)
        val cy = center(height)
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

        // 5) 指针（Day 3 新增）：必须在中心轴帽之前画，让轴帽压住指针尾端
        drawPointer(canvas, cx, cy)

        // 6) 中心轴帽
        canvas.drawCircle(cx, cy, dp(HmiDimens.CENTER_CIRCLE_RADIUS_DP), centerOuterPaint)
        canvas.drawCircle(cx, cy, dp(HmiDimens.CENTER_CIRCLE_INNER_DP), centerInnerPaint)

        // 7) 表盘内单位 "km/h"（轴心下方），0x99 = 半透明白
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

    private fun drawPointer(canvas: Canvas, cx: Float, cy: Float) {
        val angle = speedToAngle(currentSpeed)
        val head = dp(HmiDimens.POINTER_LENGTH_DP)
        val tail = dp(HmiDimens.POINTER_TAIL_LENGTH_DP)

        canvas.save()
        canvas.rotate(angle, cx, cy)       // 转到目标角度：局部 +x 即该角度方向
        canvas.drawLine(cx - tail, cy, cx + head, cy, pointerPaint)
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