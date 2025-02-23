package com.wangGang.eagleEye.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wangGang.eagleEye.databinding.ItemAlgorithmBinding

interface ItemTouchHelperAdapter {
    fun onItemMove(fromPosition: Int, toPosition: Int)
}

class DraggableListAdapter(
    private val algoList: MutableList<String>
) : RecyclerView.Adapter<DraggableListAdapter.ViewHolder>(), ItemTouchHelperAdapter {

    inner class ViewHolder(val binding: ItemAlgorithmBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlgorithmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = algoList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.algorithmName.text = algoList[position]
    }

    // Called when the user drags an item from one position to another
    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        // Swap the items and notify the adapter
        val fromItem = algoList.removeAt(fromPosition)
        algoList.add(toPosition, fromItem)
        notifyItemMoved(fromPosition, toPosition)
    }

    // getter
    fun getCurrentList(): List<String> = algoList.toList()
}
