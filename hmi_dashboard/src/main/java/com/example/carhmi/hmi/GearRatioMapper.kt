package com.example.carhmi.hmi

/**
 * 「速度 ↔ 档位 ↔ 转速」联动纯逻辑（Day 7 新增，模拟自动档换挡策略）。
 * 模型：rpm = IDLE_RPM + speed × gearRatio[gear-1]（怠速 800 + 车速按档位斜率线性换算）。
 * 升档：转速 ≥ SHIFT_UP_RPM（6000）→ 升一档，转速随即按新档位斜率回落到低转（换挡手感）。
 * 降档：转速 ≤ SHIFT_DOWN_RPM（2000）且非 1 档 → 降一档，转速抬升。
 * 档位 D1~D6；静止（speed ≤ 0）强制回 1 档怠速。
 */
class GearRatioMapper(
    private val gearRatios: DoubleArray = doubleArrayOf(80.0, 65.0, 52.0, 42.0, 32.0, 24.0),
    private val idleRpm: Double = 800.0,
    private val shiftUpRpm: Double = 6000.0,
    private val shiftDownRpm: Double = 2000.0
) {
    init {
        require(gearRatios.size in 2..6) { "档位表需 2~6 档, 实际=${gearRatios.size}" }
        require(shiftUpRpm > shiftDownRpm) { "升档点必须高于降档点" }
    }

    val gearCount: Int get() = gearRatios.size

    /** 给定速度和档位求转速（档位越界自动夹到 [1, gearCount]） */
    fun rpmOf(speedKmh: Float, gear: Int): Float {
        val g = gear.coerceIn(1, gearRatios.size)
        return (idleRpm + speedKmh * gearRatios[g - 1]).toFloat()
    }

    /** 档位 → 档位显示文本（D1~D6） */
    fun gearLabel(gear: Int): String = "D${gear.coerceIn(1, gearRatios.size)}"

    /**
     * 自动换挡：给定当前车速与档位，返回策略应处的档位。
     * 先升后降，每步都按「当前档位下的实际转速」重新判定——换挡会改变转速，必须重算。
     */
    fun shift(speedKmh: Float, gear: Int): Int {
        var g = gear.coerceIn(1, gearRatios.size)
        if (speedKmh <= 0f) return 1                       // 静止：回到 1 档怠速
        while (rpmOf(speedKmh, g) >= shiftUpRpm && g < gearRatios.size) g++  // 转速太高：连升档
        while (rpmOf(speedKmh, g) < shiftDownRpm && g > 1) g--               // 转速太低：连降档
        return g
    }

    /** 档位变化后应展示的转速（升档回落 / 降档抬升后的稳定值） */
    fun rpmAfterShift(speedKmh: Float, toGear: Int): Float = rpmOf(speedKmh, toGear.coerceIn(1, gearRatios.size))
}