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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.settings.PrefData
import com.keylesspalace.tusky.settings.PrefStore
import com.keylesspalace.tusky.settings.checkBoxPreference
import com.keylesspalace.tusky.settings.getBlocking
import com.keylesspalace.tusky.settings.makePreferenceScreen
import com.keylesspalace.tusky.settings.preferenceCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TabFilterPreferencesFragment : Fragment(), Injectable {
    @Inject
    lateinit var prefStore: PrefStore

    lateinit var prefs: PrefData
    private var updateTrigger: (() -> Unit)? = null

    private fun updatePrefs(updater: (PrefData) -> PrefData) {
        lifecycleScope.launch {
            prefStore.updateData(updater)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val viewRoot = ScrollView(inflater.context)
        val rootLayout = LinearLayout(inflater.context).apply {
            orientation = LinearLayout.VERTICAL
            viewRoot.addView(this)
        }

        prefs = prefStore.getBlocking()
        lifecycleScope.launch {
            prefStore.data.collect {
                prefs = it
                // trigger update?
                withContext(Dispatchers.Main) {
                    updateTrigger?.invoke()
                }
            }
        }

        updateTrigger = makePreferenceScreen(rootLayout) {
            preferenceCategory(R.string.title_home) {
                checkBoxPreference(
                    getString(R.string.pref_title_show_boosts),
                    { prefs.tabFilterHomeBoosts }
                ) {
                    updatePrefs { data -> data.copy(tabFilterHomeBoosts = it) }
                }
                checkBoxPreference(
                    getString(R.string.pref_title_show_replies),
                    { prefs.tabFilterHomeReplies }
                ) {
                    updatePrefs { data -> data.copy(tabFilterHomeReplies = it) }
                }
            }
        }

        return viewRoot
    }

    companion object {
        fun newInstance(): TabFilterPreferencesFragment {
            return TabFilterPreferencesFragment()
        }
    }
}
