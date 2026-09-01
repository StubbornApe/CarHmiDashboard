package com.example.carhmi.hmi

/**
 * 温度 → 颜色 / 步进量化 纯逻辑（Day 9 新增）。
 * 16℃=蓝、24℃=白、32℃=红；fraction 两段线性插值（蓝→白→红）。
 * 无 Android 依赖，可 JVM 单测（与 GradientColors / SpeedAngleMapper 同一范式）。
 */
object TempColorMapper {

    const val BLUE = 0xFF1E88E5.toInt()   // 低温 · 冷（车机夜间蓝）
    const val WHITE = 0xFFFFFFFF.toInt()  // 中温 · 舒适
    const val RED = 0xFFE53935.toInt()    // 高温 · 热

    /** 色标：升序 fraction → ARGB 色（0=蓝、0.5=白、1=红） */
    val STOPS: List<Pair<Float, Int>> = listOf(
        0f to BLUE,
        0.5f to WHITE,
        1f to RED
    )

    /** 按占比 fraction∈[0,1] 取渐变色；越界夹取端点色。两段式：蓝→白(0~0.5)、白→红(0.5~1) */
    fun colorFor(fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        for (i in 0 until STOPS.size - 1) {
            val (aFrac, aColor) = STOPS[i]
            val (bFrac, bColor) = STOPS[i + 1]
            if (f <= bFrac) {
                val t = if (bFrac == aFrac) 0f else (f - aFrac) / (bFrac - aFrac)
                return GradientColors.lerpColor(aColor, bColor, t)
            }
        }
        return STOPS.last().second
    }

    /** 温度按 step 向下取整量化：16.3→16.0、16.6→16.5（step=0.5） */
    fun quantize(temp: Float, step: Float): Float {
        return (temp / step).toInt() * step
    }
}