package com.keylesspalace.tusky.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View

class GradientLineView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val northColor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLUE
    }

    private val southColor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.LTGRAY
    }

    // avoid allocations in the draw() method
    private val rect = Rect()
    private val gradient = GradientDrawable()

    init {
        if (foreground is ColorDrawable) {
            northColor.color = (foreground as ColorDrawable).color
        }
        if (background is ColorDrawable) {
            southColor.color = (background as ColorDrawable).color
        }

        gradient.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        gradient.colors = intArrayOf(northColor.color, southColor.color)
    }

    @SuppressLint("MissingSuperCall")
    override fun draw(canvas: Canvas) {
        // do not call super as it does something strange with the foreground

        rect.set(0, 0, width, height)
        gradient.bounds = rect
        gradient.draw(canvas)
    }
}
