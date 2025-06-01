package com.wangGang.eagleEye.processing.commands

import androidx.compose.ui.graphics.Color

sealed class ProcessingCommand(val displayName: String, val tasks: List<String>, color: Color) {
    // Function to return the size of the tasks list.
    fun calculate(): Int = tasks.size

    companion object {
        // List of all available commands.
        val entries: List<ProcessingCommand> by lazy {
            listOf(SuperResolution, Dehaze, Upscale, ShadowRemoval)
        }

        // Factory function to retrieve a ProcessingCommand based on its displayName.
        fun fromDisplayName(name: String): ProcessingCommand? {
            return when (name) {
                SuperResolution.displayName -> SuperResolution
                Dehaze.displayName -> Dehaze
                Upscale.displayName -> Upscale
                ShadowRemoval.displayName -> ShadowRemoval
                else -> null
            }
        }
    }
}

// Each command is represented as an object (singleton) inheriting from ProcessingCommand.
data object SuperResolution : ProcessingCommand(
    displayName = "Super Resolution",
    tasks = listOf(
        "Reading Energy",
        "Applying Filter",
        "Performing Sharpness Measure",
        "Performing Unsharp Masking",
        "Preprocessing Images",
        "Performing alignment/warping",
        "Assessing Image Warp Results",
        "Performing Mean Fusion"
    ),
    color = Color.Green
)

data object Dehaze : ProcessingCommand(
    displayName = "Dehaze",
    tasks = listOf(
        "Loading and Resizing Image",
        "Loading Albedo Model",
        "Preprocessing Image",
        "Running Albedo Model",
        "Loading Transmission Model",
        "Running Transmission Model",
        "Resizing Image",
        "Preprocessing Image",
        "Loading Airlight Model",
        "Running Airlight Model",
        "Normalizing Image",
        "Clearing Image",
        "Processing Image",
        "Converting Image"
    ),
    color = Color.Yellow
)

data object Upscale : ProcessingCommand(
    displayName = "Upscale",
    tasks = listOf(
        "Upscaling Images"
    ),
    color = Color.Blue
)

data object ShadowRemoval : ProcessingCommand(
    displayName = "Shadow Removal",
    tasks = listOf(
        "Removing Shadows"
    ),
    color = Color.Gray
)