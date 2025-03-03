@file:JvmName("RelativeTimeUpdater")

package com.keylesspalace.tusky.util

import android.content.SharedPreferences
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.settings.PrefKeys
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val UPDATE_INTERVAL = 1.minutes

/**
 * Helper method to update adapter periodically to refresh timestamp
 * if setting absoluteTimeView is false.
 * Start updates when the Fragment becomes visible and stop when it is hidden.
 */
fun Fragment.updateRelativeTimePeriodically(preferences: SharedPreferences, adapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>) {
    val lifecycle = viewLifecycleOwner.lifecycle
    lifecycle.coroutineScope.launch {
        // This child coroutine will launch each time the Fragment moves to the STARTED state
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false)
            while (!useAbsoluteTime) {
                adapter.notifyItemRangeChanged(
                    0,
                    adapter.itemCount,
                    StatusBaseViewHolder.Key.KEY_CREATED
                )
                delay(UPDATE_INTERVAL)
            }
        }
    }
}
