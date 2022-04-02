package com.keylesspalace.tusky.settings

import android.content.Context
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.annotation.StringRes
import androidx.lifecycle.LifecycleOwner
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import de.c1710.filemojicompat_ui.views.picker.preference.EmojiPickerPreference

class PreferenceParent(
    val context: Context,
    val addPref: (pref: Preference) -> Unit
)

inline fun PreferenceParent.preference(builder: Preference.() -> Unit): Preference {
    val pref = Preference(context)
    builder(pref)
    addPref(pref)
    return pref
}

inline fun PreferenceParent.listPreference(builder: ListPreference.() -> Unit): ListPreference {
    val pref = ListPreference(context)
    builder(pref)
    addPref(pref)
    return pref
}

inline fun <A> PreferenceParent.emojiPreference(activity: A, builder: EmojiPickerPreference.() -> Unit): EmojiPickerPreference
    where A : Context, A : ActivityResultRegistryOwner, A : LifecycleOwner {
    val pref = EmojiPickerPreference.get(activity)
    builder(pref)
    addPref(pref)
    return pref
}

inline fun PreferenceParent.switchPreference(
    builder: SwitchPreference.() -> Unit
): SwitchPreference {
    val pref = SwitchPreference(context)
    builder(pref)
    addPref(pref)
    return pref
}

inline fun PreferenceParent.editTextPreference(
    builder: EditTextPreference.() -> Unit
): EditTextPreference {
    val pref = EditTextPreference(context)
    builder(pref)
    addPref(pref)
    return pref
}

inline fun PreferenceParent.checkBoxPreference(
    builder: CheckBoxPreference.() -> Unit
): CheckBoxPreference {
    val pref = CheckBoxPreference(context)
    builder(pref)
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

inline fun PreferenceFragmentCompat.makePreferenceScreen(
    builder: PreferenceParent.() -> Unit
): PreferenceScreen {
    val context = requireContext()
    val screen = preferenceManager.createPreferenceScreen(context)
    val parent = PreferenceParent(context) { screen.addPreference(it) }
    // For some functions (like dependencies) it's much easier for us if we attach screen first
    // and change it later
    preferenceScreen = screen
    builder(parent)
    return screen
}
