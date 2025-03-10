package com.wangGang.eagleEye.ui.views

import android.graphics.Canvas
import android.graphics.Point
import android.view.View

class EnlargedDragShadowBuilder(view: View, private val scaleFactor: Float = 1.5f) : View.DragShadowBuilder(view) {

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        // Calculate scaled dimensions
        val width = view.width
        val height = view.height
        val scaledWidth = (width * scaleFactor).toInt()
        val scaledHeight = (height * scaleFactor).toInt()
        outShadowSize.set(scaledWidth, scaledHeight)
        // Set the touch point to the center of the enlarged shadow.
        outShadowTouchPoint.set(scaledWidth / 2, scaledHeight / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        canvas.save()
        canvas.scale(scaleFactor, scaleFactor)
        view.draw(canvas)
        canvas.restore()
    }
}
