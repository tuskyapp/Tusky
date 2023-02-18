package com.keylesspalace.tusky.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View


val density = Resources.getSystem().displayMetrics.density

// These are extensions:

val Int.dp: Int
    get() = (this / density).toInt()

val Float.px: Float
    get() = (this * density)

fun Path.matches(width: Int, height: Int, helper: RectF): Boolean {
    this.computeBounds(helper, false)

    return helper.width() == width.toFloat() && helper.height() == height.toFloat();
}

class ZigZagView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val northColor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLUE
    }

    private val southColor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.LTGRAY
    }

    // avoid allocations in the draw() method
    private val path = Path()
    private val rect = Rect()
    private val rectf = RectF()

    init {
        if (foreground is ColorDrawable) {
            northColor.color = (foreground as ColorDrawable).color
        }
        if (background is ColorDrawable) {
            southColor.color = (background as ColorDrawable).color
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun draw(canvas: Canvas) {
        // do not call super as it does something strange with the foreground

        if (!path.matches(width, height, rectf)) {
            preparePath(width.toFloat(), height.toFloat())
        }

        canvas.apply {
            // draw a "background"; the other half of the zig-zag line
            rect.set(0, 0, width, height)
            drawRect(rect, southColor)

            drawPath(path, northColor)
        }
    }

    private fun preparePath(width: Float, height: Float) {
        var x = 0f
        var zig = true

        path.reset()
        path.moveTo(0f, 0f)
        while (x < width) {
            x += height
            path.lineTo(x, height * (if (zig) 1 else 0))
            zig = !zig

        }
        if (!zig) {
            path.lineTo(x, 0f)
        }
        path.close()
    }
}
