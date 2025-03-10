package com.wangGang.eagleEye.ui.adapters

import android.content.ClipData
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wangGang.eagleEye.R

class CommandListAdapter(
    val items: ArrayList<Pair<Long, String>>
) : RecyclerView.Adapter<CommandListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Inflate the same list item layout (or create a dedicated one for source items)
        val view = LayoutInflater.from(parent.context).inflate(R.layout.processing_order_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textView.text = item.second
        holder.itemView.tag = item

        // Set a long click listener to start a drag-and-drop operation.
        holder.itemView.setOnLongClickListener { view ->
            // Create a ClipData object with the item's text.
            val clipData = ClipData.newPlainText("command", item.second)
            // Create a DragShadowBuilder using the view.
            val shadowBuilder = View.DragShadowBuilder(view)
            // Start drag-and-drop; pass the item as local state.
            view.startDragAndDrop(clipData, shadowBuilder, item, 0)
            true // consume the long press event
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Assuming the layout has a TextView with ID "text"
        val textView: TextView = itemView.findViewById(R.id.text)
    }
}
