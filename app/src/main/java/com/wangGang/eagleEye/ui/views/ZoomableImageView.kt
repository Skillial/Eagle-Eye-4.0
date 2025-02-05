package com.wangGang.eagleEye.ui.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.min

class ZoomableImageView(context: Context, attr: AttributeSet?) : AppCompatImageView(context, attr) {

    private var imageMatrix: Matrix = Matrix()
    private var mode: Int = NONE

    private var last: PointF = PointF()
    private var start: PointF = PointF()
    private var minScale: Float = 1f
    private var maxScale: Float = 4f
    private var m: FloatArray

    private var redundantXSpace: Float = 0f
    private var redundantYSpace: Float = 0f
    private var viewWidth: Float = 0f
    private var viewHeight: Float = 0f
    private var saveScale: Float = 1f
    private var right: Float = 0f
    private var bottom: Float = 0f
    private var origWidth: Float = 0f
    private var origHeight: Float = 0f
    private var bmWidth: Float = 0f
    private var bmHeight: Float = 0f

    private var mScaleDetector: ScaleGestureDetector

    init {
        super.setClickable(true)
        mScaleDetector = ScaleGestureDetector(context, ScaleListener())
        imageMatrix.setTranslate(1f, 1f)
        m = FloatArray(9)
        setImageMatrix(imageMatrix)
        scaleType = ScaleType.MATRIX

        setOnTouchListener { _, event ->
            mScaleDetector.onTouchEvent(event)
            imageMatrix.getValues(m)
            val x = m[Matrix.MTRANS_X]
            val y = m[Matrix.MTRANS_Y]
            val curr = PointF(event.x, event.y)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    last[event.x] = event.y
                    start.set(last)
                    mode = DRAG
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    last[event.x] = event.y
                    start.set(last)
                    mode = ZOOM
                }

                MotionEvent.ACTION_MOVE -> if (mode == ZOOM || (mode == DRAG && saveScale > minScale)) {
                    var deltaX = curr.x - last.x
                    var deltaY = curr.y - last.y
                    val scaleWidth = Math.round(origWidth * saveScale).toFloat()
                    val scaleHeight = Math.round(origHeight * saveScale).toFloat()
                    if (scaleWidth < viewWidth) {
                        deltaX = 0f
                        if (y + deltaY > 0) deltaY = -y
                        else if (y + deltaY < -bottom) deltaY = -(y + bottom)
                    } else if (scaleHeight < viewHeight) {
                        deltaY = 0f
                        if (x + deltaX > 0) deltaX = -x
                        else if (x + deltaX < -right) deltaX = -(x + right)
                    } else {
                        if (x + deltaX > 0) deltaX = -x
                        else if (x + deltaX < -right) deltaX = -(x + right)

                        if (y + deltaY > 0) deltaY = -y
                        else if (y + deltaY < -bottom) deltaY = -(y + bottom)
                    }
                    imageMatrix.postTranslate(deltaX, deltaY)
                    last[curr.x] = curr.y
                }

                MotionEvent.ACTION_UP -> {
                    mode = NONE
                    val xDiff = abs((curr.x - start.x).toDouble()).toInt()
                    val yDiff = abs((curr.y - start.y).toDouble()).toInt()
                    if (xDiff < CLICK && yDiff < CLICK) performClick()
                }

                MotionEvent.ACTION_POINTER_UP -> mode = NONE
            }
            setImageMatrix(imageMatrix)
            invalidate()
            true
        }
    }

    override fun setImageBitmap(bm: Bitmap) {
        super.setImageBitmap(bm)
        bmWidth = bm.width.toFloat()
        bmHeight = bm.height.toFloat()
    }

    fun setMaxZoom(x: Float) {
        maxScale = x
    }

    private inner class ScaleListener : SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mode = ZOOM
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var mScaleFactor = detector.scaleFactor
            val origScale = saveScale
            saveScale *= mScaleFactor
            if (saveScale > maxScale) {
                saveScale = maxScale
                mScaleFactor = maxScale / origScale
            } else if (saveScale < minScale) {
                saveScale = minScale
                mScaleFactor = minScale / origScale
            }
            right = viewWidth * saveScale - viewWidth - (2 * redundantXSpace * saveScale)
            bottom = viewHeight * saveScale - viewHeight - (2 * redundantYSpace * saveScale)
            if (origWidth * saveScale <= viewWidth || origHeight * saveScale <= viewHeight) {
                imageMatrix.postScale(mScaleFactor, mScaleFactor, viewWidth / 2, viewHeight / 2)
                if (mScaleFactor < 1) {
                    imageMatrix.getValues(m)
                    val x = m[Matrix.MTRANS_X]
                    val y = m[Matrix.MTRANS_Y]
                    if (mScaleFactor < 1) {
                        if (Math.round(origWidth * saveScale) < viewWidth) {
                            if (y < -bottom) imageMatrix.postTranslate(0f, -(y + bottom))
                            else if (y > 0) imageMatrix.postTranslate(0f, -y)
                        } else {
                            if (x < -right) imageMatrix.postTranslate(-(x + right), 0f)
                            else if (x > 0) imageMatrix.postTranslate(-x, 0f)
                        }
                    }
                }
            } else {
                imageMatrix.postScale(mScaleFactor, mScaleFactor, detector.focusX, detector.focusY)
                imageMatrix.getValues(m)
                val x = m[Matrix.MTRANS_X]
                val y = m[Matrix.MTRANS_Y]
                if (mScaleFactor < 1) {
                    if (x < -right) imageMatrix.postTranslate(-(x + right), 0f)
                    else if (x > 0) imageMatrix.postTranslate(-x, 0f)
                    if (y < -bottom) imageMatrix.postTranslate(0f, -(y + bottom))
                    else if (y > 0) imageMatrix.postTranslate(0f, -y)
                }
            }
            return true
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        viewWidth = MeasureSpec.getSize(widthMeasureSpec).toFloat()
        viewHeight = MeasureSpec.getSize(heightMeasureSpec).toFloat()
        val scale: Float
        val scaleX = viewWidth / bmWidth
        val scaleY = viewHeight / bmHeight
        scale = min(scaleX.toDouble(), scaleY.toDouble()).toFloat()
        imageMatrix.setScale(scale, scale)
        setImageMatrix(imageMatrix)
        saveScale = 1f

        redundantYSpace = viewHeight - (scale * bmHeight)
        redundantXSpace = viewWidth - (scale * bmWidth)
        redundantYSpace /= 2f
        redundantXSpace /= 2f

        imageMatrix.postTranslate(redundantXSpace, redundantYSpace)

        origWidth = viewWidth - 2 * redundantXSpace
        origHeight = viewHeight - 2 * redundantYSpace
        right = viewWidth * saveScale - viewWidth - (2 * redundantXSpace * saveScale)
        bottom = viewHeight * saveScale - viewHeight - (2 * redundantYSpace * saveScale)
        setImageMatrix(imageMatrix)
    }

    companion object {
        const val NONE: Int = 0
        const val DRAG: Int = 1
        const val ZOOM: Int = 2
        const val CLICK: Int = 3
    }
}