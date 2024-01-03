/*
 * Copyright 2023 Tusky Contributors
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

package com.keylesspalace.tusky.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_UP
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.doOnLayout
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.R
import java.lang.Float.max
import java.lang.Float.min
import kotlin.math.abs

/**
 * Displays text to the user with optional [ClickableSpan]s. Extends the touchable area of the spans
 * to ensure they meet the minimum size of 48dp x 48dp for accessibility requirements.
 *
 * If the touchable area of multiple spans overlap the touch is dispatched to the closest span.
 */
class ClickableSpanTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {
    /**
     * Map of [RectF] that enclose the [ClickableSpan] without any additional touchable area. A span
     * may extend over more than one line, so multiple entries in this map may point to the same
     * span.
     */
    private val spanRects = mutableMapOf<RectF, ClickableSpan>()

    /**
     * Map of [RectF] that enclose the [ClickableSpan] with the additional touchable area. A span
     * may extend over more than one line, so multiple entries in this map may point to the same
     * span.
     */
    private val delegateRects = mutableMapOf<RectF, ClickableSpan>()

    /**
     * The [ClickableSpan] that is used for the point the user has touched. Null if the user is
     * not tapping, or the point they have touched is not associated with a span.
     */
    private var clickedSpan: ClickableSpan? = null

    /** The minimum size, in pixels, of a touchable area for accessibility purposes */
    private val minDimenPx = resources.getDimensionPixelSize(R.dimen.minimum_touch_target)

    /**
     * Debugging helper. Normally false, set this to true to show a border around spans, and
     * shade their touchable area.
     */
    private val showSpanBoundaries = false

    /**
     * Debugging helper. The paint to use to draw a span.
     */
    private lateinit var spanDebugPaint: Paint

    /**
     * Debugging helper. The paint to use to shade a span's touchable area.
     */
    private lateinit var paddingDebugPaint: Paint

    init {
        // Initialise debugging paints, if appropriate. Only ever present in debug builds, and
        // is optimised out if showSpanBoundaries is false.
        if (BuildConfig.DEBUG && showSpanBoundaries) {
            spanDebugPaint = Paint()
            spanDebugPaint.color = Color.BLACK
            spanDebugPaint.style = Paint.Style.STROKE

            paddingDebugPaint = Paint()
            paddingDebugPaint.color = Color.MAGENTA
            paddingDebugPaint.alpha = 50
        }
    }

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)

        // TextView tries to optimise the layout process, and will not perform a layout if the
        // new text takes the same area as the old text (see TextView.checkForRelayout()). This
        // can result in statuses using the wrong clickable areas as they are never remeasured.
        // (https://github.com/tuskyapp/Tusky/issues/3596). Force a layout pass to ensure that
        // the spans are measured correctly.
        if (!isInLayout) requestLayout()

        doOnLayout { measureSpans() }
    }

    /**
     * Compute [Rect]s for each [ClickableSpan].
     *
     * Each span is associated with at least two Rects. One for the span itself, and one for the
     * touchable area around the span.
     *
     * If the span runs over multiple lines there will be two Rects per line.
     */
    private fun measureSpans() {
        spanRects.clear()
        delegateRects.clear()

        val spannedText = text as? Spanned ?: return

        // The goal is to record all the [Rect]s associated with a span with the same fidelity
        // that the user sees when they highlight text in the view to select it.
        //
        // There's no method in [TextView] or [Layout] that does exactly that. [Layout.getSelection]
        // would be perfect, but it's not accessible. However, [Layout.getSelectionPath] is. That
        // records the Rects between two characters in the string, and handles text that spans
        // multiple lines, is bidirectional, etc.
        //
        // However, it records them in to a [Path], and a Path has no mechanism to extract the
        // Rects saved in to it.
        //
        // So subclass Path with [RectRecordingPath], which records the data from calls to
        // [addRect]. Pass that to `getSelectionPath` to extract all the Rects between start and
        // end.
        val rects = mutableListOf<RectF>()
        val rectRecorder = RectRecordingPath(rects)

        for (span in spannedText.getSpans(0, text.length - 1, ClickableSpan::class.java)) {
            rects.clear()
            val spanStart = spannedText.getSpanStart(span)
            val spanEnd = spannedText.getSpanEnd(span)

            // Collect all the Rects for this span
            layout.getSelectionPath(spanStart, spanEnd, rectRecorder)

            // Save them
            for (rect in rects) {
                // Adjust to account for the view's padding and gravity
                rect.offset(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
                rect.bottom += extendedPaddingBottom

                // The rect wraps just the span, with no additional touchable area. Save a copy.
                spanRects[RectF(rect)] = span

                // Adjust the rect to meet the minimum dimensions
                if (rect.height() < minDimenPx) {
                    val yOffset = (minDimenPx - rect.height()) / 2
                    rect.top = max(0f, rect.top - yOffset)
                    rect.bottom = min(rect.bottom + yOffset, bottom.toFloat())
                }

                if (rect.width() < minDimenPx) {
                    val xOffset = (minDimenPx - rect.width()) / 2
                    rect.left = max(0f, rect.left - xOffset)
                    rect.right = min(rect.right + xOffset, right.toFloat())
                }

                // Save it
                delegateRects[rect] = span
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // workaround to https://code.google.com/p/android/issues/detail?id=191430
        // from https://stackoverflow.com/a/36740247
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
            val startSelection = selectionStart
            val endSelection = selectionEnd

            val content = text
            if (content is Spannable && (startSelection < 0 || endSelection < 0)) {
                Selection.setSelection(content as Spannable?, content.length)
            } else if (startSelection != endSelection) {
                if (event.actionMasked == ACTION_DOWN) {
                    text = null
                    text = content
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    /**
     * Handle some touch events.
     *
     * - [ACTION_DOWN]: Determine which, if any span, has been clicked, and save in clickedSpan
     * - [ACTION_UP]: If a span was saved then dispatch the click to that span
     * - [ACTION_CANCEL]: Clear the saved span
     *
     * Defer to the parent class for other touches.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return super.onTouchEvent(null)
        if (delegateRects.isEmpty()) return super.onTouchEvent(event)

        when (event.action) {
            ACTION_DOWN -> {
                clickedSpan = null
                val x = event.x
                val y = event.y

                // If the user has clicked directly on a span then use it, ignoring any overlap
                for (entry in spanRects) {
                    if (!entry.key.contains(x, y)) continue
                    clickedSpan = entry.value
                    Log.v(TAG, "span click: ${(clickedSpan as URLSpan).url}")
                    return super.onTouchEvent(event)
                }

                // Otherwise, check to see if it's in a touchable area
                var activeEntry: MutableMap.MutableEntry<RectF, ClickableSpan>? = null

                for (entry in delegateRects) {
                    if (entry == activeEntry) continue
                    if (!entry.key.contains(x, y)) continue

                    if (activeEntry == null) {
                        activeEntry = entry
                        continue
                    }
                    Log.v(TAG, "Overlap: ${(entry.value as URLSpan).url} ${(activeEntry.value as URLSpan).url}")
                    if (isClickOnFirst(entry.key, activeEntry.key, x, y)) {
                        activeEntry = entry
                    }
                }
                clickedSpan = activeEntry?.value
                clickedSpan?.let { Log.v(TAG, "padding click: ${(clickedSpan as URLSpan).url}") }
                return super.onTouchEvent(event)
            }
            ACTION_UP -> {
                clickedSpan?.let {
                    clickedSpan = null
                    val duration = event.eventTime - event.downTime
                    if (duration <= ViewConfiguration.getLongPressTimeout()) {
                        it.onClick(this)
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
            ACTION_CANCEL -> {
                clickedSpan = null
                return super.onTouchEvent(event)
            }
            else -> return super.onTouchEvent(event)
        }
    }

    /**
     * Determine whether a click on overlapping rectangles should be attributed to the first or the
     * second rectangle.
     *
     * When the user clicks on the overlap it has to be attributed to the "best" rectangle. The
     * rectangles have equivalent z-order, so their "closeness" to the user in the Z-plane is not
     * a consideration.
     *
     * The chosen rectangle depends on whether they overlap top/bottom (the top of one rect is
     * not the same as the top of the other rect), or they overlap left/right (the tops of both
     * rects are the same).
     *
     * In this example the rectangles overlap top/bottom because their top edges are not aligned.
     *
     * ```
     *     +--------------+
     *     |1             |
     *     |      +--------------+
     *     |      |2             |
     *     |      |              |
     *     |      |              |
     *     +------|              |
     *            |              |
     *            +--------------+
     * ```
     *
     * (Rectangle #1 being partially occluded by rectangle #2 is for clarity in the diagram, it
     * does not affect the algorithm)
     *
     * Take the Y coordinate of the centre of each rectangle.
     *
     * ```
     *     +--------------+
     *     |1             |
     *     |      +--------------+
     *     |......|2             |  <-- Rect #1 centre line
     *     |      |              |
     *     |      |..............|  <-- Rect #2 centre line
     *     +------|              |
     *            |              |
     *            +--------------+
     * ```
     *
     * Take the Y position of the click, and determine which Y centre coordinate it is closest too.
     * Whichever one is closest is the clicked rectangle.
     *
     * In these examples the left column of numbers is the Y coordinate, `*` marks the point where
     * the user clicked.
     *
     * ```
     * 0   +--------------+                  +--------------+
     * 1   |1             |                  |1             |
     * 2   |      +--------------+           |      +--------------+
     * 3   |......|2  *          |           |......|2             |
     * 4   |      |              |           |      |              |
     * 5   |      |..............|           |      |*.............|
     * 6   +------|              |           +------|              |
     * 7          |              |                  |              |
     * 8          +--------------+                  +--------------+
     *
     *     Rect #1 centre Y = 3
     *     Rect #2 centre Y = 5
     *     Click (*) Y      = 3              Click (*) Y      = 5
     *     Result: Rect #1 is clicked        Result: Rect #2 is clicked
     * ```
     *
     * The approach is the same if the rectangles overlap left/right, but the X coordinate of the
     * centre of the rectangle is tested against the X coordinate of the click.
     *
     * @param first rectangle to test against
     * @param second rectangle to test against
     * @param x coordinate of user click
     * @param y coordinate of user click
     * @return true if the click was closer to the first rectangle than the second
     */
    private fun isClickOnFirst(first: RectF, second: RectF, x: Float, y: Float): Boolean {
        Log.v(TAG, "first: $first second: $second click: $x $y")
        val (firstDiff, secondDiff) = if (first.top == second.top) {
            Log.v(TAG, "left/right overlap")
            Pair(abs(first.centerX() - x), abs(second.centerX() - x))
        } else {
            Log.v(TAG, "top/bottom overlap")
            Pair(abs(first.centerY() - y), abs(second.centerY() - y))
        }
        Log.d(TAG, "firstDiff: $firstDiff secondDiff: $secondDiff")
        return firstDiff < secondDiff
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        // Paint span boundaries. Optimised out on release builds, or debug builds where
        // showSpanBoundaries is false.
        if (BuildConfig.DEBUG && showSpanBoundaries) {
            canvas?.save()
            for (entry in delegateRects) {
                canvas?.drawRect(entry.key, paddingDebugPaint)
            }

            for (entry in spanRects) {
                canvas?.drawRect(entry.key, spanDebugPaint)
            }
            canvas?.restore()
        }
    }

    companion object {
        const val TAG = "ClickableSpanTextView"
    }
}

/**
 * A [Path] that records the contents of all the [addRect] calls it receives.
 *
 * @param rects list to record the received [RectF]
 */
private class RectRecordingPath(private val rects: MutableList<RectF>) : Path() {
    override fun addRect(left: Float, top: Float, right: Float, bottom: Float, dir: Direction) {
        rects.add(RectF(left, top, right, bottom))
    }
}
