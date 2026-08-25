package com.example.carhmi

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardViewModelTest {

    @Test
    fun `初值 speed 为 60`() {
        val vm = DashboardViewModel()
        assertEquals(60f, vm.speed.value, 1e-4f)
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