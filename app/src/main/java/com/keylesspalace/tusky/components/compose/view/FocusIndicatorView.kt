package com.keylesspalace.tusky.components.compose.view

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
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
    private var focus: Attachment.Focus? = null
    private var imageSize: Point? = null

    fun setImageSize(width: Int, height: Int) {
        this.imageSize = Point(width, height)
        if (focus != null)
            invalidate()
    }

    fun setFocus(focus: Attachment.Focus) {
        this.focus = focus
        if (imageSize != null)
            invalidate()
    }

    // Assumes setFocus called first
    fun getFocus(): Attachment.Focus {
        return focus!!
    }

    // Remember focus uses -1..1 y-down coordinates
    private fun axisToFocus(value: Float, innerLimit: Int, outerLimit: Int) : Float {
        val offset = (outerLimit-innerLimit)/2
        val result = (value-offset).toFloat()/innerLimit.toFloat() * -2.0f + 1.0f // To range -1..1
        return Math.min(1.0f, Math.max(-1.0f, result)) // Clamp
    }

    private fun axisFromFocus(value:Float, innerLimit: Int, outerLimit: Int) : Float {
        val offset = (outerLimit-innerLimit)/2
        return offset.toFloat() + ((-value+1.0f)/2.0f)*innerLimit.toFloat() // From range -1..1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL)
            return false

        val imageSize = this.imageSize
        if (imageSize == null)
            return false

        // Convert touch xy to point inside image
        focus = Attachment.Focus(axisToFocus(event.x, imageSize.x, getWidth()), axisToFocus(event.y, imageSize.y, getHeight()))
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

        val imageSize = this.imageSize
        val focus = this.focus

        if (imageSize != null && focus != null) {
            val x = axisFromFocus(focus.x, imageSize.x, getWidth())
            val y = axisFromFocus(focus.y, imageSize.y, getHeight())
            val width = getWidth().toFloat()
            val height = getHeight().toFloat()
            val circleRadius = Math.min(width, height) / 4.0f

            val curtainPath = Path() // Draw a flood fill with a hole cut out of it
            curtainPath.setFillType(Path.FillType.WINDING)
            curtainPath.addRect(0.0f, 0.0f, width, height, Path.Direction.CW)
            curtainPath.addCircle(x, y, circleRadius, Path.Direction.CCW)
            canvas.drawPath(curtainPath, curtainPaint)

            canvas.drawCircle(
                x,
                y,
                circleRadius,
                strokePaint
            )             // Draw white circle
            canvas.drawCircle(x, y, strokeWidth / 2.0f, strokePaint) // Draw white dot
        }
    }
}