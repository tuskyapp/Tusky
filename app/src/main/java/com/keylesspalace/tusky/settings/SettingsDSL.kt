package com.keylesspalace.tusky.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.*
import com.keylesspalace.tusky.EmojiPreference

class PreferenceParent(
        val context: Context,
        val addPref: (pref: Preference) -> Unit
)

inline fun PreferenceParent.preference(builder: Preference.() -> Unit) {
    addPref(Preference(context).also(builder))
}

inline fun PreferenceParent.listPreference(builder: ListPreference.() -> Unit) {
    addPref(ListPreference(context).also(builder))
}

inline fun PreferenceParent.emojiPreference(builder: EmojiPreference.() -> Unit) {
    addPref(EmojiPreference(context).also(builder))
}

inline fun PreferenceParent.switchPreference(builder: SwitchPreference.() -> Unit) {
    addPref(SwitchPreference(context).also(builder))
}

inline fun PreferenceParent.preferenceCategory(
        @StringRes title: Int,
        builder: PreferenceParent.() -> Unit
) {
    val category = PreferenceCategory(context)
    addPref(category)
    category.setTitle(title)
    val newParent = PreferenceParent(context) { category.addPreference(it) }
    builder(newParent)
}

inline fun makePreferenceScreen(fragment: PreferenceFragmentCompat,
                                builder: PreferenceParent.() -> Unit): PreferenceScreen {
    val context = fragment.requireContext()
    val screen = fragment.preferenceManager.createPreferenceScreen(context)
    val parent = PreferenceParent(context) { screen.addPreference(it) }
    builder(parent)
    return screen
}