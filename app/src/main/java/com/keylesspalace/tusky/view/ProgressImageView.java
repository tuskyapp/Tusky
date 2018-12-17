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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import android.util.AttributeSet;

import com.keylesspalace.tusky.R;
import at.connyduck.sparkbutton.helpers.Utils;

public final class ProgressImageView extends AppCompatImageView {

    private int progress = -1;
    private final RectF progressRect = new RectF();
    private final RectF biggerRect = new RectF();
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable captionDrawable;

    public ProgressImageView(Context context) {
        super(context);
        init();
    }

    public ProgressImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ProgressImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        circlePaint.setColor(ContextCompat.getColor(getContext(), R.color.tusky_blue));
        circlePaint.setStrokeWidth(Utils.dpToPx(getContext(), 4));
        circlePaint.setStyle(Paint.Style.STROKE);

        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        markBgPaint.setStyle(Paint.Style.FILL);
        markBgPaint.setColor(ContextCompat.getColor(getContext(),
                R.color.description_marker_unselected));
        captionDrawable = AppCompatResources.getDrawable(getContext(), R.drawable.spellcheck);
    }

    public void setProgress(int progress) {
        this.progress = progress;
        if (progress != -1) {
            setColorFilter(Color.rgb(123, 123, 123), PorterDuff.Mode.MULTIPLY);
        } else {
            clearColorFilter();
        }
        invalidate();
    }

    public void setChecked(boolean checked) {
        this.markBgPaint.setColor(ContextCompat.getColor(getContext(),
                checked ? R.color.tusky_blue : R.color.description_marker_unselected));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float angle = (progress / 100f) * 360 - 90;
        float halfWidth = getWidth() / 2;
        float halfHeight = getHeight() / 2;
        progressRect.set(halfWidth * 0.75f, halfHeight * 0.75f, halfWidth * 1.25f, halfHeight * 1.25f);
        biggerRect.set(progressRect);
        int margin = 8;
        biggerRect.set(progressRect.left - margin, progressRect.top - margin, progressRect.right + margin, progressRect.bottom + margin);
        canvas.saveLayer(biggerRect, null, Canvas.ALL_SAVE_FLAG);
        if (progress != -1) {
            canvas.drawOval(progressRect, circlePaint);
            canvas.drawArc(biggerRect, angle, 360 - angle - 90, true, clearPaint);
        }
        canvas.restore();

        int circleRadius = Utils.dpToPx(getContext(), 14);
        int circleMargin = Utils.dpToPx(getContext(), 14);

        int circleY = getHeight() - circleMargin - circleRadius / 2;
        int circleX = getWidth() - circleMargin - circleRadius / 2;

        canvas.drawCircle(circleX, circleY, circleRadius, markBgPaint);

        captionDrawable.setBounds(getWidth() - circleMargin - circleRadius,
                getHeight() - circleMargin - circleRadius,
                getWidth() - circleMargin,
                getHeight() - circleMargin);
        captionDrawable.setTint(Color.WHITE);
        captionDrawable.draw(canvas);
    }
}
