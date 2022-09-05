package com.keylesspalace.tusky.components.compose.view

import android.content.Context
import android.graphics.Canvas
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

    private var focusDrawable: Drawable = AppCompatResources.getDrawable(context, R.drawable.spellcheck)!! // TODO: use an actual drawable suited as indicator
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        focusDrawable.setBounds(
            posX.toInt() - focusDrawable.intrinsicWidth / 2,
            posY.toInt() - focusDrawable.intrinsicHeight / 2,
            posX.toInt() + focusDrawable.intrinsicWidth / 2,
            posY.toInt() + focusDrawable.intrinsicHeight / 2
        )
        focusDrawable.draw(canvas)
    }
}