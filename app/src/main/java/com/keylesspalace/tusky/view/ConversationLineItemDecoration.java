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

public class ConversationLineItemDecoration extends RecyclerView.ItemDecoration {
    private final Context mContext;
    private final Drawable mDivider;

    public ConversationLineItemDecoration(Context context, Drawable divider) {
        mContext = context;
        mDivider = divider;
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        int dividerLeft  = parent.getPaddingLeft() + mContext.getResources().getDimensionPixelSize(R.dimen.status_left_line_margin);
        int dividerRight = dividerLeft + mDivider.getIntrinsicWidth();

        int childCount   = parent.getChildCount();
        int avatarMargin = mContext.getResources().getDimensionPixelSize(R.dimen.account_avatar_margin);

        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);

            int dividerTop    = child.getTop() + (i == 0 ? avatarMargin : 0);
            int dividerBottom = (i == childCount - 1 ? child.getTop() + avatarMargin : child.getBottom());

            mDivider.setBounds(dividerLeft, dividerTop, dividerRight, dividerBottom);
            mDivider.draw(c);
        }
    }
}
