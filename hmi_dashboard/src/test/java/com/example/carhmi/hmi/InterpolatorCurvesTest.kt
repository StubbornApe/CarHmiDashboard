package com.example.carhmi.hmi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterpolatorCurvesTest {

    @Test
    fun `linear 端点正确`() {
        assertEquals(0f, InterpolatorCurves.linear(0f), 1e-4f)
        assertEquals(1f, InterpolatorCurves.linear(1f), 1e-4f)
    }

    @Test
    fun `easeInOut 端点与中点正确`() {
        assertEquals(0f, InterpolatorCurves.easeInOut(0f), 1e-4f)
        assertEquals(1f, InterpolatorCurves.easeInOut(1f), 1e-4f)
        assertEquals(0.5f, InterpolatorCurves.easeInOut(0.5f), 1e-4f)
    }

    @Test
    fun `easeInOut 前慢后快`() {
        assertTrue(InterpolatorCurves.easeInOut(0.25f) < 0.25f)   // 前段慢于线性
        assertTrue(InterpolatorCurves.easeInOut(0.75f) > 0.75f)   // 后段快于线性
    }

    @Test
    fun `overshoot 终点前越界、终点收敛`() {
        assertTrue(InterpolatorCurves.overshoot(0.8f) > 1f)       // 冲过目标 >1
        assertEquals(1f, InterpolatorCurves.overshoot(1f), 1e-4f)
    }

    @Test
    fun `anticipate 起始为负、端点归位`() {
        assertTrue(InterpolatorCurves.anticipate(0.2f) < 0f)      // 反向蓄力 <0
        assertEquals(0f, InterpolatorCurves.anticipate(0f), 1e-4f)
        assertEquals(1f, InterpolatorCurves.anticipate(1f), 1e-4f)
    }

    @Test
    fun `overshoot 张力越大过冲越大`() {
        val soft = InterpolatorCurves.overshoot(0.8f, tension = 1f)
        val hard = InterpolatorCurves.overshoot(0.8f, tension = 3f)
        assertTrue(hard > soft)
    }

    @Test
    fun `cubicBezier 端点正确`() {
        assertEquals(0f, InterpolatorCurves.cubicBezier(0f), 1e-4f)
        assertEquals(1f, InterpolatorCurves.cubicBezier(1f), 1e-4f)
    }

    @Test
    fun `cubicBezier 默认曲线在中点为 0_5 对称`() {
        assertEquals(0.5f, InterpolatorCurves.cubicBezier(0.5f), 0.01f)
    }

    @Test
    fun `越界 t 夹取到端点`() {
        assertEquals(0f, InterpolatorCurves.easeInOut(-1f), 1e-4f)
        assertEquals(1f, InterpolatorCurves.easeInOut(2f), 1e-4f)
    }

    // ---- Day 8 新增：动画进度计算 progress() ----

    @Test
    fun `progress 线性端点与中点`() {
        assertEquals(0f, InterpolatorCurves.progress(0f, 240f, InterpolatorCurves::linear, 0f), 1e-4f)
        assertEquals(120f, InterpolatorCurves.progress(0f, 240f, InterpolatorCurves::linear, 0.5f), 1e-4f)
        assertEquals(240f, InterpolatorCurves.progress(0f, 240f, InterpolatorCurves::linear, 1f), 1e-4f)
    }

    @Test
    fun `progress 过冲中间越界再收敛`() {
        // ::overshoot 带默认参数，方法引用是 (Float,Float)->Float，需用 lambda 包装成 (Float)->Float
        val mid = InterpolatorCurves.progress(0f, 240f, { t -> InterpolatorCurves.overshoot(t) }, 0.8f)
        assertTrue("过冲中途应超过 end 240", mid > 240f)
        assertEquals("t=1 收敛回 end", 240f,
            InterpolatorCurves.progress(0f, 240f, { t -> InterpolatorCurves.overshoot(t) }, 1f), 1e-4f)
    }

    @Test
    fun `progress 越界 t 夹取`() {
        assertEquals(0f, InterpolatorCurves.progress(0f, 240f, InterpolatorCurves::linear, -1f), 1e-4f)
        assertEquals(240f, InterpolatorCurves.progress(0f, 240f, InterpolatorCurves::linear, 2f), 1e-4f)
    }
}
