package com.wangGang.eagleEye.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.woxthebox.draglistview.DragItemAdapter
import com.wangGang.eagleEye.R
import com.wangGang.eagleEye.constants.ParameterConfig

class ProcessingOrderListAdapter (
    itemList: ArrayList<Pair<Long, String>>,
    private val layoutId: Int,
    private val grabHandleId: Int,
    private val dragOnLongPress: Boolean
) : DragItemAdapter<Pair<Long, String>, ProcessingOrderListAdapter.ViewHolder>() {

    init {
        setItemList(itemList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val item = mItemList[position]
        holder.textView.text = item.second
        holder.itemView.tag = item

        val currentItemIsUpscale = item.second.equals("Upscale", ignoreCase = true)
        if (ParameterConfig.isScalingFactorGreaterThanOrEqual8() && currentItemIsUpscale) {
            holder.itemView.setOnLongClickListener { false }
            // Dim the whole item to indicate it's fixed.
            holder.itemView.alpha = 0.5f
        } else {
            holder.itemView.setOnLongClickListener { true }
            holder.itemView.alpha = 1.0f
        }
    }


    override fun getUniqueItemId(position: Int): Long {
        return mItemList[position].first
    }

    inner class ViewHolder(itemView: View) :
        DragItemAdapter.ViewHolder(itemView, grabHandleId, dragOnLongPress) {
        val textView: TextView = itemView.findViewById(R.id.text)
    }


}
