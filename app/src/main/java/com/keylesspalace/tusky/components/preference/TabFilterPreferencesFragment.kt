/* Copyright 2018 Conny Duck
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.components.preference

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.settings.makePreferenceScreen
import com.keylesspalace.tusky.settings.preferenceCategory
import com.keylesspalace.tusky.settings.switchPreference

class TabFilterPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        makePreferenceScreen {
            preferenceCategory(R.string.title_home) { category ->
                category.isIconSpaceReserved = false

                switchPreference {
                    setTitle(R.string.pref_title_show_boosts)
                    key = PrefKeys.TAB_FILTER_HOME_BOOSTS
                    setDefaultValue(true)
                    isIconSpaceReserved = false
                }

                switchPreference {
                    setTitle(R.string.pref_title_show_replies)
                    key = PrefKeys.TAB_FILTER_HOME_REPLIES
                    setDefaultValue(true)
                    isIconSpaceReserved = false
                }

                switchPreference {
                    setTitle(R.string.pref_title_show_self_boosts)
                    setSummary(R.string.pref_title_show_self_boosts_description)
                    key = PrefKeys.TAB_SHOW_HOME_SELF_BOOSTS
                    setDefaultValue(true)
                    isIconSpaceReserved = false
                }.apply { dependency = PrefKeys.TAB_FILTER_HOME_BOOSTS }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.pref_title_post_tabs)
    }

    companion object {
        fun newInstance(): TabFilterPreferencesFragment {
            return TabFilterPreferencesFragment()
        }
    }
}
