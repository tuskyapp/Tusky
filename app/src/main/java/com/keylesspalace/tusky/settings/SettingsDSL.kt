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
import androidx.fragment.app.Fragment
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.switchmaterial.SwitchMaterial
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.util.ThemeUtils

class PreferenceParent(
    val context: Context,
    val addPref: (pref: View) -> Unit
)

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

fun PreferenceParent.switchPreference(
    title: String,
    isChecked: () -> Boolean,
    onSelection: (Boolean) -> Unit
) {
    val layout = itemLayout(context)
    val textView = TextView(context).apply {
        text = title
        val refs = TypedValue()
        context.theme.resolveAttribute(android.R.attr.textAppearanceListItem, refs, true)
        setTextAppearance(context, refs.resourceId)
        setTextColor(ThemeUtils.getColor(context, android.R.attr.textColorPrimary))
        // this is in resource but not at runtime?
//        setSingleLine()
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
    switch.isChecked = isChecked()
    switch.setOnCheckedChangeListener { _, isChecked -> onSelection(isChecked) }
    switchLayout.addView(switch)

    addPref(layout)
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
    val newParent = PreferenceParent(context) { categoryLayout.addView(it) }
    builder(newParent)
}

inline fun Fragment.makePreferenceScreen(
    viewGroup: ViewGroup,
    builder: PreferenceParent.() -> Unit
) {
    val context = requireContext()
    val parent = PreferenceParent(context) { viewGroup.addView(it) }
    // For some functions (like dependencies) it's much easier for us if we attach screen first
    // and change it later
    builder(parent)
}

fun View.dpToPx(dp: Int) = Utils.dpToPx(this.context, dp)
fun PreferenceParent.dpToPx(dp: Int) = Utils.dpToPx(this.context, dp)