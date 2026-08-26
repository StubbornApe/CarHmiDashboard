package com.example.carhmi.hmi

/**
 * 刻度布局纯逻辑（Day 5 新增）：把"速度 ↔ 刻度段数/步进角/绝对角度/沿弧占比"从 View 抽出。
 * 与 SpeedAngleMapper 同一设计理念——可 JVM 单测的数学换算，View 只负责画。
 */
class TickMapper(
    private val maxSpeed: Int,
    private val startAngle: Float,
    private val sweepAngle: Float
) {
    init {
        require(maxSpeed > 0) { "maxSpeed 必须大于 0, 实际=$maxSpeed" }
        require(sweepAngle in 1f..360f) { "sweepAngle 必须在 (0,360], 实际=$sweepAngle" }
        require(startAngle in 0f..360f) { "startAngle 必须在 [0,360], 实际=$startAngle" }
    }

    /** 该步进下的刻度段数（如 maxSpeed=240、majorStep=20 → 12 段） */
    fun tickCount(step: Int): Int = maxSpeed / step

    /** 相邻刻度步进角（段数等分量程扫掠角） */
    fun stepAngle(step: Int): Float = sweepAngle / tickCount(step)

    /** 第 index 根刻度的绝对角度（与 drawArc 同约定：0°=3 点钟、顺时针为正） */
    fun angleForTick(index: Int, step: Int): Float =
        startAngle + index * stepAngle(step)

    /** 速度沿量程的占比 [0,1]（渐变取色定位用） */
    fun fractionForSpeed(speed: Float): Float =
        (speed / maxSpeed).coerceIn(0f, 1f)

    /** 第 index 根刻度代表的速度值 */
    fun speedAtTick(index: Int, step: Int): Int = index * step

    /** 该索引是否为「主刻度」位置（主/副步进比整除）——副刻度绘制时应跳过 */
    fun isMajorIndex(index: Int, majorStep: Int, minorStep: Int): Boolean =
        index % (majorStep / minorStep) == 0
}