package com.example.carhmi.hmi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 横向温度条（Day 9 新增）：
 * 上部 = 环形进度（drawArc，135° 起、270° 扫掠）+ 环内温度大数字 + °C 单位；
 * 下部 = 横向圆角轨道 + 拖动手柄。
 * 16~32℃、0.5℃ 步进（quantize 量化）、颜色随温 蓝→白→红（TempColorMapper）、
 * 每跨一个步进触发一次触觉反馈（performHapticFeedback）。
 */
class TemperatureStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private companion object {
        // ---- 尺寸常量（dp/sp，进 onDraw 前换算） ----
        const val VIEW_HEIGHT_DP = 300f       // 组件测量高度
        const val RING_DIAMETER_DP = 150f     // 环形进度直径
        const val RING_WIDTH_DP = 10f         // 环粗细
        const val RING_TOP_DP = 20f           // 环顶部边距
        const val STRIP_GAP_DP = 36f          // 环底到轨道中心线的距离
        const val STRIP_W_DP = 8f             // 轨道粗
        const val THUMB_R_DP = 17f            // 手柄半径
        const val TOUCH_HALF_H_DP = 36f       // 轨道触摸带半高（命中判定容差）
        const val VALUE_SP = 42f              // 环内温度大数字字号
        const val UNIT_SP = 14f               // °C 单位字号
        const val ARC_START = 135f            // 环形进度起始角（与仪表盘一致）
        const val ARC_SWEEP = 270f            // 环形进度扫掠角（留 90° 缺口）
        const val DEFAULT_TEMP = 24f          // 初始温度 24℃（与 Day 8 空调骨架一致）
    }

    // ---- 量程配置（attrs 可覆盖，默认 16~32℃ / 0.5℃ / 可触摸） ----
    private var minTemp = 16f
    private var maxTemp = 32f
    private var tempStep = 0.5f
    private var touchEnabled = true

    // ---- 数据（单一数据源 currentTemp，其它一切显示都由它派生） ----
    private var currentTemp = DEFAULT_TEMP
    private var lastHapticStep = -1          // 上次已反馈的步进索引（防重复震动）
    private var isDragging = false

    init {
        val ta = context.obtainStyledAttributes(
            attrs, R.styleable.TemperatureStripView, defStyleAttr, 0
        )
        try {
            minTemp = ta.getFloat(R.styleable.TemperatureStripView_minTemp, 16f)
            maxTemp = ta.getFloat(R.styleable.TemperatureStripView_maxTemp, 32f)
            tempStep = ta.getFloat(R.styleable.TemperatureStripView_stepTemp, 0.5f)
            touchEnabled = ta.getBoolean(R.styleable.TemperatureStripView_touchEnabled, true)
        } finally {
            ta.recycle()
        }
    }

    // ---- 对外 API：set 夹取 + 量化，get 取当前值（与 SpeedometerView.setSpeed 同一风格） ----
    fun getTemp(): Float = currentTemp

    /** 温度变化回调（Day 9 进阶挑战 3：空调面板复用 - 拖动同步 tvTemp）；触摸拖动 / 程序化赋值都会触发 */
    var onTempChangedListener: ((Float) -> Unit)? = null

    fun setTemp(value: Float) {
        currentTemp = TempColorMapper.quantize(value.coerceIn(minTemp, maxTemp), tempStep)
        lastHapticStep = stepIndexOf(currentTemp)      // 程序化赋值：直接以新档位为基准
        invalidate()
        onTempChangedListener?.invoke(currentTemp)      // 程序化赋值也广播（单一数据出口）
    }

    // ---- 派生量：进度环 / 轨道填充 / 手柄三处共用 ----
    private val fraction: Float
        get() = (currentTemp - minTemp) / (maxTemp - minTemp)
    private val currentColor: Int
        get() = TempColorMapper.colorFor(fraction)

    // ---- 尺寸换算（依赖 Context DisplayMetrics，同 SpeedometerView） ----
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaledDensity

// ---- Paint（一次建好，onDraw 复用，避免每帧 new） ----
    private val ringBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(RING_WIDTH_DP)
        color = 0xFF2E2E2E.toInt()           // 环底：深灰
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(RING_WIDTH_DP)
        strokeCap = Paint.Cap.ROUND           // 圆头，进度环端点更柔和
    }
    private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(STRIP_W_DP)
        strokeCap = Paint.Cap.ROUND
        color = 0xFF2E2E2E.toInt()           // 轨道底：深灰
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(STRIP_W_DP)
        strokeCap = Paint.Cap.ROUND           // 已选段：圆头
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()           // 手柄：白
    }
    private val thumbEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = 0xFF1A1A1A.toInt()           // 手柄描边：深色压边
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = sp(VALUE_SP)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80FFFFFF.toInt()           // 半透明白
        textSize = sp(UNIT_SP)
        textAlign = Paint.Align.CENTER
    }

    // ---- 测量：固定高度，宽度吃父容器（横屏车机拉满宽度，档位间距大、好拖） ----
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, dp(VIEW_HEIGHT_DP).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val ringR = dp(RING_DIAMETER_DP) / 2f
        val ringCy = dp(RING_TOP_DP) + ringR

        // 1) 环形底：整段 270° 灰弧（先画底，进度弧叠在上面）
        val ringRect = RectF(cx - ringR, ringCy - ringR, cx + ringR, ringCy + ringR)
        canvas.drawArc(ringRect, ARC_START, ARC_SWEEP, false, ringBgPaint)
        // 2) 环形进度：扫掠角 = fraction × 270°，颜色 = 当前温变色
        progressPaint.color = currentColor
        canvas.drawArc(ringRect, ARC_START, fraction * ARC_SWEEP, false, progressPaint)
        // 3) 环内温度大数字：用 fontMetrics 做垂直居中修正（ascent+descent 是负值）
        val valueText = String.format("%.1f", currentTemp)
        val fm = valuePaint.fontMetrics
        val baseline = ringCy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(valueText, cx, baseline, valuePaint)
        // 4) 单位 °C：大数字下方小一号
        canvas.drawText("°C", cx, baseline + dp(VALUE_SP) * 0.55f, unitPaint)

        // 5) 轨道：中心线 y = 环底 + STRIP_GAP，左右留出手柄半径 + 边距
        val stripY = ringCy + ringR + dp(STRIP_GAP_DP)
        val left = trackLeft()
        val right = trackRight()
        // 轨道底（整段灰）
        canvas.drawLine(left, stripY, right, stripY, trackBgPaint)
        // 已选段（手柄左侧，当前温变色）
        val thumbX = left + fraction * (right - left)
        fillPaint.color = currentColor
        canvas.drawLine(left, stripY, thumbX, stripY, fillPaint)
        // 拖动手柄：白色实心圆 + 深色描边
        canvas.drawCircle(thumbX, stripY, dp(THUMB_R_DP), thumbPaint)
        canvas.drawCircle(thumbX, stripY, dp(THUMB_R_DP), thumbEdgePaint)
    }

    // ---- 轨道几何（供触摸换算与 onDraw 共用，保证"画在哪 == 摸到哪"） ----
    private fun stripY(): Float {
        val ringCy = dp(RING_TOP_DP) + dp(RING_DIAMETER_DP) / 2f
        return ringCy + dp(RING_DIAMETER_DP) / 2f + dp(STRIP_GAP_DP)
    }

    private fun trackLeft(): Float = dp(THUMB_R_DP + 12f)
    private fun trackRight(): Float = width - dp(THUMB_R_DP + 12f)

    // ---- 触摸交互（Day 3 触摸三元组：DOWN 命中捡起 / MOVE 跟手 / UP·CANCEL 复位） ----
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!touchEnabled) return false                     // 配置关触摸：放行
                if (!isInTrack(event.y)) return false               // 不在轨道触摸带内：放行父容器
                isDragging = true
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)  // 按下确认轻震
                updateFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) updateFromTouch(event.x)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false                                  // 必须复位，否则下一指鬼拖动
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return isDragging || super.onTouchEvent(event)
    }

    /** 手指是否落在轨道触摸带内（只判 y，横向任意位置都可起拖） */
    private fun isInTrack(y: Float): Boolean {
        return abs(y - stripY()) <= dp(TOUCH_HALF_H_DP)
    }

    /** 温度对应的档位索引（min 为第 0 档），用于"跨档才震"判定 */
    private fun stepIndexOf(temp: Float): Int {
        return ((temp - minTemp) / tempStep).roundToInt()
    }

    /** 触摸 x → 轨道占比 → 温度 → 0.5℃ 量化 → 更新显示 */
    private fun updateFromTouch(x: Float) {
        val f = ((x - trackLeft()) / (trackRight() - trackLeft())).coerceIn(0f, 1f)
        val target = minTemp + f * (maxTemp - minTemp)
        currentTemp = TempColorMapper.quantize(target, tempStep)
        val step = stepIndexOf(currentTemp)
        if (step != lastHapticStep) {          // 跨档才震：本次档位 != 上次档位
            lastHapticStep = step
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        invalidate()
        onTempChangedListener?.invoke(currentTemp)      // 触摸拖动实时广播（每次 MOVE 后）
    }

    /** lint 要求：消费了 UP 就要有对应的语义点击；本组件无点击动作，仅转发 */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}