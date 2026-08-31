package com.example.carhmi.hmi

/**
 * 角度归一化纯逻辑（Day 8 新增）。
 * 表盘刻度用 startAngle + index*stepAngle 折算，末根角度可能 >360（如 405°）。
 * 绘制刻度/旋转前必须归一化到 [0,360)，否则刻度错位。无 Android 依赖，可 JVM 单测。
 */
object AngleNormalizer {

    /** 把任意角归一化到 [0,360)。对负角也安全：((-90) % 360 + 360) % 360 = 270 */
    fun normalize(degrees: Float): Float {
        val m = degrees % 360f
        return if (m < 0f) m + 360f else m
    }
}