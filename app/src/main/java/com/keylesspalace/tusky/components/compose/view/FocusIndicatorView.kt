package com.keylesspalace.tusky.components.compose.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.keylesspalace.tusky.entity.Attachment
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class FocusIndicatorView
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var focus: Attachment.Focus? = null
    private var imageSize: Point? = null
    private var circleRadius: Float? = null

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

    // This needs to be consistent every time it is consulted over the lifetime of the object,
    // so base it on the view width/height whenever the first access occurs.
    private fun getCircleRadius(): Float {
        val circleRadius = this.circleRadius
        if (circleRadius != null)
            return circleRadius
        val newCircleRadius = min(this.width, this.height).toFloat() / 4.0f
        this.circleRadius = newCircleRadius
        return newCircleRadius
    }

    // Remember focus uses -1..1 y-down coordinates (so focus value should be negated for y)
    private fun axisToFocus(value: Float, innerLimit: Int, outerLimit: Int): Float {
        val offset = (outerLimit - innerLimit) / 2 // Assume image is centered in widget frame
        val result = (value - offset) / innerLimit.toFloat() * 2.0f - 1.0f // To range -1..1
        return min(1.0f, max(-1.0f, result)) // Clamp
    }

    private fun axisFromFocus(value: Float, innerLimit: Int, outerLimit: Int): Float {
        val offset = (outerLimit - innerLimit) / 2
        return offset.toFloat() + ((value + 1.0f) / 2.0f) * innerLimit.toFloat() // From range -1..1
    }

    @SuppressLint("ClickableViewAccessibility") // Android Studio wants us to implement PerformClick for accessibility, but that unfortunately cannot be made meaningful for this widget.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL)
            return false

        val imageSize = this.imageSize ?: return false

        // Convert touch xy to point inside image
        focus = Attachment.Focus(axisToFocus(event.x, imageSize.x, this.width), -axisToFocus(event.y, imageSize.y, this.height))
        invalidate()
        return true
    }

    private val transparentDarkGray = 0x40000000
    private val strokeWidth = 4.0f * this.resources.displayMetrics.density

    private val curtainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val curtainPath = Path()

    init {
        curtainPaint.color = transparentDarkGray
        curtainPaint.style = Paint.Style.FILL

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = strokeWidth
        strokePaint.color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val imageSize = this.imageSize
        val focus = this.focus

        if (imageSize != null && focus != null) {
            val x = axisFromFocus(focus.x, imageSize.x, this.width)
            val y = axisFromFocus(-focus.y, imageSize.y, this.height)
            val circleRadius = getCircleRadius()

            curtainPath.reset() // Draw a flood fill with a hole cut out of it
            curtainPath.fillType = Path.FillType.WINDING
            curtainPath.addRect(0.0f, 0.0f, this.width.toFloat(), this.height.toFloat(), Path.Direction.CW)
            curtainPath.addCircle(x, y, circleRadius, Path.Direction.CCW)
            canvas.drawPath(curtainPath, curtainPaint)

            canvas.drawCircle(x, y, circleRadius, strokePaint) // Draw white circle
            canvas.drawCircle(x, y, strokeWidth / 2.0f, strokePaint) // Draw white dot
        }
    }

    // Give a "safe" height based on currently set image size. Assume imageSize is set and height>width already checked
    fun maxAttractiveHeight(): Int {
        val height = this.imageSize!!.y
        val circleRadius = getCircleRadius()

        // Give us enough space for the image, plus on each side half a focus indicator circle, plus a strokeWidth
        return ceil(height.toFloat() + circleRadius * 2.0f + strokeWidth).toInt()
    }
}
