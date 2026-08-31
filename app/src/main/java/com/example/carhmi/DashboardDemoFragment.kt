package com.example.carhmi

import android.graphics.Path
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.carhmi.databinding.FragmentDashboardDemoBinding
import com.example.carhmi.hmi.SpeedometerView
import com.example.carhmi.hmi.TachometerView
import kotlinx.coroutines.launch

class DashboardDemoFragment : Fragment() {

    private var _binding: FragmentDashboardDemoBinding? = null
    private val binding get() = _binding!!

    // Activity 作用域：与 HomeFragment 共享同一个 DashboardViewModel（离开再返回数据不丢）
    private val viewModel: DashboardViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardDemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val speedMeter = binding.speedometerView
        val tacho = binding.tachometerView

        // 速度表：沿用 Day 6（指针平滑动画 + Interpolator 切换）
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.speed.collect { speed -> speedMeter.setSpeed(speed) }
            }
        }
        // Day 7：转速表——双表联动（rpm 驱动指针/数字/进度环，gear 驱动档位标签）
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rpm.collect { rpm -> tacho.setRpm(rpm) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.gear.collect { gear -> tacho.setGear("D$gear") }
            }
        }

        // Day 6：切换指针过渡曲线（只换曲线、不动目标，便于对比「动画手感 = 曲线形状」）
        binding.btnAccel.setOnClickListener {
            speedMeter.setInterpolator(AccelerateDecelerateInterpolator())
            demoTo240(speedMeter)
        }
        binding.btnOvershoot.setOnClickListener {
            speedMeter.setInterpolator(OvershootInterpolator(2f))
            demoTo240(speedMeter)
        }
        binding.btnAnticipate.setOnClickListener {
            speedMeter.setInterpolator(AnticipateInterpolator(2f))
            demoTo240(speedMeter)
        }
        binding.btnPath.setOnClickListener {
            // 自定义「先急后缓」曲线：起点坡度陡（起步猛），末段坡度缓（收尾柔）
            val customPath = Path().apply {
                moveTo(0f, 0f)                          // 必须从 (0,0) 起
                cubicTo(0.2f, 0.8f, 0.8f, 1f, 1f, 1f)  // 控制点(0.2,0.8)高 → 起步快，终点停在 (1,1)
            }
            speedMeter.setInterpolator(PathInterpolator(customPath))
            demoTo240(speedMeter)
        }
        binding.btnSimulate.setOnClickListener { viewModel.startSimulation() }
    }

    /** 停掉模拟，从当前值平滑动画到 240，完整展示当前 Interpolator 的过渡轨迹 */
    private fun demoTo240(speedMeter: SpeedometerView) {
        viewModel.stopSimulation()
        speedMeter.setSpeedImmediate(0f)   // 先瞬间复位到统一起点 0，保证每条曲线都能完整演示
        speedMeter.setSpeed(240f)          // 再从 0 平滑动画到 240
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null   // 防内存泄漏（View 生命周期短于 Fragment）
    }
}