package com.keylesspalace.tusky.settings

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import at.connyduck.sparkbutton.helpers.Utils
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.preference.EmojiPreference
import okhttp3.OkHttpClient

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
//inline fun PreferenceParent.checkBoxPreference(
//    builder: CheckBoxPreference.() -> Unit
//): CheckBoxPreference {
//    val pref = CheckBoxPreference(context)
//    builder(pref)
//    addPref(pref)
//    return pref
//}
//

inline fun PreferenceParent.preferenceCategory(
    @StringRes title: Int,
    builder: PreferenceParent.() -> Unit
) {
    val category = LinearLayout(context)
    addPref(category)
    val titleView = TextView(context).apply {
        layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.MarginLayoutParams.WRAP_CONTENT,
            ViewGroup.MarginLayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dpToPx(20)
            topMargin = 10
            rightMargin = 10
            bottomMargin = 10
        }

        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
        setTextColor(typedValue.data)

        setText(title)
    }

    category.addView(titleView)
    val newParent = PreferenceParent(context) { category.addView(it) }
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