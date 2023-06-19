package com.keylesspalace.tusky.view

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ViewBackgroundMessageBinding
import com.keylesspalace.tusky.util.addDrawables
import com.keylesspalace.tusky.util.getDrawableRes
import com.keylesspalace.tusky.util.getErrorString
import com.keylesspalace.tusky.util.visible

/**
 * This view is used for screens with content which may be empty or might have failed to download.
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

    fun setup(throwable: Throwable, listener: ((v: View) -> Unit)? = null) {
        setup(throwable.getDrawableRes(), throwable.getErrorString(context), listener)
    }

    fun setup(
        @DrawableRes imageRes: Int,
        @StringRes messageRes: Int,
        clickListener: ((v: View) -> Unit)? = null
    ) = setup(imageRes, context.getString(messageRes), clickListener)

    /**
     * Setup image, message and button.
     * If [clickListener] is `null` then the button will be hidden.
     */
    fun setup(
        @DrawableRes imageRes: Int,
        message: String,
        clickListener: ((v: View) -> Unit)? = null
    ) {
        binding.messageTextView.text = message
        binding.messageTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.imageView.setImageResource(imageRes)
        binding.button.setOnClickListener(clickListener)
        binding.button.visible(clickListener != null)
    }

    fun showHelp(@StringRes helpRes: Int) {
        val size: Int = binding.helpText.textSize.toInt() + 2
        val color = binding.helpText.currentTextColor
        val text = context.getText(helpRes)
        val textWithDrawables = addDrawables(text, color, size, context)

        binding.helpText.setText(textWithDrawables, TextView.BufferType.SPANNABLE)

        binding.helpText.visible(true)
    }
}
