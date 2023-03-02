package com.keylesspalace.tusky.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import com.keylesspalace.tusky.databinding.ViewBackgroundMessageBinding
import com.keylesspalace.tusky.util.visible
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import java.util.regex.Pattern


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
        // TODO this seems to be needed? Otherwise most parts are hidden?
        // But why? This is exactly the same as specified in the xml.

        gravity = Gravity.CENTER_HORIZONTAL
        orientation = VERTICAL

        if (isInEditMode) {
            setup(com.keylesspalace.tusky.R.drawable.elephant_offline, com.keylesspalace.tusky.R.string.error_network) {}
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

    fun showHelp(@StringRes helpRes: Int) {
        val size: Int = binding.helpText.textSize.toInt() + 2
        val alignment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) DynamicDrawableSpan.ALIGN_CENTER else DynamicDrawableSpan.ALIGN_BASELINE

        val builder = SpannableStringBuilder(context.getText(helpRes))

        val pattern = Pattern.compile("\\[(drawable|iconics) ([0-9a-z_]+)\\]")
        val matcher = pattern.matcher(builder)
        while (matcher.find()) {
            val resourceType = matcher.group(1)
            val resourceName = matcher.group(2)
                ?: continue

            val drawable: Drawable? = when(resourceType)  {
                "iconics" -> IconicsDrawable(context, GoogleMaterial.getIcon(resourceName))
                else -> {
                    val drawableResourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
                    if (drawableResourceId != 0) AppCompatResources.getDrawable(context, drawableResourceId) else null
                }
            }

            if (drawable != null) {
                // give it text size (bit bigger) and color
                drawable.setBounds(0, 0, size, size)
                drawable.setTint(binding.helpText.currentTextColor)

                builder.setSpan(ImageSpan(drawable, alignment), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        binding.helpText.setText(builder, TextView.BufferType.SPANNABLE)

        binding.helpText.visible(true)
    }
}
