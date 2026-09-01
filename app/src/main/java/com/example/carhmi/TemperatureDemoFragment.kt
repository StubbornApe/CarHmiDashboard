package com.example.carhmi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.carhmi.databinding.FragmentTemperatureDemoBinding

/** 温度条 Demo（Day 9 新增）：展示 TemperatureStripView 拖动调温 + 触觉反馈 */
class TemperatureDemoFragment : Fragment() {

    private var _binding: FragmentTemperatureDemoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTemperatureDemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tempStrip.setTemp(24f)   // 进入页面初始 24℃（量程中位偏下，方便左右都拖）
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null   // viewBinding 防泄漏，与 Day 8 各 Fragment 一致
    }
}