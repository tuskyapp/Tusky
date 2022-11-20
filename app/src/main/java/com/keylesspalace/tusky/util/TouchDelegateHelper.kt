@file:JvmName("TouchDelegateHelper")

package com.keylesspalace.tusky.util

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import at.connyduck.sparkbutton.helpers.Utils

/**
 * Ensures the given children of the view will have a touch area of at least the given size, using
 * a [TouchDelegate].
 */
@JvmOverloads
fun ViewGroup.ensureMinTouchSize(childIds: List<Int>, minSize: Int = Utils.dpToPx(context, 48)) {
    doOnLayout {
        touchDelegate = CompositeTouchDelegate(
            this,
            childIds.map { childId ->
                val view = findViewById<View>(childId)
                val width = view.width
                val height = view.height

                val rect = Rect()
                view.getHitRect(rect)

                val extraWidth = minSize - width
                if (extraWidth > 0) {
                    rect.left -= extraWidth / 2
                    rect.right += extraWidth / 2
                }

                val extraHeight = minSize - height
                if (extraHeight > 0) {
                    rect.top -= extraHeight / 2
                    rect.bottom += extraHeight / 2
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
