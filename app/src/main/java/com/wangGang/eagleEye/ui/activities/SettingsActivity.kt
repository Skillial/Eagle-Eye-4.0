package com.wangGang.eagleEye.ui.activities

import DragManageAdapter
import android.os.Bundle
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.databinding.ActivitySettingsBinding
import com.wangGang.eagleEye.ui.adapters.DraggableListAdapter

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
    private lateinit var adapter: DraggableListAdapter

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
        setupDraggableList()
    }

    override fun onStop() {
        super.onStop()
        saveNewAlgoOrder()
    }

    private fun saveNewAlgoOrder() {
        val newOrder = adapter.getCurrentList()
        ParameterConfig.setProcessingOrder(newOrder)
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

    private fun setupDraggableList() {
        val currentOrder = ParameterConfig.getProcessingOrder().toMutableList()
        adapter = DraggableListAdapter(currentOrder)

        algoRecyclerView.layoutManager = LinearLayoutManager(this)
        algoRecyclerView.adapter = adapter

        // Attach ItemTouchHelper for drag
        val callback = DragManageAdapter(adapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(algoRecyclerView)
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

        // Draggable List
        algoRecyclerView = binding.draggableAlgoList
    }
}
