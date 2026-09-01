package com.example.carhmi.hmi

import org.junit.Assert.assertEquals
import org.junit.Test

class TempColorMapperTest {

    @Test
    fun `fraction 0 返回蓝色端点`() {
        assertEquals(TempColorMapper.BLUE, TempColorMapper.colorFor(0f))
    }

    @Test
    fun `fraction 0_5 返回白色中点`() {
        assertEquals(TempColorMapper.WHITE, TempColorMapper.colorFor(0.5f))
    }

    @Test
    fun `fraction 1 返回红色端点`() {
        assertEquals(TempColorMapper.RED, TempColorMapper.colorFor(1f))
    }

    @Test
    fun `fraction 0_25 为蓝到白的线性中点`() {
        val expected = GradientColors.lerpColor(TempColorMapper.BLUE, TempColorMapper.WHITE, 0.5f)
        assertEquals(expected, TempColorMapper.colorFor(0.25f))
    }

    @Test
    fun `越界 fraction 夹取到端点色`() {
        assertEquals(TempColorMapper.BLUE, TempColorMapper.colorFor(-1f))
        assertEquals(TempColorMapper.RED, TempColorMapper.colorFor(2f))
    }

    @Test
    fun `quantize 按步进向下取整`() {
        assertEquals(16.0f, TempColorMapper.quantize(16f, 0.5f), 1e-4f)
        assertEquals(16.0f, TempColorMapper.quantize(16.3f, 0.5f), 1e-4f)
        assertEquals(16.5f, TempColorMapper.quantize(16.6f, 0.5f), 1e-4f)
        assertEquals(24.0f, TempColorMapper.quantize(24.2f, 0.5f), 1e-4f)
    }
}