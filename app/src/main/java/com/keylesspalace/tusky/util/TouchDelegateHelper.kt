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
        var res = false
        val x = event.x
        val y = event.y
        for (delegate in delegates) {
            event.setLocation(x, y)
            res = delegate.onTouchEvent(event) || res
        }
        return res
    }
}
