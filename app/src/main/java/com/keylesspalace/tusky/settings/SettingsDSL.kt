package com.keylesspalace.tusky.settings

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.fragment.app.Fragment
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.switchmaterial.SwitchMaterial
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemPrefBinding
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.util.visible

typealias Update = () -> Unit

class PreferenceParent(
    val context: Context,
    val registerUpdate: (update: Update) -> Unit,
    val addPref: (pref: View) -> Unit,
) {
}

private fun itemLayout(context: Context): LinearLayout {
    return LinearLayout(context).apply {
        layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.MarginLayoutParams.MATCH_PARENT,
            ViewGroup.MarginLayoutParams.WRAP_CONTENT,
        )
        setPadding(dpToPx(16), 0, dpToPx(16), 0)
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
    }
}

fun PreferenceParent.checkBoxPreference(
    text: String,
    selected: Boolean,
    onSelection: (Boolean) -> Unit
) {
    val layout = itemLayout(context)

    val textView = TextView(context)
    textView.text = text
    layout.addView(textView)

    val checkbox = CheckBox(context)
    layout.addView(checkbox)
    checkbox.isSelected = selected

    // TODO listener
//    builder(pref)
//    addPref(pref)
    addPref(layout)
}

fun PreferenceParent.clickPreference(
    title: String,
    onClick: () -> Unit,
) {
    val layout = inflateItemLayout().apply {
        setTitle(title)
        setShowSummary(false)
    }
    layout.root.setOnClickListener { onClick() }
    addPref(layout.root)
}

fun PreferenceParent.switchPreference(
    title: String,
    isChecked: () -> Boolean,
    icon: Drawable? = null,
    onSelection: (Boolean) -> Unit
) {
    val layout = inflateItemLayout().apply {
        setTitle(title)
        setShowSummary(false)
        icon?.let { setIcon(it) }
    }

    val switch = SwitchMaterial(context)
    layout.prefCutomContainer.addView(switch)
    registerUpdate {
        switch.isChecked = isChecked()
    }
    layout.root.setOnClickListener { onSelection(!isChecked()) }
    switch.setOnCheckedChangeListener { _, isChecked -> onSelection(isChecked) }
    switch.updateLayoutParams<FrameLayout.LayoutParams> {
        updateMargins(left = dpToPx(8))
    }

    addPref(layout.root)
}

fun PreferenceParent.editTextPreference(
    title: String,
    value: () -> String,
    onNewValue: (String) -> Unit
) {
    val layout = inflateItemLayout()
    layout.root.setOnClickListener {
        val editLayout = FrameLayout(context)
        val editText = EditText(context).apply {
            setText(value())
        }
        editLayout.addView(editText)
        editText.updateLayoutParams<FrameLayout.LayoutParams> {
            setMargins(dpToPx(8), 0, dpToPx(8), 0)
        }

        AlertDialog.Builder(context)
            .setView(editLayout)
            .setTitle(title)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                onNewValue(editText.text.toString())
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }
    layout.prefTitle.text = title
    registerUpdate {
        layout.prefSummary.text = value()
        layout.prefSummary.visible(value().isNotBlank())
    }
    addPref(layout.root)
}

data class PreferenceOption<T>(val name: String, val value: T)

@Suppress("FunctionName")
fun <T> PreferenceParent.PreferenceOption(pair: Pair<T, Int>): PreferenceOption<T> {
    return PreferenceOption(context.getString(pair.second), pair.first)
}

infix fun <T> T.named(name: String) = PreferenceOption(name, this)

fun <T> PreferenceParent.listPreference(
    title: String,
    options: List<PreferenceOption<T>>,
    selected: () -> T,
    icon: Drawable? = null,
    onSelection: (T) -> Unit,
) {
    val layout = inflateItemLayout().apply {
        setTitle(title)
        setShowSummary(true)
        icon?.let { setIcon(it) }
    }

    registerUpdate {
        val selectedOptionIndex = options.indexOfFirst { it.value == selected() }

        layout.setSummary(options[selectedOptionIndex].name)

        layout.root.setOnClickListener {
            AlertDialog.Builder(context)
                .setSingleChoiceItems(
                    options.map { it.name }.toTypedArray(),
                    selectedOptionIndex,
                ) { dialog, wh ->
                    onSelection(options[wh].value)
                    dialog.dismiss()
                }
                .setCancelable(true)
                .show()
        }
    }
    addPref(layout.root)
}

fun PreferenceParent.customListPreference(
    title: String,
    selected: () -> String,
    icon: Drawable? = null,
    onClick: () -> Unit
) {
    val layout = inflateItemLayout().apply {
        setTitle(title)
        setShowSummary(true)
        icon?.let { setIcon(it) }
    }

    layout.root.setOnClickListener {
        onClick()
    }

    registerUpdate {
        layout.setSummary(selected())
    }
    addPref(layout.root)
}


fun PreferenceParent.preferenceCategory(
    @StringRes title: Int,
    builder: PreferenceParent.() -> Unit
) {
    val categoryLayout = LinearLayout(context)
    categoryLayout.orientation = LinearLayout.VERTICAL

    addPref(categoryLayout)

    val titleLayout = itemLayout(context).apply {
        setPadding(dpToPx(16), dpToPx(8 + 16), dpToPx(16), dpToPx(8))
    }
    categoryLayout.addView(titleLayout)

    val titleView = TextView(context).apply {
        layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.MarginLayoutParams.WRAP_CONTENT,
            ViewGroup.MarginLayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dpToPx(52)
        }

        setTextAppearance(context, R.style.TextAppearance_AppCompat_Body2)
        setTextColor(ThemeUtils.getColor(context, R.attr.colorPrimary))

        setText(title)
    }


    titleLayout.addView(titleView)
    val newParent = PreferenceParent(context, registerUpdate) { categoryLayout.addView(it) }
    builder(newParent)
}

inline fun Fragment.makePreferenceScreen(
    viewGroup: ViewGroup,
    builder: PreferenceParent.() -> Unit
): (() -> Unit) {
    val context = requireContext()
    val updates = mutableListOf<Update>()
    val updateTrigger = {
        for (update in updates) {
            update()
        }
    }
    val parent = PreferenceParent(context, updates::add) { viewGroup.addView(it) }
    // For some functions (like dependencies) it's much easier for us if we attach screen first
    // and change it later
    builder(parent)
    // Run once to update all views
    updateTrigger()
    return updateTrigger
}

fun View.dpToPx(dp: Int) = Utils.dpToPx(this.context, dp)

private fun PreferenceParent.dpToPx(dp: Int) = Utils.dpToPx(this.context, dp)

private fun PreferenceParent.inflateItemLayout(): ItemPrefBinding {
    return ItemPrefBinding.inflate(LayoutInflater.from(context))
}

private fun ItemPrefBinding.setTitle(text: String) {
    prefTitle.text = text
}

private fun ItemPrefBinding.setSummary(summary: String) {
    prefSummary.text = summary
}

private fun ItemPrefBinding.setShowSummary(show: Boolean) {
    prefSummary.isVisible = show
}

private fun ItemPrefBinding.setIcon(icon: Drawable) {
    prefIcon.setImageDrawable(icon)
}