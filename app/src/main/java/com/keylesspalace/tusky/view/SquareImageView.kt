package com.keylesspalace.tusky.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView

/**
 * Created by charlag on 26/10/2017.
 */

class SquareImageView : ImageView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attributes: AttributeSet) : super(context, attributes)

    constructor(context: Context, attributes: AttributeSet, defStyleAttr: Int)
            : super(context, attributes, defStyleAttr)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = measuredWidth
        setMeasuredDimension(width, width)
    }
}