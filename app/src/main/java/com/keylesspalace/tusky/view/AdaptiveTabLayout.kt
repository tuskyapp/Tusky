package com.keylesspalace.tusky.view

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import com.google.android.material.tabs.TabLayout

/**
 * Workaround for "auto" mode not behaving as expected.
 *
 * Switches the tab display mode depending on available size: start out with "scrollable" but
 * if there is enough room switch to "fixed" (and re-measure).
 *
 * Idea taken from https://stackoverflow.com/a/44894143
 */
class AdaptiveTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TabLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        tabMode = MODE_SCROLLABLE // make sure to only measure the "minimum width"
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (tabCount < 2) {
            return
        }

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
    }
}
