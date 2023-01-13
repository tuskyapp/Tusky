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
import android.graphics.PathMeasure
import android.graphics.Rect
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.keylesspalace.tusky.R
import kotlin.math.max

class GraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {
    @get:ColorInt
    @ColorInt
    var primaryLineColor = 0

    @get:ColorInt
    @ColorInt
    var secondaryLineColor = 0

    @get:Dimension
    var lineThickness = 0f

    @get:ColorInt
    @ColorInt
    var graphColor = 0

    var proportionalTrending = false

    private lateinit var primaryLinePaint: Paint
    private lateinit var secondaryLinePaint: Paint
    private lateinit var primaryCirclePaint: Paint
    private lateinit var secondaryCirclePaint: Paint
    private lateinit var graphPaint: Paint

    private lateinit var sizeRect: Rect
    private var primaryLinePath: Path = Path()
    private var secondaryLinePath: Path = Path()

    var maxTrendingValue: Long = 300
    var primaryLineData: List<Long> = if (isInEditMode) listOf(
        30, 60, 70, 80, 130, 190, 80,
    ) else listOf(
        1, 1, 1, 1, 1, 1, 1,
    )
        set(value) {
            field = value.map { max(1, it) }
            primaryLinePath.reset()
            invalidate()
        }

    var secondaryLineData: List<Long> = if (isInEditMode) listOf(
        10, 20, 40, 60, 100, 132, 20,
    ) else listOf(
        1, 1, 1, 1, 1, 1, 1,
    )
        set(value) {
            field = value.map { max(1, it) }
            secondaryLinePath.reset()
            invalidate()
        }

    init {
        initFromXML(attrs)
    }

    private fun initFromXML(attr: AttributeSet?) {
        val a = context.obtainStyledAttributes(attr, R.styleable.GraphView)

        primaryLineColor = ContextCompat.getColor(
            context,
            a.getResourceId(
                R.styleable.GraphView_primaryLineColor,
                R.color.tusky_blue,
            )
        )

        secondaryLineColor = ContextCompat.getColor(
            context,
            a.getResourceId(
                R.styleable.GraphView_secondaryLineColor,
                R.color.tusky_red,
            )
        )

        lineThickness = resources.getDimension(
            a.getResourceId(
                R.styleable.GraphView_lineThickness,
                R.dimen.graph_line_thickness,
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

        primaryLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryLineColor
            strokeWidth = lineThickness
            style = Paint.Style.STROKE
        }

        primaryCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryLineColor
            style = Paint.Style.FILL
        }

        secondaryLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryLineColor
            strokeWidth = lineThickness
            style = Paint.Style.STROKE
        }

        secondaryCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryLineColor
            style = Paint.Style.FILL
        }

        graphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = graphColor
        }

        a.recycle()
    }

    private fun initializeVertices() {
        sizeRect = Rect(0, 0, width, height)

        initLine(primaryLineData, primaryLinePath)
        initLine(secondaryLineData, secondaryLinePath)
    }

    private fun initLine(lineData: List<Long>, path: Path) {
        val max = if (proportionalTrending) {
            maxTrendingValue
        } else {
            max(primaryLineData.max(), 1)
        }
        val mainRatio = height.toFloat() / max.toFloat()
        val pointDistance = width.toFloat() / max(lineData.size - 1, 1).toFloat()

        lineData.forEachIndexed { index, magnitude ->
            val x = pointDistance * index.toFloat()

            val ratio = magnitude.toFloat() / max.toFloat()

            val y = height.toFloat() - (magnitude.toFloat() * ratio * mainRatio)

            if (index == 0) {
                path.reset()
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (primaryLinePath.isEmpty && width > 0) {
            initializeVertices()
        }

        canvas?.apply {
            drawRect(sizeRect, graphPaint)

            drawLine(
                canvas = canvas,
                linePath = secondaryLinePath,
                linePaint = secondaryLinePaint,
                circlePaint = secondaryCirclePaint,
                lineThickness = lineThickness,
            )
            drawLine(
                canvas = canvas,
                linePath = primaryLinePath,
                linePaint = primaryLinePaint,
                circlePaint = primaryCirclePaint,
                lineThickness = lineThickness,
            )
        }
    }

    private fun drawLine(
        canvas: Canvas,
        linePath: Path,
        linePaint: Paint,
        circlePaint: Paint,
        lineThickness: Float,
    ) {
        canvas.apply {
            drawPath(
                linePath,
                linePaint,
            )

            val pm = PathMeasure(linePath, false)
            val coord = floatArrayOf(0f, 0f)
            pm.getPosTan(pm.length * 1f, coord, null)

            drawCircle(coord[0], coord[1], lineThickness * 2f, circlePaint)
        }
    }
}
