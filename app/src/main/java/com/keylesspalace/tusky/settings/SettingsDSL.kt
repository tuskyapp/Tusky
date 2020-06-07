package com.keylesspalace.tusky.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.*
import com.keylesspalace.tusky.EmojiPreference

class PreferenceParent(
        val context: Context,
        val addPref: (pref: Preference) -> Unit
)

inline fun PreferenceParent.preference(builder: Preference.() -> Unit): Preference {
    val pref = Preference(context).also(builder)
    addPref(pref)
    return pref
}

inline fun PreferenceParent.listPreference(builder: ListPreference.() -> Unit): ListPreference {
    val pref = ListPreference(context).also(builder)
    addPref(pref)
    return pref
}

inline fun PreferenceParent.emojiPreference(builder: EmojiPreference.() -> Unit): EmojiPreference {
    val pref = EmojiPreference(context).also(builder)
    addPref(pref)
    return pref
}

inline fun PreferenceParent.switchPreference(
        builder: SwitchPreference.() -> Unit
): SwitchPreference {
    val pref = SwitchPreference(context).also(builder)
    addPref(pref)
    return pref
}

inline fun PreferenceParent.editTextPreference(
        builder: EditTextPreference.() -> Unit
): EditTextPreference {
    val pref = EditTextPreference(context).also(builder)
    addPref(pref)
    return pref
}

inline fun PreferenceParent.checkBoxPreference(
        builder: CheckBoxPreference.() -> Unit
): CheckBoxPreference {
    val pref = CheckBoxPreference(context).also(builder)
    addPref(pref)
    return pref
}

inline fun PreferenceParent.preferenceCategory(
        @StringRes title: Int,
        builder: PreferenceParent.(PreferenceCategory) -> Unit
) {
    val category = PreferenceCategory(context)
    addPref(category)
    category.setTitle(title)
    val newParent = PreferenceParent(context) { category.addPreference(it) }
    builder(newParent, category)
}

inline fun makePreferenceScreen(fragment: PreferenceFragmentCompat,
                                builder: PreferenceParent.() -> Unit): PreferenceScreen {
    val context = fragment.requireContext()
    val screen = fragment.preferenceManager.createPreferenceScreen(context)
    val parent = PreferenceParent(context) { screen.addPreference(it) }
    builder(parent)
    return screen
}