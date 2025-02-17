/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.util

import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.keylesspalace.tusky.R

fun View.show() {
    this.visibility = View.VISIBLE
}

fun View.hide() {
    this.visibility = View.GONE
}

fun View.visible(visible: Boolean, or: Int = View.GONE) {
    this.visibility = if (visible) View.VISIBLE else or
}

/**
 * Reduce ViewPager2's sensitivity to horizontal swipes.
 */
fun ViewPager2.reduceSwipeSensitivity() {
    // ViewPager2 is very sensitive to horizontal motion when swiping vertically, and will
    // trigger a page transition if the user's swipe is only a few tens of degrees off from
    // vertical. This is a problem if the underlying content is a list that the user wants
    // to scroll vertically -- it's far too easy to trigger an accidental horizontal swipe.
    //
    // One way to stop this is to reach in to ViewPager2's RecyclerView and adjust the amount
    // of touch slop it has.
    //
    // See https://issuetracker.google.com/issues/139867645 and
    // https://bladecoder.medium.com/fixing-recyclerview-nested-scrolling-in-opposite-direction-f587be5c1a04
    // for more (the approach in that Medium article works, but is still quite sensitive to
    // horizontal movement while scrolling).
    try {
        val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView

        val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        // Experimentally, 2 seems to be a sweet-spot, requiring a downward swipe that's at least
        // 45 degrees off the vertical to trigger a change. This is consistent with maximum angle
        // supported to open the nav. drawer.
        val scaleFactor = 2
        touchSlopField.set(recyclerView, touchSlop * scaleFactor)
    } catch (e: Exception) {
        Log.w("reduceSwipeSensitivity", e)
    }
}

/**
 * TextViews with an ancestor RecyclerView can forget that they are selectable. Toggling
 * calls to [TextView.setTextIsSelectable] fixes this.
 *
 * @see https://issuetracker.google.com/issues/37095917
 */
fun TextView.fixTextSelection() {
    setTextIsSelectable(false)
    post { setTextIsSelectable(true) }
}

/**
 * Makes sure the [RecyclerView] has the correct bottom padding set
 * and no system bars or floating action buttons cover the content when it is scrolled all the way up.
 * This must be called before window insets are applied (Activity.onCreate or Fragment.onViewCreated).
 * The RecyclerView needs to have clipToPadding set to false for this to work.
 * @param fab true if there is a [FloatingActionButton] above the RecyclerView
 */
fun RecyclerView.ensureBottomPadding(fab: Boolean = false) {
    val bottomPadding = if (fab) {
        context.resources.getDimensionPixelSize(R.dimen.recyclerview_bottom_padding_actionbutton)
    } else {
        context.resources.getDimensionPixelSize(R.dimen.recyclerview_bottom_padding_no_actionbutton)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val systemBarsInsets = insets.getInsets(systemBars())
            view.updatePadding(bottom = bottomPadding + systemBarsInsets.bottom)
            WindowInsetsCompat.Builder(insets)
                .setInsets(systemBars(), Insets.of(systemBarsInsets.left, systemBarsInsets.top, systemBarsInsets.right, 0))
                .build()
        }
    } else {
        updatePadding(bottom = bottomPadding)
    }
}

/** Makes sure a [FloatingActionButton] has the correct bottom margin set
 * so it is not covered by any system bars.
 */
fun FloatingActionButton.ensureBottomMargin() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bottomInsets = insets.getInsets(systemBars()).bottom
        val actionButtonMargin = resources.getDimensionPixelSize(R.dimen.fabMargin)
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = bottomInsets + actionButtonMargin
        }
        insets
    }
}

/**
 * Combines WindowInsetsAnimationCompat.Callback and OnApplyWindowInsetsListener
 * for easy implementation of layouts that animate with they keyboard.
 * The animation callback is only called when something animates, so it isn't suitable for initial setup.
 * The OnApplyWindowInsetsListener can do that, but the insets it supplies must not be used when an animation is ongoing,
 * as that messes with the animation.
 */
fun View.setOnWindowInsetsChangeListener(listener: (WindowInsetsCompat) -> Unit) {
    val callback = WindowInsetsCallback(listener)

    ViewCompat.setWindowInsetsAnimationCallback(this, callback)
    ViewCompat.setOnApplyWindowInsetsListener(this, callback)
}

private class WindowInsetsCallback(
    private val listener: (WindowInsetsCompat) -> Unit,
) : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP),
    OnApplyWindowInsetsListener {

    var imeVisible = false
    var deferredInsets: WindowInsetsCompat? = null

    override fun onStart(animation: WindowInsetsAnimationCompat, bounds: WindowInsetsAnimationCompat.BoundsCompat): WindowInsetsAnimationCompat.BoundsCompat {
        imeVisible = true
        return super.onStart(animation, bounds)
    }

    override fun onProgress(
        insets: WindowInsetsCompat,
        runningAnimations: List<WindowInsetsAnimationCompat>,
    ): WindowInsetsCompat {
        listener(insets)
        return WindowInsetsCompat.CONSUMED
    }

    override fun onApplyWindowInsets(
        view: View,
        insets: WindowInsetsCompat,
    ): WindowInsetsCompat {
        val ime = insets.getInsets(ime()).bottom
        if (!imeVisible && ime == 0) {
            listener(insets)
            deferredInsets = null
        } else {
            deferredInsets = insets
        }
        return WindowInsetsCompat.CONSUMED
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
        imeVisible = deferredInsets?.isVisible(ime()) == true
        deferredInsets?.let { insets ->
            listener(insets)
            deferredInsets = null
        }
    }
}
