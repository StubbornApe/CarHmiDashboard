package com.example.carhmi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carhmi.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HomeDemoBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HomeDemoBinding { entry -> findNavController().navigate(entry.actionId) }
        binding.rvDemos.adapter = adapter
        binding.rvDemos.layoutManager = LinearLayoutManager(requireContext())
        adapter.submit(
            listOf(
                DemoEntry("仪表盘 Demo（双表＋插值器）", R.id.action_home_to_dashboardDemo),
                DemoEntry("空调面板 Demo（MotionLayout）", R.id.action_home_to_hvacDemo),
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}