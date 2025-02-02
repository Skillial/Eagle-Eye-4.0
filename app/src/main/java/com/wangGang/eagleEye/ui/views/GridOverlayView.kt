package com.wangGang.eagleEye.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GridOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint().apply {
        color = 0x80FFFFFF.toInt()  // Semi-transparent white
        strokeWidth = 3f  // Adjust line thickness as needed
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cellWidth = width / 3f
        val cellHeight = height / 3f

        // Draw vertical lines
        for (i in 1 until 3) {
            val x = cellWidth * i
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
        }

        // Draw horizontal lines
        for (i in 1 until 3) {
            val y = cellHeight * i
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
        }
    }
}
