package com.example.carhmi.hmi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpeedAngleMapperTest {

    /** 缺省参数构造：240 / 135° / 270°（与真实默认一致） */
    private fun mapper(
        max: Int = 240,
        start: Float = 135f,
        sweep: Float = 270f
    ) = SpeedAngleMapper(max, start, sweep)

    private val DELTA = 1e-4f   // 浮点断言允许误差

    @Test
    fun `speedToAngle zero 返回起点角 135`() {
        assertEquals(135f, mapper().speedToAngle(0f), DELTA)
    }

    @Test
    fun `speedToAngle max 返回终点角 405`() {
        assertEquals(405f, mapper().speedToAngle(240f), DELTA)
    }

    @Test
    fun `speedToAngle mid 返回量程中点 270`() {
        assertEquals(270f, mapper().speedToAngle(120f), DELTA)
    }

    @Test
    fun `speedToAngle 对速度线性增长`() {
        val m = mapper()
        // 每 5 km/h 采样一次，角度差应恒为 270/240*5 = 5.625°
        val step = 5
        for (i in 0 until 240 step step) {
            val a0 = m.speedToAngle(i.toFloat())
            val a1 = m.speedToAngle((i + step).toFloat())
            assertEquals(270f / 240f * step, a1 - a0, DELTA)
        }
    }

    @Test
    fun `angleToSpeed 与 speedToAngle 互为往返一致`() {
        val m = mapper()
        for (speed in 0..240 step 5) {
            val roundTrip = m.angleToSpeed(m.speedToAngle(speed.toFloat()))
            assertEquals(speed.toFloat(), roundTrip, DELTA)
        }
    }

    @Test
    fun `angleToSpeed 死区落在底部缺口时吸附到较近边界`() {
        val m = mapper()
        // 起点 135°、扫 270° → 缺口在底部（45°~135° 之间，即换算后 >405°）
        assertEquals(240f, m.angleToSpeed(45f), DELTA)   // 距终点近 → 吸附 maxSpeed
        assertEquals(0f, m.angleToSpeed(90f), DELTA)     // 距起点近 → 吸附 0
    }

    @Test
    fun `非法量程在构造时抛 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpeedAngleMapper(maxSpeed = 0, startAngle = 135f, sweepAngle = 270f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpeedAngleMapper(maxSpeed = 240, startAngle = 135f, sweepAngle = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpeedAngleMapper(maxSpeed = 240, startAngle = 400f, sweepAngle = 270f)
        }
    }
}