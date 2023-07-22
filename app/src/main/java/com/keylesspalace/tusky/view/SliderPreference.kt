package com.keylesspalace.tusky.view

import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.LabelFormatter.LABEL_GONE
import com.google.android.material.slider.Slider
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.PrefSliderBinding
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import java.lang.Float.max
import java.lang.Float.min

/**
 * Slider preference
 *
 * Similar to [androidx.preference.SeekBarPreference], but better because:
 *
 * - Uses a [Slider] instead of a [android.widget.SeekBar]. Slider supports float values, and step sizes
 *   other than 1.
 * - Displays the currently selected value in the Preference's summary, for consistency
 *   with platform norms.
 * - Icon buttons can be displayed at the start/end of the slider. Pressing them will
 *   increment/decrement the slider by `stepSize`.
 * - User can supply a custom formatter to format the summary value
 */
class SliderPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes),
    Slider.OnChangeListener,
    Slider.OnSliderTouchListener {

    /** Backing property for `value` */
    private var _value = 0F

    /**
     * @see Slider.getValue
     * @see Slider.setValue
     */
    var value: Float = defaultValue
        get() = _value
        set(v) {
            val clamped = max(max(v, valueFrom), min(v, valueTo))
            if (clamped == field) return
            _value = clamped
            persistFloat(v)
            notifyChanged()
        }

    /** @see Slider.setValueFrom */
    var valueFrom: Float

    /** @see Slider.setValueTo */
    var valueTo: Float

    /** @see Slider.setStepSize */
    var stepSize: Float

    /**
     * Format string to be applied to values before setting the summary. For more control set
     * [SliderPreference.formatter]
     */
    var format: String = defaultFormat

    /**
     * Function that will be used to format the summary. The default formatter formats using the
     * value of the [SliderPreference.format] property.
     */
    var formatter: (Float) -> String = { format.format(it) }

    /**
     * Optional icon to show in a button at the start of the slide. If non-null the button is
     * shown. Clicking the button decrements the value by one step.
     */
    var decrementIcon: Drawable? = null

    /**
     * Optional icon to show in a button at the end of the slider. If non-null the button is
     * shown. Clicking the button increments the value by one step.
     */
    var incrementIcon: Drawable? = null

    /** View binding */
    private lateinit var binding: PrefSliderBinding

    init {
        // Using `widgetLayoutResource` here would be incorrect, as that tries to put the entire
        // preference layout to the right of the title and summary.
        layoutResource = R.layout.pref_slider

        val a = context.obtainStyledAttributes(attrs, R.styleable.SliderPreference, defStyleAttr, defStyleRes)

        value = a.getFloat(R.styleable.SliderPreference_android_value, defaultValue)
        valueFrom = a.getFloat(R.styleable.SliderPreference_android_valueFrom, defaultValueFrom)
        valueTo = a.getFloat(R.styleable.SliderPreference_android_valueTo, defaultValueTo)
        stepSize = a.getFloat(R.styleable.SliderPreference_android_stepSize, defaultStepSize)
        format = a.getString(R.styleable.SliderPreference_format) ?: defaultFormat

        val decrementIconResource = a.getResourceId(R.styleable.SliderPreference_iconStart, -1)
        if (decrementIconResource != -1) {
            decrementIcon = AppCompatResources.getDrawable(context, decrementIconResource)
        }

        val incrementIconResource = a.getResourceId(R.styleable.SliderPreference_iconEnd, -1)
        if (incrementIconResource != -1) {
            incrementIcon = AppCompatResources.getDrawable(context, incrementIconResource)
        }

        a.recycle()
    }

    override fun onGetDefaultValue(a: TypedArray, i: Int): Any {
        return a.getFloat(i, defaultValue)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedFloat((defaultValue ?: Companion.defaultValue) as Float)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        binding = PrefSliderBinding.bind(holder.itemView)

        binding.root.isClickable = false

        binding.slider.clearOnChangeListeners()
        binding.slider.clearOnSliderTouchListeners()
        binding.slider.addOnChangeListener(this)
        binding.slider.addOnSliderTouchListener(this)
        binding.slider.value = value // sliderValue
        binding.slider.valueTo = valueTo
        binding.slider.valueFrom = valueFrom
        binding.slider.stepSize = stepSize

        // Disable the label, the value is shown in the preference summary
        binding.slider.labelBehavior = LABEL_GONE
        binding.slider.isEnabled = isEnabled

        binding.summary.show()
        binding.summary.text = formatter(value)

        decrementIcon?.let { icon ->
            binding.decrement.icon = icon
            binding.decrement.show()
            binding.decrement.setOnClickListener {
                value -= stepSize
            }
        } ?: binding.decrement.hide()

        incrementIcon?.let { icon ->
            binding.increment.icon = icon
            binding.increment.show()
            binding.increment.setOnClickListener {
                value += stepSize
            }
        } ?: binding.increment.hide()
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (!fromUser) return
        binding.summary.text = formatter(value)
    }

    override fun onStartTrackingTouch(slider: Slider) {
        // Deliberately empty
    }

    override fun onStopTrackingTouch(slider: Slider) {
        value = slider.value
    }

    companion object {
        private const val TAG = "SliderPreference"
        private const val defaultValueFrom = 0F
        private const val defaultValueTo = 1F
        private const val defaultValue = 0.5F
        private const val defaultStepSize = 0.1F
        private const val defaultFormat = "%3.1f"
    }
}
