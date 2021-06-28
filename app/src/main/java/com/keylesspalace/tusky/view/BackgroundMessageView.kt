package com.keylesspalace.tusky.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ViewBackgroundMessageBinding
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

    private val binding = ViewBackgroundMessageBinding.inflate(LayoutInflater.from(context), this)

    init {
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
    fun setup(
        @DrawableRes imageRes: Int,
        @StringRes messageRes: Int,
        clickListener: ((v: View) -> Unit)? = null
    ) {
        binding.messageTextView.setText(messageRes)
        binding.imageView.setImageResource(imageRes)
        binding.button.setOnClickListener(clickListener)
        binding.button.visible(clickListener != null)
    }
}
