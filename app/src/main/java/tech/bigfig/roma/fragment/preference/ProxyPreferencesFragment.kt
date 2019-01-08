/* Copyright 2018 Conny Duck

 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.fragment.preference

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import tech.bigfig.roma.R

class ProxyPreferencesFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var pendingRestart = false

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.http_proxy_preferences)

        sharedPreferences = preferenceManager.sharedPreferences

    }

    override fun onResume() {
        super.onResume()

        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        updateSummary("httpProxyServer")
        updateSummary("httpProxyPort")
    }

    override fun onPause() {
        super.onPause()

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)

        if (pendingRestart) {
            pendingRestart = false
            System.exit(0)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        updateSummary (key)
    }

    private fun updateSummary(key: String) {
        when (key) {
            "httpProxyServer", "httpProxyPort" -> {
                val editTextPreference = findPreference(key) as EditTextPreference
                editTextPreference.summary = editTextPreference.text
            }
        }
    }

    companion object {

        fun newInstance(): ProxyPreferencesFragment {
            return ProxyPreferencesFragment()
        }
    }
}
