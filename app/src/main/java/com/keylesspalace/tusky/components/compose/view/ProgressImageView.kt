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
package com.keylesspalace.tusky.components.compose.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import at.connyduck.sparkbutton.helpers.Utils
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.view.MediaPreviewImageView

class ProgressImageView : MediaPreviewImageView {
    private var progress = -1
    private val progressRect = RectF()
    private val biggerRect = RectF()
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private lateinit var captionDrawable: Drawable

    constructor(context: Context?) : super(context!!) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!,
        attrs
    ) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {
        circlePaint.color = context.getColor(R.color.tusky_blue)
        circlePaint.strokeWidth = Utils.dpToPx(context, 4).toFloat()
        circlePaint.style = Paint.Style.STROKE
        clearPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        markBgPaint.style = Paint.Style.FILL
        markBgPaint.color = context.getColor(R.color.tusky_grey_10)
        captionDrawable = AppCompatResources.getDrawable(context, R.drawable.spellcheck)!!
    }

    fun setProgress(progress: Int) {
        this.progress = progress
        if (progress != -1) {
            setColorFilter(Color.rgb(123, 123, 123), PorterDuff.Mode.MULTIPLY)
        } else {
            clearColorFilter()
        }
        invalidate()
    }

    fun setChecked(checked: Boolean) {
        markBgPaint.color =
            context.getColor(if (checked) R.color.tusky_blue else R.color.tusky_grey_10)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val angle = progress / 100f * 360 - 90
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        progressRect[halfWidth * 0.75f, halfHeight * 0.75f, halfWidth * 1.25f] = halfHeight * 1.25f
        biggerRect.set(progressRect)
        val margin = 8
        biggerRect[progressRect.left - margin, progressRect.top - margin, progressRect.right + margin] =
            progressRect.bottom + margin
        canvas.saveLayer(biggerRect, null, Canvas.ALL_SAVE_FLAG)
        if (progress != -1) {
            canvas.drawOval(progressRect, circlePaint)
            canvas.drawArc(biggerRect, angle, 360 - angle - 90, true, clearPaint)
        }
        canvas.restore()
        val circleRadius = Utils.dpToPx(context, 14)
        val circleMargin = Utils.dpToPx(context, 14)
        val circleY = height - circleMargin - circleRadius / 2
        val circleX = width - circleMargin - circleRadius / 2
        canvas.drawCircle(circleX.toFloat(), circleY.toFloat(), circleRadius.toFloat(), markBgPaint)
        captionDrawable.setBounds(
            width - circleMargin - circleRadius,
            height - circleMargin - circleRadius,
            width - circleMargin,
            height - circleMargin
        )
        captionDrawable.setTint(Color.WHITE)
        captionDrawable.draw(canvas)
    }
}
