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

class ConversationLineItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
    private val divider: Drawable = ContextCompat.getDrawable(
        context,
        R.drawable.conversation_thread_line
    )!!

    private val avatarTopMargin = context.resources.getDimensionPixelSize(
        R.dimen.account_avatar_margin
    )
    private val halfAvatarHeight = context.resources.getDimensionPixelSize(R.dimen.timeline_status_avatar_height) / 2
    private val statusLineMarginStart = context.resources.getDimensionPixelSize(
        R.dimen.status_line_margin_start
    )

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val dividerStart = parent.paddingStart + statusLineMarginStart
        val dividerEnd = dividerStart + divider.intrinsicWidth

        val items = (parent.adapter as ThreadAdapter).currentList

        parent.forEach { statusItemView ->
            val position = parent.getChildAdapterPosition(statusItemView)

            items.getOrNull(position)?.let { current ->
                val above = items.getOrNull(position - 1)
                val dividerTop = if (above != null && above.id == current.status.inReplyToId) {
                    statusItemView.top
                } else {
                    statusItemView.top + avatarTopMargin + halfAvatarHeight
                }
                val below = items.getOrNull(position + 1)
                val dividerBottom = if (below != null && current.id == below.status.inReplyToId && !current.isDetailed) {
                    statusItemView.bottom
                } else {
                    statusItemView.top + avatarTopMargin + halfAvatarHeight
                }

                if (parent.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
                    divider.setBounds(dividerStart, dividerTop, dividerEnd, dividerBottom)
                } else {
                    divider.setBounds(
                        canvas.width - dividerEnd,
                        dividerTop,
                        canvas.width - dividerStart,
                        dividerBottom
                    )
                }
                divider.draw(canvas)
            }
        }
    }
}
