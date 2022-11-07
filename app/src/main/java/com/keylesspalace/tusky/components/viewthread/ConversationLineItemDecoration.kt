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

package com.keylesspalace.tusky.components.viewthread

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.forEach
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R

class ConversationLineItemDecoration(private val context: Context) : RecyclerView.ItemDecoration() {

    private val divider: Drawable = ContextCompat.getDrawable(context, R.drawable.conversation_thread_line)!!

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val dividerStart = parent.paddingStart + context.resources.getDimensionPixelSize(R.dimen.status_line_margin_start)
        val dividerEnd = dividerStart + divider.intrinsicWidth

        val avatarMargin = context.resources.getDimensionPixelSize(R.dimen.account_avatar_margin)

        val items = (parent.adapter as ThreadAdapter).currentList

        parent.forEach { child ->

            val position = parent.getChildAdapterPosition(child)

            val current = items.getOrNull(position)

            if (current != null) {
                val above = items.getOrNull(position - 1)
                val dividerTop = if (above != null && above.id == current.status.inReplyToId) {
                    child.top
                } else {
                    child.top + avatarMargin
                }
                val below = items.getOrNull(position + 1)
                val dividerBottom = if (below != null && current.id == below.status.inReplyToId && !current.isDetailed) {
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
