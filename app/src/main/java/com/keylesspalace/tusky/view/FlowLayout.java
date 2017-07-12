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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.util.Assert;

public class FlowLayout extends ViewGroup {
    private int paddingHorizontal; // internal padding between child views
    private int paddingVertical;   //

    public FlowLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlowLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.getTheme()
                .obtainStyledAttributes(attrs, R.styleable.FlowLayout, 0, 0);
        try {
            paddingHorizontal = a.getDimensionPixelSize(R.styleable.FlowLayout_paddingHorizontal, 0);
            paddingVertical = a.getDimensionPixelSize(R.styleable.FlowLayout_paddingVertical, 0);
        } finally {
            a.recycle();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Assert.expect(MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED);
        int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int height = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();
        int count = getChildCount();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int childHeightMeasureSpec;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
        } else {
            childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        }
        int rowHeight = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                int widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
                child.measure(widthSpec, childHeightMeasureSpec);
                child.getLayoutParams();
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                if (x + childWidth > width) {
                    x = getPaddingLeft();
                    y += rowHeight;
                    rowHeight = childHeight + paddingVertical;
                } else {
                    rowHeight = Math.max(rowHeight, childHeight + paddingVertical);
                }
                x += childWidth + paddingHorizontal;
            }
        }
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            height = y + rowHeight;
        } else if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            if (y + rowHeight < height) {
                height = y + rowHeight;
            }
        }
        height += 5; // Fudge to avoid clipping bottom of last row.
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = r - l;
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                if (x + childWidth > width) {
                    x = getPaddingLeft();
                    y += rowHeight;
                    rowHeight = childHeight + paddingVertical;
                } else {
                    rowHeight = Math.max(childHeight, rowHeight);
                }
                child.layout(x, y, x + childWidth, y + childHeight);
                x += childWidth + paddingHorizontal;
            }
        }
    }
}