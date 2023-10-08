package com.keylesspalace.tusky.util

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import com.google.android.material.tabs.TabLayout

/**
 * Switches the tab display mode depending on available size: start out with "scrollable" but
 * if there is enough room switch to "fixed" (an re-measure).
 *
 * Idea taken from https://stackoverflow.com/a/44894143
 */
class AdaptiveTabLayout : TabLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        tabMode = MODE_SCROLLABLE // make sure to only measure the "minimum width"
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (tabCount < 2) {
            return
        }

        try {
            val tabLayout = getChildAt(0) as ViewGroup
            var widthOfAllTabs = 0
            for (i in 0 until tabLayout.childCount) {
                widthOfAllTabs += tabLayout.getChildAt(i).measuredWidth
            }
            if (widthOfAllTabs <= measuredWidth) {
                // fill all space if there is enough room
                tabMode = MODE_FIXED
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
