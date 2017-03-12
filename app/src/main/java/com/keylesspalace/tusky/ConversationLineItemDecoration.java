package com.keylesspalace.tusky;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.View;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;

class ConversationLineItemDecoration extends RecyclerView.ItemDecoration {
    private final Context mContext;
    private final Drawable mDivider;

    public ConversationLineItemDecoration(Context context, Drawable divider) {
        mContext = context;
        mDivider = divider;
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        // Fun fact: this method draws in pixels, but all layouts are in DP, so I'm using the divider's
        // own 2dp width to calculate what I want
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
