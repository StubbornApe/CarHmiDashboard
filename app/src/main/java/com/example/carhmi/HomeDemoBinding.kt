package com.example.carhmi

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.carhmi.databinding.ItemDemoBinding

/** 列表条目（数据）：标题 + 跳转动作 id */
data class DemoEntry(val title: String, val actionId: Int)

/** RecyclerView 适配器：item_demo → ItemDemoBinding（与 Navigation 解耦，跳转由回调决定） */
class HomeDemoBinding(private val onClick: (DemoEntry) -> Unit) :
    RecyclerView.Adapter<HomeDemoBinding.VH>() {

    private val items = mutableListOf<DemoEntry>()

    fun submit(list: List<DemoEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemDemoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemDemoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.tvDemoTitle.text = item.title
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}