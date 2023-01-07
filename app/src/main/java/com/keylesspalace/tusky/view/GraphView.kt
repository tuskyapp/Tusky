/* Copyright 2023 Tusky Contributors
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
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.keylesspalace.tusky.R
import kotlin.math.max
import kotlin.random.Random


class GraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {
    @get:ColorInt
    @ColorInt
    var lineColor = 0

    @get:Dimension
    var lineThickness = 0f

    @get:ColorInt
    @ColorInt
    var fillColor = 0

    @get:ColorInt
    @ColorInt
    var graphColor = 0

    var proportionalTrending = false

    private lateinit var linePaint: Paint
    private lateinit var fillPaint: Paint
    private lateinit var graphPaint: Paint

    private lateinit var sizeRect: Rect
    private var linePath: Path = Path()
    private var fillPath: Path = Path()

    var maxTrendingValue: Int = 300
    var data: List<Int> = if (isInEditMode) listOf(
        Random.nextInt(300),
        Random.nextInt(300),
        Random.nextInt(300),
        Random.nextInt(300),
        Random.nextInt(300),
        Random.nextInt(300),
        Random.nextInt(300),
    ) else listOf(
        1, 1, 1, 1, 1, 1, 1,
    )
        set(value) {
            field = value.map { max(1, it) }

            if (linePath.isEmpty && width > 0) {
                initializeVertices()
                invalidate()
            }
        }

    init {
        initFromXML(attrs)
    }

    private fun initFromXML(attr: AttributeSet?) {
        val a = context.obtainStyledAttributes(attr, R.styleable.GraphView)

        lineColor = ContextCompat.getColor(
            context,
            a.getResourceId(
                R.styleable.GraphView_lineColor,
                R.color.tusky_blue_light,
            )
        )

        lineThickness = resources.getDimension(
            a.getResourceId(
                R.styleable.GraphView_lineThickness,
                R.dimen.graph_line_thickness,
            )
        )

        fillColor = ContextCompat.getColor(
            context,
            a.getResourceId(
                R.styleable.GraphView_fillColor,
                R.color.tusky_blue,
            )
        )

        graphColor = ContextCompat.getColor(
            context,
            a.getResourceId(
                R.styleable.GraphView_graphColor,
                R.color.colorBackground,
            )
        )

        proportionalTrending = a.getBoolean(
            R.styleable.GraphView_proportionalTrending,
            proportionalTrending,
        )

        linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor

            style = Paint.Style.STROKE;
            strokeJoin = Paint.Join.MITER;
            strokeCap = Paint.Cap.SQUARE;
        }

        fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor

            style = Paint.Style.FILL;
            strokeJoin = Paint.Join.MITER;
            strokeCap = Paint.Cap.SQUARE;
        }

        graphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = graphColor
        }

        a.recycle()
    }

    private fun initializeVertices() {
        sizeRect = Rect(0, 0, width, height)

        val max = if (proportionalTrending) {
            maxTrendingValue
        } else {
            max(data.max(), 1)
        }
        val mainRatio = height.toFloat() / max.toFloat()
        val pointDistance = width.toFloat() / max(data.size - 1, 1).toFloat()

        data.forEachIndexed { index, magnitude ->
            val x = pointDistance * index.toFloat()

            val ratio = magnitude.toFloat() / max.toFloat()

            val y = height.toFloat() - (magnitude.toFloat() * ratio * mainRatio)

            if (index == 0) {
                linePath.reset()
                linePath.moveTo(x, y)
                fillPath.reset()
                fillPath.moveTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(width.toFloat(), height.toFloat())
        fillPath.lineTo(0f, height.toFloat())
        fillPath.lineTo(0f, height.toFloat() - data.first())
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (linePath.isEmpty && width > 0) {
            initializeVertices()
        }

        canvas?.apply {
            drawRect(sizeRect, graphPaint)

            drawPath(
                fillPath,
                fillPaint,
            )

            drawPath(
                linePath,
                linePaint,
            )
        }
    }
}