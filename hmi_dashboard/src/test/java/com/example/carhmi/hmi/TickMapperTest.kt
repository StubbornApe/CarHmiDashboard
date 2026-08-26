package com.example.carhmi.hmi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TickMapperTest {

    private fun mapper(
        max: Int = 240,
        start: Float = 135f,
        sweep: Float = 270f
    ) = TickMapper(max, start, sweep)

    private val DELTA = 1e-4f

    @Test
    fun `主刻度 20 步进段数为 12`() {
        assertEquals(12, mapper().tickCount(20))
    }

    @Test
    fun `副刻度 5 步进段数为 48`() {
        assertEquals(48, mapper().tickCount(5))
    }

    @Test
    fun `主刻度步进角为 22_5 度`() {
        assertEquals(22.5f, mapper().stepAngle(20), DELTA)
    }

    @Test
    fun `第 0 根刻度角度为起点角`() {
        assertEquals(135f, mapper().angleForTick(0, 20), DELTA)
    }

    @Test
    fun `第 12 根主刻度角度为终点角`() {
        assertEquals(405f, mapper().angleForTick(12, 20), DELTA)
    }

    @Test
    fun `fractionForSpeed 边界与越界夹取`() {
        val m = mapper()
        assertEquals(0f, m.fractionForSpeed(0f), DELTA)
        assertEquals(1f, m.fractionForSpeed(240f), DELTA)
        assertEquals(0.5f, m.fractionForSpeed(120f), DELTA)
        assertEquals(0f, m.fractionForSpeed(-10f), DELTA)
        assertEquals(1f, m.fractionForSpeed(999f), DELTA)
    }

    @Test
    fun `主刻度位置被 isMajorIndex 识别`() {
        val m = mapper()
        assertEquals(true, m.isMajorIndex(0, 20, 5))
        assertEquals(true, m.isMajorIndex(4, 20, 5))    // 20 km/h：主刻度
        assertEquals(false, m.isMajorIndex(1, 20, 5))   // 5 km/h：副刻度
        assertEquals(false, m.isMajorIndex(3, 20, 5))   // 15 km/h：副刻度
    }

    @Test
    fun `非法量程构造时抛 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            TickMapper(maxSpeed = 0, startAngle = 135f, sweepAngle = 270f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TickMapper(maxSpeed = 240, startAngle = 135f, sweepAngle = 0f)
        }
    }
}