package com.keylesspalace.tusky.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.util.visible


class BackgroundMessageView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val image: ImageView
    private val message: TextView
    private val button: Button

    init {
        View.inflate(context, R.layout.view_background_message, this)
        gravity = Gravity.CENTER_HORIZONTAL
        orientation = VERTICAL

        image = findViewById(R.id.imageView)
        message = findViewById(R.id.messageTextView)
        button = findViewById(R.id.button)

////        if (isInEditMode) {
        image.setImageResource(R.drawable.elephant_offline)
        message.setText(R.string.error_network)
////        }
        requestLayout()
    }

    fun setup(@DrawableRes imageRes: Int, @StringRes messageRes: Int, clickListener: ((v: View) -> Unit)?) {
        image.setImageResource(imageRes)
        message.setText(messageRes)
        button.setOnClickListener(clickListener)
        button.visible(clickListener != null)
    }
}