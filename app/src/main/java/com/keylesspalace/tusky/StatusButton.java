/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky. If not, see
 * <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;

public class StatusButton extends AppCompatImageButton {
    private static final int[] STATE_MARKED = { R.attr.state_marked };

    private boolean marked;

    public StatusButton(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        TypedArray array = context.getTheme().obtainStyledAttributes(
                attributeSet, R.styleable.StatusButton, 0, 0);
        try {
            marked = array.getBoolean(R.styleable.StatusButton_state_marked, false);
        } finally {
            array.recycle();
        }
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        if (marked) {
            extraSpace += 1;
        }
        int[] drawableState = super.onCreateDrawableState(extraSpace);
        if (marked) {
            mergeDrawableStates(drawableState, STATE_MARKED);
        }
        return drawableState;
    }

    public void setMarked(boolean marked) {
        this.marked = marked;
    }
}
