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

package com.keylesspalace.tusky.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.adapter.ThreadAdapter;
import com.keylesspalace.tusky.viewdata.StatusViewData;

public class ConversationLineItemDecoration extends RecyclerView.ItemDecoration {
    private final Context context;
    private final Drawable divider;

    public ConversationLineItemDecoration(Context context, Drawable divider) {
        this.context = context;
        this.divider = divider;
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        int dividerLeft  = parent.getPaddingLeft()
                + context.getResources().getDimensionPixelSize(R.dimen.status_left_line_margin);
        int dividerRight = dividerLeft + divider.getIntrinsicWidth();

        int childCount   = parent.getChildCount();
        int avatarMargin = context.getResources()
                .getDimensionPixelSize(R.dimen.account_avatar_margin);

        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);

            int position = parent.getChildAdapterPosition(child);
            ThreadAdapter adapter = (ThreadAdapter) parent.getAdapter();
            StatusViewData.Concrete current = adapter.getItem(position);
            int dividerTop, dividerBottom;
            if (current != null) {
                StatusViewData.Concrete above = adapter.getItem(position - 1);
                if (above != null && above.getId().equals(current.getInReplyToId())) {
                    dividerTop = child.getTop();
                } else {
                    dividerTop = child.getTop() + avatarMargin;
                }
                StatusViewData.Concrete below = adapter.getItem(position + 1);
                if (below != null && current.getId().equals(below.getInReplyToId())) {
                    dividerBottom = child.getBottom();
                } else {
                    dividerBottom = child.getTop() + avatarMargin;
                }
            } else {
                dividerTop = child.getTop();
                if (i == 0) {
                    dividerTop += avatarMargin;
                }
                if (i == childCount - 1) {
                    dividerBottom = child.getTop() + avatarMargin;
                } else {
                    dividerBottom = child.getBottom();
                }
            }

            divider.setBounds(dividerLeft, dividerTop, dividerRight, dividerBottom);
            divider.draw(c);
        }
    }
}
