/* Copyright 2022 Tusky contributors

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

@file:JvmName("TouchDelegateHelper")

package com.keylesspalace.tusky.util

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup

/**
 * Expands the touch area of the give row of views to fill the space in-between them, using a
 * [TouchDelegate].
 */
fun ViewGroup.expandTouchSizeToFillRow(children: List<View>) {
    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        touchDelegate = CompositeTouchDelegate(
            this,
            children.mapIndexed { i, view ->
                val rect = Rect()
                view.getHitRect(rect)
                val left = children.getOrNull(i - 1)
                if (left != null) {
                    // extend half-way to previous view
                    rect.left -= (view.left - left.right) / 2
                }
                val right = children.getOrNull(i + 1)
                if (right != null) {
                    // extend half-way to next view
                    rect.right += (right.left - view.right) / 2
                }
                TouchDelegate(rect, view)
            }
        )
    }
}

private class CompositeTouchDelegate(view: View, private val delegates: List<TouchDelegate>) :
    TouchDelegate(Rect(), view) {

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        return delegates.fold(false) { res, delegate ->
            event.setLocation(x, y)
            delegate.onTouchEvent(event) || res
        }
    }
}
