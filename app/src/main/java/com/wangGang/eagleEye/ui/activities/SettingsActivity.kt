package com.wangGang.eagleEye.ui.activities

import android.os.Bundle
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wangGang.eagleEye.R
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.databinding.ActivitySettingsBinding
import com.wangGang.eagleEye.ui.adapters.MyItemAdapter
import com.woxthebox.draglistview.DragListView

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
    /* Draggable List */
    private lateinit var algoRecyclerView: RecyclerView
    // private lateinit var adapter: DraggableListAdapter
    private lateinit var adapter: MyItemAdapter
    private lateinit var dragListView: DragListView

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
        // setupDraggableList()
        setupDragListView()
    }

    override fun onStop() {
        super.onStop()
        // saveNewAlgoOrder()
    }

    /*private fun saveNewAlgoOrder() {
        val newOrder = adapter.getCurrentList()
        ParameterConfig.setProcessingOrder(newOrder)
    }*/

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

   /* private fun setupDraggableList() {
        val currentOrder = ParameterConfig.getProcessingOrder().toMutableList()
        adapter = DraggableListAdapter(currentOrder)

        algoRecyclerView.layoutManager = LinearLayoutManager(this)
        algoRecyclerView.adapter = adapter

        // Attach ItemTouchHelper for drag
        val callback = DragManageAdapter(adapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(algoRecyclerView)
    }*/

    private fun assignViews() {
        backButton = binding.btnBack

        // Switches
        superResolutionSwitch = binding.switchSuperResolution
        gridOverlaySwitch = binding.switchGridOverlay
        dehazeSwitch = binding.switchDehaze

        // Scale Factor Setting
        scaleSeekBar = binding.scaleSeekbar
        scalingLabel = binding.scalingLabel

        // Draggable List
        // algoRecyclerView = binding.draggableAlgoList

        dragListView = binding.dragListView
    }

    private fun setupDragListView() {
        // Create a list with three items. "Upscale" is marked with a unique ID and will be non-draggable.
        val items = arrayListOf(
            Pair(0L, "SR"),
            Pair(1L, "Dehaze"),
            Pair(2L, "Upscale")
        )

        // Initialize the adapter.
        val myAdapter = MyItemAdapter(
            itemList = items,
            layoutId = R.layout.list_item,  // your list item layout
            grabHandleId = R.id.image,       // the ID of the drag handle in your layout
            dragOnLongPress = true
        )

        // Save adapter reference if needed globally:
        adapter = myAdapter

        // Set up DragListView using its setter methods (instead of property syntax).
        dragListView.setLayoutManager(LinearLayoutManager(this))
        dragListView.setAdapter(myAdapter, true)
        dragListView.setCanDragHorizontally(false)

        dragListView.setDragListListener(object : DragListView.DragListListenerAdapter() {
            override fun onItemDragEnded(fromPosition: Int, toPosition: Int) {
                // When dragging ends, enforce that "Upscale" is always at the bottom.
                val currentList = myAdapter.itemList  // from the adapter
                val upscaleItem = currentList.find { it.second.equals("Upscale", ignoreCase = true) }
                if (upscaleItem != null) {
                    currentList.removeAll { it.second.equals("Upscale", ignoreCase = true) }
                    currentList.add(upscaleItem)
                    myAdapter.notifyDataSetChanged()
                }
                // Update processing order (e.g., via ParameterConfig)
                updateProcessingOrder(currentList)
            }
        })
    }

    private fun updateProcessingOrder(newOrder: List<Pair<Long, String>>) {
        // Extract just the titles and update ParameterConfig.
        val orderTitles = newOrder.map { it.second }
        ParameterConfig.setProcessingOrder(orderTitles)
    }
}
