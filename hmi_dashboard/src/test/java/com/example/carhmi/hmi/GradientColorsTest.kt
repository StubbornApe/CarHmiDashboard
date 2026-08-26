package com.example.carhmi.hmi

import org.junit.Assert.assertEquals
import org.junit.Test

class GradientColorsTest {

    @Test
    fun `fraction 0 返回绿色端点`() {
        assertEquals(GradientColors.GREEN, GradientColors.interpolate(0f))
    }

    @Test
    fun `fraction 1 返回红色端点`() {
        assertEquals(GradientColors.RED, GradientColors.interpolate(1f))
    }

    @Test
    fun `fraction 0_5 返回黄色中点`() {
        assertEquals(GradientColors.YELLOW, GradientColors.interpolate(0.5f))
    }

    @Test
    fun `fraction 0_25 为绿到黄的线性中点`() {
        val expected = GradientColors.lerpColor(GradientColors.GREEN, GradientColors.YELLOW, 0.5f)
        assertEquals(expected, GradientColors.interpolate(0.25f))
    }

    @Test
    fun `越界 fraction 夹取到端点色`() {
        assertEquals(GradientColors.GREEN, GradientColors.interpolate(-1f))
        assertEquals(GradientColors.RED, GradientColors.interpolate(2f))
    }

    @Test
    fun `lerpColor 通道线性且 alpha 完整`() {
        val mid = GradientColors.lerpColor(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f)
        assertEquals(0xFF808080.toInt(), mid)
    }
}