package com.example.carhmi.hmi

import org.junit.Assert.assertEquals
import org.junit.Test

class RpmAngleMapperTest {

    private val mapper = RpmAngleMapper(8000, 135f, 270f)

    @Test
    fun `转速到角度端点与中点`() {
        assertEquals("0 → 起点 135°", 135f, mapper.rpmToAngle(0f), 0.001f)
        assertEquals("8000 → 终点 405°(=45°)", 405f, mapper.rpmToAngle(8000f), 0.001f)
        assertEquals("4000 → 中点 270°", 270f, mapper.rpmToAngle(4000f), 0.001f)
    }

    @Test
    fun `角度到转速往返一致`() {
        val midAngle = mapper.rpmToAngle(4000f)
        assertEquals("中点往返一致", 4000f, mapper.angleToRpm(midAngle), 0.001f)
    }

    @Test
    fun `越界转速夹回量程`() {
        assertEquals("负转速夹到 0", 135f, mapper.rpmToAngle(-100f), 0.001f)
        assertEquals("超量程夹到终点", 405f, mapper.rpmToAngle(9999f), 0.001f)
    }

    @Test
    fun `死区角度吸附到较近边界`() {
        // 缺口 45~135 内取 90°：相对量程位置 315° ≥ 缺口内中线 315° → 判定偏缺口一边 → 回到 0
        assertEquals("缺口内部吸附到起点", 0f, mapper.angleToRpm(90f), 0.001f)
    }

    @Test
    fun `显示取整`() {
        assertEquals(2500, mapper.displayRpm(2500.4f))
        assertEquals(2501, mapper.displayRpm(2500.6f))
    }
}