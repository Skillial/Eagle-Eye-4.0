package com.wangGang.eagleEye.ui.views

import android.content.Context
import androidx.core.view.ViewCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.util.AttributeSet
import android.view.View

class MySwipeRefreshLayout: SwipeRefreshLayout {
    private var mScrollingView: View? = null

    constructor(context: Context): super(context)

    constructor(context: Context, attrs: AttributeSet): super(context, attrs)

    override fun canChildScrollUp(): Boolean {
        if (mScrollingView != null) {
            return ViewCompat.canScrollVertically(mScrollingView, -1)
        }

        return super.canChildScrollUp()
    }

    fun setScrollingView(scrollingView: View) {
        mScrollingView = scrollingView
    }
}