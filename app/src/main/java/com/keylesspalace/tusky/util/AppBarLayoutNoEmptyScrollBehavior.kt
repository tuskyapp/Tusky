/* Copyright 2019 Joel Pyska
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
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.util

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import com.google.android.material.appbar.AppBarLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView


/**
 * Disable AppBar scroll if content view empty or don't need to scroll
 */
class AppBarLayoutNoEmptyScrollBehavior : AppBarLayout.Behavior {

    constructor() : super()

    constructor (context: Context, attrs: AttributeSet) : super(context, attrs)

    private fun isRecyclerViewScrollable(appBar: AppBarLayout, recyclerView: RecyclerView?): Boolean {
        if (recyclerView == null)
            return false
        var recyclerViewHeight = recyclerView.height // Height includes RecyclerView plus AppBarLayout at same level
        val appCompatHeight = appBar.height
        recyclerViewHeight -= appCompatHeight

        return recyclerView.computeVerticalScrollRange() > recyclerViewHeight
    }

    override fun onStartNestedScroll(parent: CoordinatorLayout, child: AppBarLayout, directTargetChild: View, target: View, nestedScrollAxes: Int, type: Int): Boolean {
        return if (isRecyclerViewScrollable(child, getRecyclerView(parent))) {
            super.onStartNestedScroll(parent, child, directTargetChild, target, nestedScrollAxes, type)
        } else false
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: AppBarLayout, ev: MotionEvent): Boolean {
        //Prevent scroll on app bar drag
        return if (child.isShown && !isRecyclerViewScrollable(child, getRecyclerView(parent)))
            true
        else
            super.onTouchEvent(parent, child, ev)
    }

    private fun getRecyclerView(parent: ViewGroup): RecyclerView? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is RecyclerView)
                return child
            else if (child is ViewGroup) {
                val childRecyclerView = getRecyclerView(child)
                if (childRecyclerView is RecyclerView)
                    return childRecyclerView
            }
        }
        return null
    }
}