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
import androidx.core.content.res.use
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
    var lineWidth = 0f

    @get:ColorInt
    @ColorInt
    var graphColor = 0

    @get:ColorInt
    @ColorInt
    var metaColor = 0

    var proportionalTrending = false

    private lateinit var primaryLinePaint: Paint
    private lateinit var secondaryLinePaint: Paint
    private lateinit var primaryCirclePaint: Paint
    private lateinit var secondaryCirclePaint: Paint
    private lateinit var graphPaint: Paint
    private lateinit var metaPaint: Paint

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
        context.obtainStyledAttributes(attr, R.styleable.GraphView).use { a ->
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

            lineWidth = a.getDimensionPixelSize(
                R.styleable.GraphView_lineWidth,
                R.dimen.graph_line_thickness
            ).toFloat()

            graphColor = ContextCompat.getColor(
                context,
                a.getResourceId(
                    R.styleable.GraphView_graphColor,
                    R.color.colorBackground,
                )
            )

            metaColor = ContextCompat.getColor(
                context,
                a.getResourceId(
                    R.styleable.GraphView_metaColor,
                    R.color.dividerColor,
                )
            )

            proportionalTrending = a.getBoolean(
                R.styleable.GraphView_proportionalTrending,
                proportionalTrending,
            )
        }

        primaryLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryLineColor
            strokeWidth = lineWidth
            style = Paint.Style.STROKE
        }

        primaryCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryLineColor
            style = Paint.Style.FILL
        }

        secondaryLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryLineColor
            strokeWidth = lineWidth
            style = Paint.Style.STROKE
        }

        secondaryCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryLineColor
            style = Paint.Style.FILL
        }

        graphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = graphColor
        }

        metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = metaColor
            strokeWidth = 0f
            style = Paint.Style.STROKE
        }
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

        val ratioedData = lineData.map { it.toFloat() * mainRatio }

        val pointDistance = dataSpacing(ratioedData)

        /** X coord of the start of this path segment */
        var startX = 0F

        /** Y coord of the start of this path segment */
        var startY = 0F

        /** X coord of the end of this path segment */
        var endX: Float

        /** Y coord of the end of this path segment */
        var endY: Float

        /** X coord of bezier control point #1 */
        var controlX1: Float

        /** X coord of bezier control point #2 */
        var controlX2: Float

        // Draw cubic bezier curves between each pair of points.
        ratioedData.forEachIndexed { index, magnitude ->
            val x = pointDistance * index.toFloat()
            val y = height.toFloat() - magnitude

            if (index == 0) {
                path.reset()
                path.moveTo(x, y)
                startX = x
                startY = y
            } else {
                endX = x
                endY = y

                // X-coord for a control point is placed one third of the distance between the
                // two points.
                val offsetX = (endX - startX) / 3
                controlX1 = startX + offsetX
                controlX2 = endX - offsetX
                path.cubicTo(controlX1, startY, controlX2, endY, x, y)

                startX = x
                startY = y
            }
        }
    }

    private fun dataSpacing(data: List<Any>) = width.toFloat() / max(data.size - 1, 1).toFloat()

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (primaryLinePath.isEmpty && width > 0) {
            initializeVertices()
        }

        canvas?.apply {
            drawRect(sizeRect, graphPaint)

            val pointDistance = dataSpacing(primaryLineData)

            // Vertical tick marks
            for (i in 0 until primaryLineData.size + 1) {
                drawLine(
                    i * pointDistance,
                    height.toFloat(),
                    i * pointDistance,
                    height - (height.toFloat() / 20),
                    metaPaint
                )
            }

            // X-axis
            drawLine(0f, height.toFloat(), width.toFloat(), height.toFloat(), metaPaint)

            // Data lines
            drawLine(
                canvas = canvas,
                linePath = secondaryLinePath,
                linePaint = secondaryLinePaint,
                circlePaint = secondaryCirclePaint,
                lineThickness = lineWidth,
            )
            drawLine(
                canvas = canvas,
                linePath = primaryLinePath,
                linePaint = primaryLinePaint,
                circlePaint = primaryCirclePaint,
                lineThickness = lineWidth,
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
