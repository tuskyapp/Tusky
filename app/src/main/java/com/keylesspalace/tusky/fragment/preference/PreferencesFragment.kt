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

package com.keylesspalace.tusky.fragment.preference

import android.os.Bundle
import android.support.v7.preference.PreferenceFragmentCompat
import com.keylesspalace.tusky.PreferencesActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.util.getNonNullString

class PreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        addPreferencesFromResource(R.xml.preferences)

        val timelineFilterPreferences = findPreference("timelineFilterPreferences")
        timelineFilterPreferences.setOnPreferenceClickListener { _ ->
            activity?.let {
                val intent = PreferencesActivity.newIntent(it, PreferencesActivity.TAB_FILTER_PREFERENCES)
                it.startActivity(intent)
                it.overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
            }
            true
        }

        val httpProxyPreferences = findPreference("httpProxyPreferences")
        httpProxyPreferences.setOnPreferenceClickListener { _ ->
            activity?.let {
                val intent = PreferencesActivity.newIntent(it, PreferencesActivity.PROXY_PREFERENCES)
                it.startActivity(intent)
                it.overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
            }
            true
        }

    }

    override fun onResume() {
        super.onResume()
        updateHttpProxySummary()
    }

    private fun updateHttpProxySummary() {

        val httpProxyPref = findPreference("httpProxyPreferences")

        val sharedPreferences = preferenceManager.sharedPreferences

            val httpProxyEnabled = sharedPreferences.getBoolean("httpProxyEnabled", false)

            val httpServer = sharedPreferences.getNonNullString("httpProxyServer", "")

            try {
                val httpPort = sharedPreferences.getNonNullString("httpProxyPort", "-1").toInt()

                if (httpProxyEnabled && httpServer.isNotBlank() && httpPort > 0 && httpPort < 65535) {
                    httpProxyPref.summary = "$httpServer:$httpPort"
                    return
                }
            } catch (e: NumberFormatException) {
                // user has entered wrong port, fall back to empty summary
            }

            httpProxyPref.summary = ""

    }

    companion object {
        fun newInstance(): PreferencesFragment {
            return PreferencesFragment()
        }
    }
}
