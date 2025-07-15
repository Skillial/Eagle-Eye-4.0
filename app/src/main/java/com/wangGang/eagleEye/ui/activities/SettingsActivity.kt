package com.wangGang.eagleEye.ui.activities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.TooltipCompat
import android.widget.ImageView
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Spinner
import android.hardware.camera2.CaptureRequest
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wangGang.eagleEye.camera.CameraController
import com.wangGang.eagleEye.R
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.databinding.ActivitySettingsBinding
import com.wangGang.eagleEye.processing.commands.ProcessingCommand
import com.wangGang.eagleEye.ui.adapters.CommandListAdapter
import com.wangGang.eagleEye.ui.adapters.ProcessingOrderListAdapter
import com.woxthebox.draglistview.DragListView

class SettingsActivity : AppCompatActivity() {

    companion object {
        private val TAG = "SettingsActivity"

        private lateinit var commandItems: ArrayList<Pair<Long, String>>

        private val SCALING_FACTORS = listOf(1, 2, 4, 8, 16)
    }

    // Views
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var backButton: ImageButton

    /* === Switches === */
    private lateinit var gridOverlaySwitch: SwitchCompat
    private lateinit var flashSwitch: SwitchCompat
    private lateinit var hdrSwitch: SwitchCompat
    private lateinit var infoHdr: ImageView
    private lateinit var hdrLabel: TextView

    /* === SeekBar === */
    private lateinit var scaleSeekBar: SeekBar
    private lateinit var scalingLabel: TextView
    private lateinit var timerSeekBar: SeekBar
    private lateinit var timerLabel: TextView
    private lateinit var whiteBalanceSpinner: Spinner
    private lateinit var whiteBalanceLabel: TextView
    private lateinit var exposureSeekBar: SeekBar
    private lateinit var exposureLabel: TextView

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

        commandItems = ArrayList(
            ProcessingCommand.entries.mapIndexed { index, algorithm ->
                index.toLong() to algorithm.displayName
            }
        )

        assignViews()
        setupSwitchButtons()
        setupFlashSwitch()
        setupHdrSwitch()
        setupScaleSeekBar()
        setupTimerSeekBar()
        setupWhiteBalanceSpinner()
        setupExposureSeekBar()
        setupBackButton()
        setupCommandListRecyclerView()
        setupProcessingOrderListView()

        binding.btnRevertToDefault.setOnClickListener {
            revertToDefault()
        }
    }

    private fun assignViews() {
        backButton = binding.btnBack
        gridOverlaySwitch = binding.switchGridOverlay
        flashSwitch = binding.switchFlash
        hdrSwitch = binding.switchHdr
        infoHdr = binding.infoHdr
        hdrLabel = binding.hdrLabel
        scaleSeekBar = binding.scaleSeekbar
        scalingLabel = binding.scalingLabel
        timerSeekBar = binding.timerSeekbar
        timerLabel = binding.timerLabel
        whiteBalanceSpinner = binding.whiteBalanceSpinner
        whiteBalanceLabel = binding.whiteBalanceLabel
        exposureSeekBar = binding.exposureSeekbar
        exposureLabel = binding.exposureLabel
        commandListRecyclerView = binding.sourceListView
        processingOrderDragListView = binding.targetListView
    }

    private fun setupBackButton() {
        backButton.setOnClickListener { finish() }
    }

    private fun setupSwitchButtons() {
        gridOverlaySwitch.isChecked = ParameterConfig.isGridOverlayEnabled()

        gridOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            ParameterConfig.setGridOverlayEnabled(isChecked)
        }
    }

    private fun setupFlashSwitch() {
        flashSwitch.isChecked = ParameterConfig.isFlashEnabled()

        flashSwitch.setOnCheckedChangeListener { _, isChecked ->
            ParameterConfig.setFlashEnabled(isChecked)
        }
    }

    private fun setupHdrSwitch() {
        val cameraController = CameraController.getInstance()
        val hdrNotSupportedMessage = "HDR not supported on this device"

        if (!cameraController.supportsHdr()) {
            hdrSwitch.isEnabled = false
            hdrSwitch.isChecked = false
            ParameterConfig.setHdrEnabled(false)
            infoHdr.visibility = View.VISIBLE
            TooltipCompat.setTooltipText(infoHdr, hdrNotSupportedMessage)
            infoHdr.setOnClickListener {
                Toast.makeText(this, hdrNotSupportedMessage, Toast.LENGTH_LONG).show()
            }
        } else {
            hdrSwitch.isChecked = ParameterConfig.isHdrEnabled()

            hdrSwitch.setOnCheckedChangeListener { _, isChecked ->
                ParameterConfig.setHdrEnabled(isChecked)
            }
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
                if (ParameterConfig.isScalingFactorGreaterThanOrEqual8()) {
                    enforceUpscaleAtBottom(processingOrderListAdapter)
                    updateProcessingOrder(processingOrderListAdapter.itemList)
                }
                processingOrderListAdapter.notifyDataSetChanged()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

    }

    private fun setupTimerSeekBar() {
        val timerValues = listOf(0, 3, 5, 10) // 0s, 3s, 5s, 10s
        val currentTimer = ParameterConfig.getTimerDuration()
        val currentIndex = timerValues.indexOf(currentTimer).coerceAtLeast(0)
        timerSeekBar.progress = currentIndex
        timerLabel.text = "Timer: ${currentTimer}s"

        timerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val chosenDuration = timerValues[progress]
                ParameterConfig.setTimerDuration(chosenDuration)
                timerLabel.text = "Timer: ${chosenDuration}s"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    private fun setupWhiteBalanceSpinner() {
        val cameraController = CameraController.getInstance()
        val supportedModes = cameraController.getSupportedAwbModes()
        Log.d(TAG, "Supported AWB Modes (raw): ${supportedModes.joinToString()}")

        val modeNames = supportedModes.map { mode ->
            when (mode) {
                CaptureRequest.CONTROL_AWB_MODE_AUTO -> "Auto"
                CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "Cloudy Daylight"
                CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT -> "Daylight"
                CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT -> "Fluorescent"
                CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT -> "Incandescent"
                CaptureRequest.CONTROL_AWB_MODE_SHADE -> "Shade"
                CaptureRequest.CONTROL_AWB_MODE_TWILIGHT -> "Twilight"
                CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "Warm Fluorescent"
                CaptureRequest.CONTROL_AWB_MODE_OFF -> "Off"
                else -> "Unknown"
            }
        }

        Log.d(TAG, "$supportedModes")
        Log.d(TAG, "$modeNames")


        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modeNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        whiteBalanceSpinner.adapter = adapter

        val currentMode = ParameterConfig.getWhiteBalanceMode()
        Log.d(TAG, "Current white balance mode from ParameterConfig: $currentMode")
        val currentModeIndex = supportedModes.indexOf(currentMode)
        Log.d(TAG, "Index of current white balance mode: $currentModeIndex")
        if (currentModeIndex != -1) {
            whiteBalanceSpinner.setSelection(currentModeIndex)
        }

        whiteBalanceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMode = supportedModes[position]
                ParameterConfig.setWhiteBalanceMode(selectedMode)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun setupExposureSeekBar() {
        val cameraController = CameraController.getInstance()
        val characteristics = cameraController.getCameraCharacteristics()
        val exposureRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val exposureStep = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)

        if (exposureRange == null || exposureStep == null) {
            exposureSeekBar.isEnabled = false
            exposureLabel.text = "Exposure: Not Supported"
            return
        }

        val minExposure = exposureRange.lower
        val maxExposure = exposureRange.upper
        val stepValue = exposureStep.toFloat()

        val maxProgress = ((maxExposure - minExposure) / stepValue).toInt()
        exposureSeekBar.max = maxProgress

        val currentExposure = ParameterConfig.getExposureCompensation()
        val initialProgress = ((currentExposure - minExposure) / stepValue).toInt()
        exposureSeekBar.progress = initialProgress
        exposureLabel.text = "Exposure: %.1f EV".format(currentExposure)

        exposureSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val selectedExposure = minExposure + (progress * stepValue)
                ParameterConfig.setExposureCompensation(selectedExposure)
                exposureLabel.text = "Exposure: %.1f EV".format(selectedExposure)
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
        if (items[0].second == "") {
            items.removeAt(0)
        }

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
        commandListAdapter = CommandListAdapter(commandItems)
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
                if (ParameterConfig.isScalingFactorGreaterThanOrEqual8()) {
                    val currentList = processingOrderListAdapter.itemList
                    val upscaleIndex = currentList.indexOfFirst { it.second.equals("Upscale", ignoreCase = true) }

                    // If the drop position is below (i.e. a higher index than) the "Upscale" item,
                    // show a toast and enforce that "Upscale" remains at the bottom.
                    if (upscaleIndex != -1 && toPosition > upscaleIndex) {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Cannot drop item below 'Upscale' when scaling factor ≥ 8",
                            Toast.LENGTH_SHORT
                        ).show()
                        enforceUpscaleAtBottom(processingOrderListAdapter)
                        processingOrderListAdapter.notifyDataSetChanged()
                    }
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
                    if (droppedItem != null) {
                        if (adapter.itemList.any { it.second.equals(droppedItem.second, ignoreCase = true) }) {
                            // Show a toast if duplicate.
                            Toast.makeText(this@SettingsActivity, "Duplicate command: ${droppedItem.second}", Toast.LENGTH_SHORT).show()
                        } else {
                            adapter.itemList.add(droppedItem)
                            if (ParameterConfig.isScalingFactorGreaterThanOrEqual8()) {
                                enforceUpscaleAtBottom(adapter)
                            }
                            updateProcessingOrder(adapter.itemList)
                            adapter.notifyDataSetChanged()
                        }
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
//        if (order.first() == "") {
//            // Remove empty string
//            order.removeAt(0)
//            order.addAll(DEFAULT_PROCESSING_ORDER)
//        }
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

    private fun revertToDefault() {
        ParameterConfig.revertToDefault()

        // Reload the settings to reflect the default values
        setupSwitchButtons()
        setupFlashSwitch()
        setupHdrSwitch()
        setupScaleSeekBar()
        setupTimerSeekBar()
        setupWhiteBalanceSpinner()
        setupExposureSeekBar()
        setupProcessingOrderListView()
    }

}
