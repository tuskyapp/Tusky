package com.keylesspalace.tusky.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.util.visible
import kotlinx.android.synthetic.main.view_background_message.view.*


/**
 * This view is used for screens with downloadable content which may fail.
 * Can show an image, text and button below them.
 */
class BackgroundMessageView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, R.layout.view_background_message, this)
        gravity = Gravity.CENTER_HORIZONTAL
        orientation = VERTICAL

        if (isInEditMode) {
            setup(R.drawable.elephant_offline, R.string.error_network) {}
        }
    }

    /**
     * Setup image, message and button.
     * If [clickListener] is `null` then the button will be hidden.
     */
    fun setup(@DrawableRes imageRes: Int, @StringRes messageRes: Int,
              clickListener: ((v: View) -> Unit)? = null) {
        messageTextView.setText(messageRes)
        imageView.setImageResource(imageRes)
        button.setOnClickListener(clickListener)
        button.visible(clickListener != null)
    }
}