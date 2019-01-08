package tech.bigfig.roma.view

import android.content.Context
import androidx.appcompat.widget.AppCompatImageView
import android.util.AttributeSet

/**
 * Created by charlag on 26/10/2017.
 */

class SquareImageView : AppCompatImageView {
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