package com.example.carhmi

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.fragment.app.Fragment
import com.example.carhmi.databinding.FragmentHvacDemoBinding

class HvacDemoFragment : Fragment() {

    private var _binding: FragmentHvacDemoBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private val delayMillis = 2000L
    private val runnable = Runnable { binding.motionLayout.transitionToEnd() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHvacDemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val motionLayout = binding.motionLayout
        // 监听过渡：started / change(progress) / completed
        motionLayout.setTransitionListener(object : MotionLayout.TransitionListener {
            override fun onTransitionStarted(motionLayout: MotionLayout?, startId: Int, endId: Int) {
                binding.tvStatus.text = "过渡开始"   // 状态文字走 tvStatus，不再挤占温度文字的 tvTemp
            }

            override fun onTransitionChange(
                motionLayout: MotionLayout?, startId: Int, endId: Int, progress: Float
            ) {
                // progress ∈ [0,1]，映射到温度文字（24 → 28 平滑递增示例）
                val t = (24 + progress * 4).toInt()
                binding.tvTemp.text = "${t}°C"
            }

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                binding.tvStatus.text = "过渡完成"
            }

            // 第 4 个必选回调（constraintlayout 2.x）：约束触发时回调，本骨架不依赖，空实现
            override fun onTransitionTrigger(
                motionLayout: MotionLayout?, triggerId: Int, positive: Boolean, progress: Float
            ) = Unit
        })

        // 温度滑块：拖动实时改温度文字（SeekBar 0~30°C，初始 24），与自动过渡的进度映射互不干扰
        binding.seekTemp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvTemp.text = "${progress}°C"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 2s 后自动过渡到 end（对应 motion_scene 的 end 约束）
        handler.postDelayed(runnable, delayMillis)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(runnable)   // 离开页面丢掉回调，防止泄漏
        _binding = null
    }
}