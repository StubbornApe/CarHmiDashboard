package com.example.carhmi.hmi

import kotlin.math.atan2
import kotlin.math.roundToInt
import java.lang.Math

/**
 * 速度表"速度 ↔ 角度 ↔ 触摸坐标"的纯换算逻辑（无 Android View 依赖，Day 4 为其编写 JUnit 单测）。
 * 角度体系与 Day 2 刻度盘一致：0° 在 3 点钟、顺时针为正、起点 startAngle、扫掠 sweepAngle。
 */
class SpeedAngleMapper(
    private val maxSpeed: Int,
    private val startAngle: Float,
    private val sweepAngle: Float
) {
    init {
        require(maxSpeed > 0) { "maxSpeed 必须大于 0, 实际=$maxSpeed" }
        require(sweepAngle in 1f..360f) { "sweepAngle 必须在 (0,360], 实际=$sweepAngle" }
        require(startAngle in 0f..360f) { "startAngle 必须在 [0,360], 实际=$startAngle" }
    }

    /** 速度 → 表盘角度（用于画指针） */
    fun speedToAngle(speed: Float): Float =
        startAngle + (speed.toFloat() / maxSpeed) * sweepAngle

    /** 归一化角度（0~360，3 点钟为 0°）→ 速度（0~maxSpeed）。
     *  当角度落入量程外的死区缺口时要吸附到较近的量程边界（起点→速度 0，终点→maxSpeed）。 */
    fun angleToSpeed(angleDeg: Float): Float {
        // 相对起点(135°)的顺时钟偏移，必须用整周 360 取模（量程跨 0°，用 sweepAngle=270 会截错）
        val relative = (angleDeg - startAngle + 360f) % 360f
        val snapped = if (relative > sweepAngle) {
            // 落在量程外缺口（如底部 90° 死区）：按到两端距离吸附到较近边界
            val gapMid = sweepAngle + (360f - sweepAngle) / 2f   // 缺口中线
            if (relative >= gapMid) 0f else sweepAngle          // 距起点近→0；距终点近→max
        } else relative
        return (snapped / sweepAngle) * maxSpeed
    }

    /** 触摸坐标 → 归一化角度（0~360），3 点钟为 0° */
    fun angleFromTouch(x: Float, y: Float, cx: Float, cy: Float): Float {
        val dx = x - cx
        val dy = y - cy
        val rad = atan2(dy.toDouble(), dx.toDouble())
        val deg = Math.toDegrees(rad)
        return (deg + 360.0).mod(360.0).toFloat()
    }

    /** 速度显示取整（Day 4 用于标题/文案） */
    fun displaySpeed(speed: Float): Int = speed.roundToInt()

    /** 判定触摸点是否落在有效环带内（radiusMin~radiusMax） */
    fun isInTouchRing(
        x: Float, y: Float, cx: Float, cy: Float,
        radiusMin: Float, radiusMax: Float
    ): Boolean {
        val r = kotlin.math.hypot((x - cx).toDouble(), (y - cy).toDouble()).toFloat()
        return r in radiusMin..radiusMax
    }
}