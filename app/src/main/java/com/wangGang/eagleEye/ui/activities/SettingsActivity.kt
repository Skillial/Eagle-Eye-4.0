package com.wangGang.eagleEye.ui.activities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wangGang.eagleEye.R
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.databinding.ActivitySettingsBinding
import com.wangGang.eagleEye.ui.adapters.CommandListAdapter
import com.wangGang.eagleEye.ui.adapters.ProcessingOrderListAdapter
import com.woxthebox.draglistview.DragListView

class SettingsActivity : AppCompatActivity() {

    companion object {
        private val TAG = "SettingsActivity"

        private val COMMAND_ITEMS = arrayListOf(
            Pair(0L, "SR"),
            Pair(1L, "Dehaze"),
            Pair(2L, "Upscale")
        )

        private val DEFAULT_PROCESSING_ORDER = listOf("SR", "Dehaze", "Upscale")

        private val SCALING_FACTORS = listOf(1, 2, 4, 8, 16)
    }

    // Views
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var backButton: ImageButton
    private lateinit var superResolutionSwitch: SwitchCompat

    /* === Switches === */
    private lateinit var dehazeSwitch: SwitchCompat
    private lateinit var gridOverlaySwitch: SwitchCompat

    /* === SeekBar === */
    private lateinit var scaleSeekBar: SeekBar
    private lateinit var scalingLabel: TextView

    /* === RecyclerViews === */
    private lateinit var commandListRecyclerView: RecyclerView
    private lateinit var processingOrderDragListView: DragListView

    // Adapters
    private lateinit var commandListAdapter: CommandListAdapter
    private lateinit var processingOrderListAdapter: ProcessingOrderListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        assignViews()
        setupSwitchButtons()
        setupScaleSeekBar()
        setupBackButton()
        setupCommandListRecyclerView()
        setupProcessingOrderListView()
    }

    private fun assignViews() {
        backButton = binding.btnBack
        superResolutionSwitch = binding.switchSuperResolution
        gridOverlaySwitch = binding.switchGridOverlay
        dehazeSwitch = binding.switchDehaze
        scaleSeekBar = binding.scaleSeekbar
        scalingLabel = binding.scalingLabel
        commandListRecyclerView = binding.sourceListView
        processingOrderDragListView = binding.targetListView
    }

    private fun setupBackButton() {
        backButton.setOnClickListener { finish() }
    }

    private fun setupSwitchButtons() {
        superResolutionSwitch.isChecked = ParameterConfig.isSuperResolutionEnabled()
        dehazeSwitch.isChecked = ParameterConfig.isDehazeEnabled()
        gridOverlaySwitch.isChecked = ParameterConfig.isGridOverlayEnabled()

        superResolutionSwitch.setOnCheckedChangeListener { _, isChecked ->
            ParameterConfig.setSuperResolutionEnabled(isChecked)
            if (isChecked) {
                ParameterConfig.setDehazeEnabled(false)
                dehazeSwitch.isChecked = false
            }
        }
        dehazeSwitch.setOnCheckedChangeListener { _, isChecked ->
            ParameterConfig.setDehazeEnabled(isChecked)
            if (isChecked) {
                ParameterConfig.setSuperResolutionEnabled(false)
                superResolutionSwitch.isChecked = false
            }
        }
        gridOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            ParameterConfig.setGridOverlayEnabled(isChecked)
        }
    }

    private fun setupScaleSeekBar() {
        val current = ParameterConfig.getScalingFactor()
        val currentIndex = SCALING_FACTORS.indexOf(current).coerceAtLeast(0)
        scaleSeekBar.progress = currentIndex
        scalingLabel.text = "Scale Factor: $current"

        scaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val chosenFactor = SCALING_FACTORS[progress]
                ParameterConfig.setScalingFactor(chosenFactor)
                scalingLabel.text = "Scale Factor: $chosenFactor"

                // If scaling factor is less than 8, ensure that "Upscale" is at the bottom.
                if (chosenFactor >= 8) {
                    enforceUpscaleAtBottom(processingOrderListAdapter)
                    updateProcessingOrder(processingOrderListAdapter.itemList)
                }
                processingOrderListAdapter.notifyDataSetChanged()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

    }

    private fun setupProcessingOrderListView() {
        // Retrieve stored processing order, ensuring "Upscale" is at the end.
        val storedOrder = getStoredOrder()
        Log.d("SettingsActivity", "Stored order: $storedOrder")
        val items = storedOrder.mapIndexed { index, title ->
            Pair(index.toLong(), title)
        }.toCollection(arrayListOf())

        processingOrderListAdapter = ProcessingOrderListAdapter(
            itemList = items,
            layoutId = R.layout.processing_order_list_item,
            grabHandleId = R.id.item_layout,
            dragOnLongPress = true
        )

        setupInternalDragAndDrop()
        setupProcessingOrderListViewConfig()
        setupDragAndDropFromSourceList(processingOrderListAdapter)
        setupDragCallback()
        setupSwipeToDelete()
    }

    private fun setupCommandListRecyclerView() {
        commandListAdapter = CommandListAdapter(COMMAND_ITEMS)
        commandListRecyclerView.layoutManager = LinearLayoutManager(this)
        commandListRecyclerView.adapter = commandListAdapter
    }

    private fun setupProcessingOrderListViewConfig() {
        processingOrderDragListView.setLayoutManager(LinearLayoutManager(this))
        processingOrderDragListView.setAdapter(processingOrderListAdapter, true)
        processingOrderDragListView.recyclerView.isVerticalScrollBarEnabled = true
        processingOrderDragListView.setCanDragHorizontally(false)
        processingOrderDragListView.setCanDragVertically(true)
        processingOrderDragListView.isDragEnabled = true
    }

    private fun setupInternalDragAndDrop() {
        processingOrderDragListView.setDragListListener(object : DragListView.DragListListenerAdapter() {
            override fun onItemDragStarted(position: Int) = Unit

            override fun onItemDragEnded(fromPosition: Int, toPosition: Int) {
                // If scaling factor is ≥8, ensure that "Upscale" is fixed at the bottom.
                if (ParameterConfig.isScalingFactorGreaterThanOrEqual8()) {
                    enforceUpscaleAtBottom(processingOrderListAdapter)
                    processingOrderListAdapter.notifyDataSetChanged()
                }
                updateProcessingOrder(processingOrderListAdapter.itemList)
            }
        })
    }

    private fun setupDragCallback() {
        processingOrderDragListView.setDragListCallback(object : DragListView.DragListCallbackAdapter() {
            override fun canDragItemAtPosition(dragPosition: Int): Boolean {
                val item = processingOrderListAdapter.itemList[dragPosition]
                // When scaling factor is >=8, disallow dragging of "Upscale"
                return if (ParameterConfig.isScalingFactorGreaterThanOrEqual8() &&
                    item.second.equals("Upscale", ignoreCase = true)) {
                    false
                } else {
                    true
                }
            }

            override fun canDropItemAtPosition(dropPosition: Int): Boolean {
                // Always allow dropping (or you can add additional logic here)
                return true
            }
        })
    }

    private fun setupDragAndDropFromSourceList(adapter: ProcessingOrderListAdapter) {
        processingOrderDragListView.recyclerView.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    Log.d("DragListener", "ACTION_DRAG_STARTED")
                    true
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    Log.d("DragListener", "ACTION_DRAG_ENTERED")
                    view.alpha = 0.8f
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    Log.d("DragListener", "ACTION_DRAG_EXITED")
                    view.alpha = 1.0f
                    true
                }
                DragEvent.ACTION_DROP -> {
                    Log.d("DragListener", "ACTION_DROP")
                    view.alpha = 1.0f
                    val droppedItem = event.localState as? Pair<Long, String>
                    if (droppedItem != null && adapter.itemList.none { it.second.equals(droppedItem.second, ignoreCase = true) }) {
                        adapter.itemList.add(droppedItem)
                        adapter.notifyDataSetChanged()
                        updateProcessingOrder(adapter.itemList)
                    }
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    Log.d("DragListener", "ACTION_DRAG_ENDED")
                    view.alpha = 1.0f
                    true
                }
                else -> {
                    Log.d("DragListener", "Unknown action: ${event.action}")
                    true
                }
            }
        }
    }

    private fun setupSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            private val paint = Paint().apply {
                color = Color.RED
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                (processingOrderDragListView.adapter as? ProcessingOrderListAdapter)?.let { adapter ->
                    adapter.itemList.removeAt(pos)
                    adapter.notifyItemRemoved(pos)
                    updateProcessingOrder(adapter.itemList)
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    if (dX < 0) {
                        c.drawRect(
                            itemView.right.toFloat() + dX,
                            itemView.top.toFloat(),
                            itemView.right.toFloat(),
                            itemView.bottom.toFloat(),
                            paint
                        )
                    } else if (dX > 0) {
                        c.drawRect(
                            itemView.left.toFloat(),
                            itemView.top.toFloat(),
                            itemView.left.toFloat() + dX,
                            itemView.bottom.toFloat(),
                            paint
                        )
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(processingOrderDragListView.recyclerView)
    }

    private fun getStoredOrder(): MutableList<String> {
        val order = ParameterConfig.getProcessingOrder().toMutableList()
        Log.d(TAG, "getStoredOrder() - order: $order")
        if (order.first() == "") {
            // Remove empty string
            order.removeAt(0)
            order.addAll(DEFAULT_PROCESSING_ORDER)
        }
        return order
    }

    private fun updateProcessingOrder(newOrder: List<Pair<Long, String>>) {
        val orderTitles = newOrder.map { it.second }
        ParameterConfig.setProcessingOrder(orderTitles)
    }

    private fun enforceUpscaleAtBottom(adapter: ProcessingOrderListAdapter) {
        val currentList = adapter.itemList
        val upscaleItem = currentList.find { it.second.equals("Upscale", ignoreCase = true) }
        if (upscaleItem != null) {
            currentList.removeAll { it.second.equals("Upscale", ignoreCase = true) }
            currentList.add(upscaleItem)
        }
    }

}
