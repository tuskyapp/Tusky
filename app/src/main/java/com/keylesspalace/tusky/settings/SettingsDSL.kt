package com.keylesspalace.tusky.settings

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.switchmaterial.SwitchMaterial
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.util.ThemeUtils

typealias Update = () -> Unit

class PreferenceParent(
    val context: Context,
    val registerUpdate: (update: Update) -> Unit,
    val addPref: (pref: View) -> Unit,
) {
}

//inline fun PreferenceParent.preference(builder: Preference.() -> Unit): Preference {
//    val pref = Preference(context)
//    builder(pref)
//    addPref(pref)
//    return pref
//}
//
//inline fun PreferenceParent.listPreference(builder: ListPreference.() -> Unit): ListPreference {
//    val pref = ListPreference(context)
//    builder(pref)
//    addPref(pref)
//    return pref
//}
//
//inline fun PreferenceParent.emojiPreference(
//    okHttpClient: OkHttpClient,
//    builder: EmojiPreference.() -> Unit
//): EmojiPreference {
//    val pref = EmojiPreference(context, okHttpClient)
//    builder(pref)
//    addPref(pref)
//    return pref
//}
//
//inline fun PreferenceParent.switchPreference(
//    builder: SwitchPreference.() -> Unit
//): SwitchPreference {
//    val pref = SwitchPreference(context)
//    builder(pref)
//    addPref(pref)
//    return pref
//}
//
//inline fun PreferenceParent.editTextPreference(
//    builder: EditTextPreference.() -> Unit
//): EditTextPreference {
//    val pref = EditTextPreference(context)
//    builder(pref)
//    addPref(pref)
//    return pref
//}
//

private fun itemLayout(context: Context): LinearLayout {
    return LinearLayout(context).apply {
        layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.MarginLayoutParams.MATCH_PARENT,
            ViewGroup.MarginLayoutParams.WRAP_CONTENT,
        )
        setPadding(dpToPx(16), 0, dpToPx(16), 0)
        gravity = Gravity.CENTER_VERTICAL or Gravity.START

        val spacer = ImageView(context).apply {
            minimumWidth = dpToPx(56)
            setPadding(0, dpToPx(4), dpToPx(8), dpToPx(4))
        }
        addView(spacer)
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

private fun TextView.setTextAppearanceRef(ref: Int) {
    val refs = TypedValue()
    context.theme.resolveAttribute(ref, refs, true)
    setTextAppearance(context, refs.resourceId)
}

private fun TextView.setTextColorRef(ref: Int) {
    setTextColor(ThemeUtils.getColor(context, ref))
}

fun PreferenceParent.switchPreference(
    title: String,
    isChecked: () -> Boolean,
    onSelection: (Boolean) -> Unit
) {
    val layout = itemLayout(context)
    val textView = TextView(context).apply {
        text = title
        setTextAppearanceRef(android.R.attr.textAppearanceListItem)
        setTextColorRef(android.R.attr.textColorPrimary)
        ellipsize = TextUtils.TruncateAt.MARQUEE
    }
    textView.layoutParams = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        weight = 1f
        topMargin = dpToPx(16)
        bottomMargin = dpToPx(16)
    }
    layout.addView(textView)

    val switchLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
    }
    switchLayout.layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.MATCH_PARENT,
    )
    layout.addView(switchLayout)

    val switch = SwitchMaterial(context)
    registerUpdate {
        switch.isChecked = isChecked()
    }
    switch.setOnCheckedChangeListener { _, isChecked -> onSelection(isChecked) }
    switchLayout.addView(switch)

    addPref(layout)
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
    onSelection: (T) -> Unit,
) {
    val (layout, summaryView, optionView) = makeListPreferenceLayout()
    summaryView.text = title

    registerUpdate {
        val selectedOptionIndex = options.indexOfFirst { it.value == selected() }

        optionView.setText(options[selectedOptionIndex].name)

        layout.setOnClickListener {
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
}

fun PreferenceParent.customListPreference(
    title: String,
    selected: () -> String,
    onClick: () -> Unit
) {
    val (layout, summaryView, optionView) = makeListPreferenceLayout()
    summaryView.text = title

    layout.setOnClickListener {
        onClick()
    }

    registerUpdate {
        optionView.text = selected()
    }
}

private data class ListPreferenceLayout(
    val layout: LinearLayout,
    val summaryView: TextView,
    val optionView: TextView,
)

private fun PreferenceParent.makeListPreferenceLayout(): ListPreferenceLayout {
    val layout = itemLayout(context).apply {
        isClickable = true
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        setBackgroundResource(outValue.resourceId)
        setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
    }
    val linearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }


    val summaryView = TextView(context).apply {
        setTextAppearanceRef(android.R.attr.textAppearanceListItem)
        setTextColorRef(android.R.attr.textColorPrimary)
    }
    linearLayout.addView(summaryView)

    val optionView = TextView(context)
    linearLayout.addView(optionView)

    layout.addView(linearLayout)

    addPref(layout)
    return ListPreferenceLayout(layout, summaryView, optionView)
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
        )

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
fun PreferenceParent.dpToPx(dp: Int) = Utils.dpToPx(this.context, dp)