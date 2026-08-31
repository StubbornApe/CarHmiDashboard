package com.example.carhmi.hmi

import kotlin.math.roundToInt

/**
 * 转速表「转速 ↔ 表盘角度」纯换算（Day 7 新增）。
 * 与 SpeedAngleMapper 同构（角度体系一致：0°=3 点钟、顺时针、起点 startAngle、扫掠 sweepAngle），
 * 单独建类是为了语义清晰——maxSpeed 换成 maxRpm，避免 "速度表里 240" 的语义套到转速上。
 */
class RpmAngleMapper(
    private val maxRpm: Int,
    private val startAngle: Float,
    private val sweepAngle: Float
) {
    init {
        require(maxRpm > 0) { "maxRpm 必须大于 0, 实际=$maxRpm" }
        require(sweepAngle in 1f..360f) { "sweepAngle 必须在 (0,360], 实际=$sweepAngle" }
        require(startAngle in 0f..360f) { "startAngle 必须在 [0,360], 实际=$startAngle" }
    }

    /** 转速 → 表盘角度（画指针、算进度环扫掠角用），越界夹到 [0, maxRpm] */
    fun rpmToAngle(rpm: Float): Float =
        startAngle + (rpm.coerceIn(0f, maxRpm.toFloat()) / maxRpm) * sweepAngle

    /** 角度 → 转速：落入 270° 缺口死区时吸附到较近边界（策略同 SpeedAngleMapper，保持往返一致） */
    fun angleToRpm(angleDeg: Float): Float {
        val relative = (angleDeg - startAngle + 360f) % 360f
        val snapped = if (relative > sweepAngle) {
            val gapMid = sweepAngle + (360f - sweepAngle) / 2f
            if (relative >= gapMid) 0f else sweepAngle
        } else relative
        return (snapped / sweepAngle) * maxRpm
    }

    /** 转速显示取整 */
    fun displayRpm(rpm: Float): Int = rpm.roundToInt()
}