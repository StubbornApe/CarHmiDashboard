package com.example.carhmi

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.carhmi.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 通过委托获得 Activity 作用域的单例 ViewModel（现成实现，不用手动 new）
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 监听仪表数据：STARTED 时收集、STOPPED 时自动取消，值一变就驱动重绘
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.startSimulation()      // App 一起就自动 0→240→0 循环
                viewModel.speed.collect { speed ->
                    binding.speedometerView.setSpeed(speed)   // 内部 invalidate() + 气泡回调
                    binding.tvSpeed.text = "${speed.toInt()} km/h"
                }
            }
        }
    }
}