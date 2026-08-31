package com.example.carhmi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelTest {

    @Test
    fun `初值 speed 为 0 与 ViewModel 一致`() {
        val vm = DashboardViewModel()
        assertEquals(0f, vm.speed.value, 1e-4f)   // 修正：Day 4/7 初值均为 0f，历史测试误写 60
    }

    // ---- Day 8 新增：rpm / gear 联动用例（Day 7 只测了 speed，属欠账）----

    @Test
    fun `updateSpeed 同步推导 rpm 与 gear`() {
        val vm = DashboardViewModel()
        vm.updateSpeed(0f)
        assertEquals(0f, vm.speed.value, 1e-4f)
        assertEquals("静止回 1 档", 1, vm.gear.value)
        assertEquals("1 档怠速 800（默认齿比）", 800f, vm.rpm.value, 1e-4f)

        vm.updateSpeed(120f)                     // 高速 → 连升档，转速回落不超红线
        assertTrue("高速应升到更高档位", vm.gear.value > 1)
        assertTrue("升档后转速应低于红线", vm.rpm.value < 6000f)
    }

    @Test
    fun `updateSpeed 越界输入联动夹取`() {
        val vm = DashboardViewModel()
        vm.updateSpeed(-10f)                     // 脏数据：速度夹到 0，档位回 1、转速怠速
        assertEquals(0f, vm.speed.value, 1e-4f)
        assertEquals(1, vm.gear.value)
        assertEquals(800f, vm.rpm.value, 1e-4f)
    }

    @Test
    fun `updateSpeed 下界越界夹取到 0`() {
        val vm = DashboardViewModel()
        vm.updateSpeed(-10f)      // 负速度（总线脏数据）→ 夹到 0
        assertEquals(0f, vm.speed.value, 1e-4f)
    }

    @Test
    fun `updateSpeed 上界越界夹取到 240`() {
        val vm = DashboardViewModel()
        vm.updateSpeed(999f)      // 超量程 → 夹到 240
        assertEquals(240f, vm.speed.value, 1e-4f)
    }

    @Test
    fun `updateSpeed 合法值原样写入并依次流转`() {
        val vm = DashboardViewModel()
        vm.updateSpeed(0f)
        assertEquals(0f, vm.speed.value, 1e-4f)
        vm.updateSpeed(80f)
        assertEquals(80f, vm.speed.value, 1e-4f)
        vm.updateSpeed(160f)
        assertEquals(160f, vm.speed.value, 1e-4f)
    }
}