package com.wangGang.eagleEye.ui.activities

import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.wangGang.eagleEye.R
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.databinding.ActivitySettingsBinding
import com.wangGang.eagleEye.ui.adapters.MyItemAdapter
import com.wangGang.eagleEye.ui.adapters.SourceListAdapter
import com.wangGang.eagleEye.ui.views.MySwipeRefreshLayout
import com.woxthebox.draglistview.DragListView
import com.woxthebox.draglistview.swipe.ListSwipeHelper.OnSwipeListenerAdapter
import com.woxthebox.draglistview.swipe.ListSwipeItem
import com.woxthebox.draglistview.swipe.ListSwipeItem.SwipeDirection


class SettingsActivity : AppCompatActivity() {

    // Views
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var backButton: ImageButton
    /* Switches */
    private lateinit var superResolutionSwitch: SwitchCompat
    private lateinit var dehazeSwitch: SwitchCompat
    private lateinit var gridOverlaySwitch: SwitchCompat
    /* Scaling Factor */
    private lateinit var scaleSeekBar: SeekBar
    private lateinit var scalingLabel: TextView
    /* Commands List */
    private lateinit var sourceAdapter: SourceListAdapter
    private lateinit var sourceRecyclerView: RecyclerView
    /* Draggable List */
    private lateinit var adapter: MyItemAdapter
    private lateinit var dragListView: DragListView

    private lateinit var mRefreshLayout: MySwipeRefreshLayout


    // Constants
    private val scaleFactors = listOf(1, 2, 4, 8, 16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        assignViews()
        setupSwitchButtons()
        setupScaleSeekBar()
        setupBackButton()
        setupSourceList()
        setupDragListView()
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            finish()
        }
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

        // Initialize
        val current = ParameterConfig.getScalingFactor()
        val currentIndex = scaleFactors.indexOf(current).coerceAtLeast(0)
        scaleSeekBar.progress = currentIndex
        scalingLabel.text = "Scale Factor: $current"

        // Set listener
        scaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val chosenFactor = scaleFactors[progress]
                ParameterConfig.setScalingFactor(chosenFactor)

                scalingLabel.text = "Scale Factor: $chosenFactor"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    private fun assignViews() {
        backButton = binding.btnBack

        // Switches
        superResolutionSwitch = binding.switchSuperResolution
        gridOverlaySwitch = binding.switchGridOverlay
        dehazeSwitch = binding.switchDehaze

        // Scale Factor Setting
        scaleSeekBar = binding.scaleSeekbar
        scalingLabel = binding.scalingLabel

        // Commands List (Source)
        sourceRecyclerView = binding.sourceListView

        // Draggable List
        dragListView = binding.targetListView

        mRefreshLayout = binding.swipeRefreshLayout
    }

    private fun setupSourceList() {
        // Create your command list. These commands will be moved to the target list upon drop.
        val commandItems = arrayListOf(
            Pair(0L, "SR"),
            Pair(1L, "Dehaze"),
            Pair(2L, "Upscale")
        )

        sourceAdapter = SourceListAdapter(commandItems)
        sourceRecyclerView.layoutManager = LinearLayoutManager(this)
        sourceRecyclerView.adapter = sourceAdapter
    }

    private fun setupDragListView() {
        // Get the current processing order from ParameterConfig.
        // If none is stored, default to a known order.
        val storedOrder = ParameterConfig.getProcessingOrder().toMutableList()
        if (storedOrder.isEmpty()) {
            storedOrder.addAll(listOf("SR", "Dehaze", "Upscale"))
        } else {
            // Ensure "Upscale" is present and always at the end.
            if (!storedOrder.contains("Upscale")) {
                storedOrder.add("Upscale")
            } else {
                storedOrder.remove("Upscale")
                storedOrder.add("Upscale")
            }
        }

        Log.d("SettingsActivity", "Stored order: $storedOrder")

        // Build an ArrayList of Pair<Long, String> items based on the stored order.
        val items = storedOrder.mapIndexed { index, title ->
            Pair(index.toLong(), title)
        }.toCollection(arrayListOf())

        // Initialize the adapter with the items from the stored order.
        val myAdapter = MyItemAdapter(
            itemList = items,
            layoutId = R.layout.list_item,  // Your list item layout
            grabHandleId = R.id.item_layout, // Use the root layout so dragging can start anywhere
            dragOnLongPress = true
        )
        adapter = myAdapter

        // Set up DragListView.
        dragListView.setLayoutManager(LinearLayoutManager(this))
        dragListView.setAdapter(myAdapter, true)
        dragListView.recyclerView.isVerticalScrollBarEnabled = true
        dragListView.setCanDragHorizontally(false)
        dragListView.setCanDragVertically(true)

        dragListView.setDragListListener(object : DragListView.DragListListenerAdapter() {
            override fun onItemDragStarted(position: Int) {
                mRefreshLayout.setEnabled(false)
            }

            override fun onItemDragEnded(fromPosition: Int, toPosition: Int) {
                mRefreshLayout.setEnabled(true)
                // When dragging ends, enforce that "Upscale" is always at the bottom.
                val currentList = myAdapter.itemList
                val upscaleItem = currentList.find { it.second.equals("Upscale", ignoreCase = true) }
                if (upscaleItem != null) {
                    currentList.removeAll { it.second.equals("Upscale", ignoreCase = true) }
                    currentList.add(upscaleItem)
                    myAdapter.notifyDataSetChanged()
                }
                // Update processing order using ParameterConfig.
                updateProcessingOrder(currentList)
            }
        })

        mRefreshLayout.setScrollingView(dragListView.recyclerView)
        mRefreshLayout.setColorSchemeColors(ContextCompat.getColor(baseContext, R.color.teal_200))
        mRefreshLayout.setOnRefreshListener {
            mRefreshLayout.postDelayed({
                mRefreshLayout.isRefreshing = false
            }, 2000)
        }

        dragListView.setSwipeListener (object : OnSwipeListenerAdapter() {
            override fun onItemSwipeStarted(item: ListSwipeItem) {
                mRefreshLayout.setEnabled(false)
            }

            override fun onItemSwipeEnded(item: ListSwipeItem, swipedDirection: SwipeDirection) {
                mRefreshLayout.setEnabled(true)

                // Swipe to delete on left
                if (swipedDirection == SwipeDirection.LEFT) {
                    val adapterItem = item.tag as Pair<*, *>
                    val pos: Int = dragListView.getAdapter().getPositionForItem(adapterItem)
                    dragListView.getAdapter().removeItem(pos)
                }
            }
        })

        // Add an onDragListener to the underlying RecyclerView to handle drops from the source list.
        dragListView.recyclerView.setOnDragListener { view, event ->
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
                    // Retrieve the dragged item from the source list.
                    val droppedItem = event.localState as? Pair<Long, String>
                    if (droppedItem != null) {
                        myAdapter.itemList.add(droppedItem)
                        myAdapter.notifyDataSetChanged()
                        updateProcessingOrder(myAdapter.itemList)
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

    private fun updateProcessingOrder(newOrder: List<Pair<Long, String>>) {
        // Extract only the algorithm names and update ParameterConfig.
        val orderTitles = newOrder.map { it.second }
        ParameterConfig.setProcessingOrder(orderTitles)
    }

}
