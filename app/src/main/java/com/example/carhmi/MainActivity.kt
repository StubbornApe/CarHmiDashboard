package com.example.carhmi

import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.graphics.Path
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
                viewModel.speed.collect { speed ->
                    binding.speedometerView.setSpeed(speed)   // 内部 invalidate()
                }
            }
        }

        // Day 6：切换指针过渡曲线（只换曲线、不动目标，便于对比"动画手感 = 曲线形状"）
        binding.btnAccel.setOnClickListener {
            binding.speedometerView.setInterpolator(AccelerateDecelerateInterpolator())
            demoTo240()
        }
        binding.btnOvershoot.setOnClickListener {
            binding.speedometerView.setInterpolator(OvershootInterpolator(2f))
            demoTo240()
        }
        binding.btnAnticipate.setOnClickListener {
            binding.speedometerView.setInterpolator(AnticipateInterpolator(2f))
            demoTo240()
        }
        // 在 onCreate（或抽成方法）里：
        binding.btnPath.setOnClickListener {
            // 自定义「先急后缓」曲线：起点坡度陡（起步猛），末段坡度缓（收尾柔）
            val customPath = Path().apply {
                moveTo(0f, 0f)                              // 必须从 (0,0) 起
                cubicTo(0.2f, 0.8f, 0.8f, 1f, 1f, 1f)      // 第一个控制点(0.2,0.8)高 → 起步快
                // 终点已到 (1,1)；如需更复杂可再 cubicTo 续段，但必须停在 (1,1)
            }
            binding.speedometerView.setInterpolator(PathInterpolator(customPath))
            demoTo240()
        }
        binding.btnSimulate.setOnClickListener { viewModel.startSimulation() }   // 开启后指针连续跟车动
    }

    /** 停掉模拟，从当前值平滑动画到 240，完整展示当前 Interpolator 的过渡轨迹 */
    private fun demoTo240() {
        viewModel.stopSimulation()          // 避免打断，让动画一次走完
        binding.speedometerView.setSpeedImmediate(0f)   // 先瞬间复位到统一起点 0，保证每条曲线都能完整演示
        binding.speedometerView.setSpeed(240f)           // 再从 0 平滑动画到 240
    }
}