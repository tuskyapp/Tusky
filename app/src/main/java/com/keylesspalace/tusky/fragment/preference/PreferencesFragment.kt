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
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.keylesspalace.tusky.PreferencesActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.util.getNonNullString
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable

class PreferencesFragment : PreferenceFragmentCompat() {

    private val iconSize by lazy {resources.getDimensionPixelSize(R.dimen.preference_icon_size)}

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        addPreferencesFromResource(R.xml.preferences)

        val themePreference: Preference = findPreference("appTheme")
        themePreference.icon = IconicsDrawable(themePreference.context, GoogleMaterial.Icon.gmd_palette).sizePx(iconSize).color(ThemeUtils.getColor(themePreference.context, R.attr.toolbar_icon_tint))

        val emojiPreference: Preference = findPreference("emojiCompat")
        emojiPreference.icon = IconicsDrawable(emojiPreference.context, GoogleMaterial.Icon.gmd_sentiment_satisfied).sizePx(iconSize).color(ThemeUtils.getColor(emojiPreference.context, R.attr.toolbar_icon_tint))

        val textSizePreference: Preference = findPreference("statusTextSize")
        textSizePreference.icon = IconicsDrawable(textSizePreference.context, GoogleMaterial.Icon.gmd_format_size).sizePx(iconSize).color(ThemeUtils.getColor(textSizePreference.context, R.attr.toolbar_icon_tint))

        val timelineFilterPreferences: Preference = findPreference("timelineFilterPreferences")
        timelineFilterPreferences.setOnPreferenceClickListener {
            activity?.let { activity ->
                val intent = PreferencesActivity.newIntent(activity, PreferencesActivity.TAB_FILTER_PREFERENCES)
                activity.startActivity(intent)
                activity.overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
            }
            true
        }

        val httpProxyPreferences: Preference = findPreference("httpProxyPreferences")
        httpProxyPreferences.setOnPreferenceClickListener {
            activity?.let { activity ->
                val intent = PreferencesActivity.newIntent(activity, PreferencesActivity.PROXY_PREFERENCES)
                activity.startActivity(intent)
                activity.overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
            }
            true
        }

        val languagePreference: Preference = findPreference("language")
        languagePreference.icon = IconicsDrawable(languagePreference.context, GoogleMaterial.Icon.gmd_translate).sizePx(iconSize).color(ThemeUtils.getColor(languagePreference.context, R.attr.toolbar_icon_tint))
    }

    override fun onResume() {
        super.onResume()
        updateHttpProxySummary()
    }

    private fun updateHttpProxySummary() {

        val httpProxyPref: Preference = findPreference("httpProxyPreferences")

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
