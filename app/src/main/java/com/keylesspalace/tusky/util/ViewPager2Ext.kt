package com.keylesspalace.tusky.util

import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.keylesspalace.tusky.BuildConfig
import java.lang.reflect.Field
import kotlin.reflect.KClass

private const val SLOP_REDUCE_FACTOR = 4

/**
 * View Pager does not provide an API to manage the scroll sensibility despite possible…
 * Through reflection we can manage to tune it and improve our UX.
 */
fun ViewPager2.reduceHorizontalScrollSensibility() {
    try {
        // get the reflection fields
        val recyclerField: Field = ViewPager2::class["mRecyclerView"].accessible()
        val slopField: Field = RecyclerView::class["mTouchSlop"].accessible()

        // get the recycler property (1st layer) and the slop property (2nd layer)
        val recycler = recyclerField.get(this) as? RecyclerView ?: return
        val slop = slopField.get(recycler) as? Int? ?: return

        // changing the slope by a scale multiplier is the best way to prevent different
        // behaviour between different screen resolutions
        slopField.set(recycler, slop * SLOP_REDUCE_FACTOR)
    } catch (ignored: Throwable) {
        if (BuildConfig.DEBUG) {
            ignored.printStackTrace()
        }
    }
}

private fun Field.accessible(): Field {
    this.isAccessible = true
    return this
}

private inline operator fun <reified T : Any> KClass<T>.get(name: String): Field {
    return T::class.java.getDeclaredField(name)
}
