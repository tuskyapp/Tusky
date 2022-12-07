/* Copyright 2018 Conny Duck

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
import com.keylesspalace.tusky.settings.ProxyConfiguration
import com.keylesspalace.tusky.settings.makePreferenceScreen
import com.keylesspalace.tusky.settings.preferenceCategory
import com.keylesspalace.tusky.settings.switchPreference
import com.keylesspalace.tusky.settings.validatedEditTextPreference
import kotlin.system.exitProcess

class ProxyPreferencesFragment : PreferenceFragmentCompat() {
    private var pendingRestart = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        makePreferenceScreen {
            switchPreference {
                setTitle(R.string.pref_title_http_proxy_enable)
                isIconSpaceReserved = false
                key = PrefKeys.HTTP_PROXY_ENABLED
                setDefaultValue(false)
            }

            preferenceCategory { category ->
                category.dependency = PrefKeys.HTTP_PROXY_ENABLED
                category.isIconSpaceReserved = false

                validatedEditTextPreference(null, ProxyConfiguration::isValidHostname) {
                    setTitle(R.string.pref_title_http_proxy_server)
                    key = PrefKeys.HTTP_PROXY_SERVER
                    isIconSpaceReserved = false
                    setSummaryProvider { text }
                }

                val portErrorMessage = getString(
                    R.string.pref_title_http_proxy_port_message,
                    ProxyConfiguration.MIN_PROXY_PORT,
                    ProxyConfiguration.MAX_PROXY_PORT
                )

                validatedEditTextPreference(portErrorMessage, ProxyConfiguration::isValidProxyPort) {
                    setTitle(R.string.pref_title_http_proxy_port)
                    key = PrefKeys.HTTP_PROXY_PORT
                    isIconSpaceReserved = false
                    setSummaryProvider { text }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (pendingRestart) {
            pendingRestart = false
            exitProcess(0)
        }
    }

    companion object {
        fun newInstance(): ProxyPreferencesFragment {
            return ProxyPreferencesFragment()
        }
    }
}
