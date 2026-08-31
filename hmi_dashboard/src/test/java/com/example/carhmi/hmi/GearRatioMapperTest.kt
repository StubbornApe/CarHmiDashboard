package com.example.carhmi.hmi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GearRatioMapperTest {

    // 用一套「特征明确」的小齿比表，便于手算断言（60km/h：1 档 6800 / 2 档 3800 / 3 档 2300）
    private val mapper = GearRatioMapper(
        gearRatios = doubleArrayOf(100.0, 50.0, 25.0),
        idleRpm = 800.0,
        shiftUpRpm = 6000.0,
        shiftDownRpm = 2000.0
    )

    @Test
    fun `静止时回到 1 档怠速`() {
        assertEquals("静止必须回 1 档", 1, mapper.shift(0f, 4))   // 档位越界也会夹回
        assertEquals("0 车速 1 档 = 怠速 800", 800f, mapper.rpmOf(0f, 1), 0.001f)
    }

    @Test
    fun `同一车速档位越低转速越高`() {
        val speed = 60f
        val rpm1 = mapper.rpmOf(speed, 1)   // 800 + 60×100 = 6800
        val rpm2 = mapper.rpmOf(speed, 2)   // 800 + 60×50  = 3800
        val rpm3 = mapper.rpmOf(speed, 3)   // 800 + 60×25  = 2300
        assertTrue("1 档最高转", rpm1 > rpm2)
        assertTrue("2 档居中", rpm2 > rpm3)
    }

    @Test
    fun `档位越界自动夹取`() {
        assertEquals("1 档下限夹取", 800f + 60f * 100f, mapper.rpmOf(60f, 0), 0.001f)
        assertEquals("3 档上限夹取", 800f + 60f * 25f, mapper.rpmOf(60f, 99), 0.001f)
    }

    @Test
    fun `转速过高时升档并回落`() {
        val g = mapper.shift(60f, 1)          // 1 档 6800 ≥ 6000 → 升 2 档
        assertEquals("60km/h 应从 1 档升到 2 档", 2, g)
        val rpmAfter = mapper.rpmOf(60f, g)   // 2 档 = 3800 < 6000
        assertTrue("升档后转速必须回落到 6000 以下", rpmAfter < 6000f)
    }

    @Test
    fun `转速过低时连降档抬升`() {
        val g = mapper.shift(20f, 3)          // 3 档 1300 → 2 档 1800 → 1 档 2800（while 连降）
        assertEquals("20km/h 应从 3 档连降到 1 档", 1, g)
        assertTrue("降档后转速抬升", mapper.rpmOf(20f, g) > mapper.rpmOf(20f, 3))
    }

    @Test
    fun `高速低档连升到不超红线为止`() {
        val g = mapper.shift(120f, 1)
        // 1 档 12800 → 2 档 6800 → 3 档 3800，升到顶档（3 档）
        assertEquals("120km/h 应连升到顶档", 3, g)
        assertTrue("顶档转速不再超 6000", mapper.rpmOf(120f, g) < 6000f)
    }

    @Test
    fun `档位标签与默认表`() {
        assertEquals("D1", mapper.gearLabel(1))
        assertEquals("D6", GearRatioMapper().gearLabel(6))
        assertEquals("默认档位表有 6 档", 6, GearRatioMapper().gearCount)
    }
}