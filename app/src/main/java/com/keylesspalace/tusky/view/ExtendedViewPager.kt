package com.keylesspalace.tusky.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager


class ExtendedViewPager : ViewPager {
    private var swipeable = true

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, com.keylesspalace.tusky.R.styleable.ExtendedViewPager)
        try {
            swipeable = a.getBoolean(com.keylesspalace.tusky.R.styleable.ExtendedViewPager_swipeable, true)
        } finally {
            a.recycle()
        }
    }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return if (swipeable) super.onInterceptTouchEvent(event) else false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (swipeable) super.onTouchEvent(event) else false
    }
}