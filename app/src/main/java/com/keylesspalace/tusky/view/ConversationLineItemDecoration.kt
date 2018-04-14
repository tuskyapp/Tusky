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
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.support.v7.widget.RecyclerView
import android.view.View

import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.ThreadAdapter

class ConversationLineItemDecoration(private val context: Context, private val divider: Drawable) : RecyclerView.ItemDecoration() {

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State?) {
        val dividerStart = parent.paddingStart + context.resources.getDimensionPixelSize(R.dimen.status_line_margin_start)
        val dividerEnd = dividerStart + divider.intrinsicWidth

        val childCount = parent.childCount
        val avatarMargin = context.resources.getDimensionPixelSize(R.dimen.account_avatar_margin)

        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)

            val position = parent.getChildAdapterPosition(child)
            val adapter = parent.adapter as ThreadAdapter

            val current = adapter.getItem(position)
            val dividerTop: Int
            val dividerBottom: Int
            if (current != null) {
                val above = adapter.getItem(position - 1)
                dividerTop = if (above != null && above.id == current.inReplyToId) {
                    child.top
                } else {
                    child.top + avatarMargin
                }
                val below = adapter.getItem(position + 1)
                dividerBottom = if (below != null && current.id == below.inReplyToId &&
                        adapter.detailedStatusPosition != position) {
                    child.bottom
                } else {
                    child.top + avatarMargin
                }

                if (parent.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
                    divider.setBounds(dividerStart, dividerTop, dividerEnd, dividerBottom)
                } else {
                    divider.setBounds(canvas.width - dividerEnd, dividerTop, canvas.width - dividerStart, dividerBottom)
                }
                divider.draw(canvas)

            }
        }
    }
}
