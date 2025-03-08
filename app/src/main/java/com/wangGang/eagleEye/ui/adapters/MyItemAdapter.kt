package com.wangGang.eagleEye.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.woxthebox.draglistview.DragItemAdapter
import com.wangGang.eagleEye.R

class MyItemAdapter(
    itemList: ArrayList<Pair<Long, String>>,
    private val layoutId: Int,
    private val grabHandleId: Int,
    private val dragOnLongPress: Boolean
) : DragItemAdapter<Pair<Long, String>, MyItemAdapter.ViewHolder>() {

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

        if (item.second.equals("Upscale", ignoreCase = true)) {
            holder.itemView.setOnLongClickListener { false }
            holder.grabHandle.visibility = View.INVISIBLE
            // Dim the whole item to indicate it's fixed.
            holder.itemView.alpha = 0.5f
        } else {
            holder.itemView.setOnLongClickListener { true }
            holder.grabHandle.visibility = View.VISIBLE
            holder.itemView.alpha = 1.0f
        }
    }


    override fun getUniqueItemId(position: Int): Long {
        return mItemList[position].first
    }

    inner class ViewHolder(itemView: View) :
        DragItemAdapter.ViewHolder(itemView, grabHandleId, dragOnLongPress) {
        val textView: TextView = itemView.findViewById(R.id.text)
        val grabHandle: View = itemView.findViewById(grabHandleId)
    }
}
