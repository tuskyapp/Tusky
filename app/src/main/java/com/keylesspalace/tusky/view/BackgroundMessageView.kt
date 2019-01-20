package com.keylesspalace.tusky.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.util.visible


/**
 * This view is used for screens with downloadable content which may fail.
 * Can show an image, text and button below them.
 */
class BackgroundMessageView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val message: TextView
    private val button: Button

    init {
        View.inflate(context, R.layout.view_background_message, this)
        gravity = Gravity.CENTER_HORIZONTAL
        orientation = VERTICAL

        message = findViewById(R.id.messageTextView)
        button = findViewById(R.id.button)

        if (isInEditMode) {
            setup(R.drawable.elephant_offline, R.string.error_network) {}
        }
        requestLayout()
    }

    /**
     * Setup image, message and button.
     * If [clickListener] is `null` then the button will be hidden.
     */
    fun setup(@DrawableRes imageRes: Int, @StringRes messageRes: Int, clickListener: ((v: View) -> Unit)?) {
        message.setText(messageRes)
        message.setCompoundDrawablesWithIntrinsicBounds(null, context.getDrawable(imageRes),
                null, null)
        button.setOnClickListener(clickListener)
        button.visible(clickListener != null)
    }
}