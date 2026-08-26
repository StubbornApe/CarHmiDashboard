package com.example.carhmi.hmi

import kotlin.math.roundToInt

/**
 * 渐变取色纯逻辑（Day 5 新增）。
 * 定义"绿→黄→红"三档渐变色，并提供线性插值，供量程弧、主刻度、数字统一取色。
 * 无 Android 依赖，可 JVM 单测（与 SpeedAngleMapper / TickMapper 同一范式）。
 */
object GradientColors {

    // 三档主色（深色表盘上对比清晰）
    const val GREEN = 0xFF2ECC71.toInt()      // 低速 · 安全区
    const val YELLOW = 0xFFF1C40F.toInt()     // 中速 · 警戒区
    const val RED = 0xFFE74C3C.toInt()        // 高速 · 危险区

    /** 色标：升序 fraction → ARGB 色（0=绿、0.5=黄、1=红） */
    val STOPS: List<Pair<Float, Int>> = listOf(
        0f to GREEN,
        0.5f to YELLOW,
        1f to RED
    )

    /** 供 SweepGradient 直接使用的色标数组（与 STOPS 一致） */
    val COLORS: IntArray = intArrayOf(GREEN, YELLOW, RED)

    /** 按占比 fraction∈[0,1] 取渐变色；越界夹取到端点色 */
    fun interpolate(fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        for (i in 0 until STOPS.size - 1) {
            val (aFrac, aColor) = STOPS[i]
            val (bFrac, bColor) = STOPS[i + 1]
            if (f <= bFrac) {
                val t = if (bFrac == aFrac) 0f else (f - aFrac) / (bFrac - aFrac)
                return lerpColor(aColor, bColor, t)
            }
        }
        return STOPS.last().second
    }

    /** 两个 ARGB 颜色线性插值（R/G/B/A 各自独立通道混合） */
    fun lerpColor(from: Int, to: Int, t: Float): Int {
        val tc = t.coerceIn(0f, 1f)
        val a = from ushr 24 and 0xFF
        val r = from ushr 16 and 0xFF
        val g = from ushr 8 and 0xFF
        val b = from and 0xFF
        val ta = to ushr 24 and 0xFF
        val tr = to ushr 16 and 0xFF
        val tg = to ushr 8 and 0xFF
        val tb = to and 0xFF
        val mix = { x: Int, y: Int -> (x + (y - x) * tc).roundToInt() }
        return (mix(a, ta) shl 24) or (mix(r, tr) shl 16) or (mix(g, tg) shl 8) or mix(b, tb)
    }
}