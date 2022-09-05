package com.keylesspalace.tusky.components.compose.view

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Attachment

class FocusIndicatorView
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var posX = 0f
    private var posY = 0f

    fun setFocus(focus: Attachment.Focus) {
        // TODO
        invalidate()
    }

    fun getFocus() {
        // TODO
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // TODO: only handle events if they are on top of the image below
        // TODO: don't handle all event actions

        posX = event.x
        posY = event.y
        invalidate()
        return true
    }

    private val transparentDarkGray = 0x40000000
    private val strokeWidth = 10.0f

    private val curtainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        curtainPaint.setColor(transparentDarkGray)
        curtainPaint.style = Paint.Style.FILL

        strokePaint.setStyle(Paint.Style.STROKE)
        strokePaint.setStrokeWidth(strokeWidth)
        strokePaint.setColor(Color.WHITE)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = getWidth().toFloat()
        val height = getHeight().toFloat()
        val circleRadius = Math.min(width, height) / 4.0f

        val curtainPath = Path() // Draw a flood fill with a hole cut out of it
        curtainPath.setFillType(Path.FillType.WINDING)
        curtainPath.addRect(0.0f, 0.0f, width, height, Path.Direction.CW)
        curtainPath.addCircle(posX, posY, circleRadius, Path.Direction.CCW)
        canvas.drawPath(curtainPath, curtainPaint)

        canvas.drawCircle(posX, posY, circleRadius, strokePaint)             // Draw white circle
        canvas.drawCircle(posX, posY, strokeWidth / 2.0f, strokePaint) // Draw white dot
    }
}