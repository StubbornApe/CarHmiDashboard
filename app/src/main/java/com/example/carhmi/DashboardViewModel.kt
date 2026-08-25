package com.example.carhmi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 仪表盘数据源（Day 4 新增）。
 * 模拟「车载总线/CAN 单向下发车速」：对外只读暴露 StateFlow，
 * 写入口收敛到 updateSpeed()，并在源头做 0~240 越界夹取。
 */
class DashboardViewModel : ViewModel() {

    private val _speed = MutableStateFlow(60f)          // 内部可变状态，初值 60
    val speed: StateFlow<Float> = _speed.asStateFlow()  // 外部只读，只能 collect/读

    /** 模拟 CAN/VHAL 下发车速：越界自动夹取到 [0, MAX_SPEED] */
    fun updateSpeed(value: Float) {
        _speed.value = value.coerceIn(0f, MAX_SPEED)
    }

    private var simulationJob: Job? = null          // 记录模拟协程，便于停止 / 防重复启动

    /** 启动 CAN 周期模拟：0→240 爬升、到顶后减速回 0，往复循环 */
    fun startSimulation() {
        if (simulationJob?.isActive == true) return   // 已在跑则不重复启动
        simulationJob = viewModelScope.launch {
            var speed = 0f
            var phase = 0          // 0=爬升，1=减速
            while (isActive) {     // 协作式：作用域取消时退出
                updateSpeed(speed)                  // 走唯一写入口（含夹取）
                delay(50)                        // 每 150ms 下发一次，~6.7Hz
                speed = if (phase == 0) (speed + 3f).coerceIn(0f, MAX_SPEED)
                else (speed - 3f).coerceIn(0f, MAX_SPEED)
                if (speed >= MAX_SPEED) phase = 1
                if (speed <= 0f) phase = 0
            }
        }
    }

    /** 停止模拟 */
    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    companion object {
        const val MAX_SPEED = 240f   // 与 SpeedometerView 默认量程一致
    }

    override fun onCleared() {
        super.onCleared()
        stopSimulation()
    }
}