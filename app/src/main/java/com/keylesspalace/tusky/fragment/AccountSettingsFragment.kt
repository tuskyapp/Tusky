/* Copyright 2017 Andrew Dawson
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

package com.keylesspalace.tusky.fragment

import android.graphics.drawable.Drawable
import android.opengl.Visibility
import android.os.Bundle
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat

import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.util.ThemeUtils
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable

class AccountSettingsFragment : PreferenceFragmentCompat() {


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.account_settings)

        findPreference("notificationPreferences2").icon = IconicsDrawable(context, GoogleMaterial.Icon.gmd_block).sizeDp(24).color(ThemeUtils.getColor(context, R.attr.toolbar_icon_tint))


        findPreference("defaultPostPrivacy").onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            preference.icon = getIconForVisibility(Status.Visibility.byString(newValue as String))
            true
        }


    }


    private fun getIconForVisibility(visibility: Status.Visibility): Drawable? {

        val drawable = when (visibility) {
            Status.Visibility.PRIVATE -> context?.getDrawable(R.drawable.ic_lock_outline_24dp)

            Status.Visibility.UNLISTED -> context?.getDrawable(R.drawable.ic_lock_open_24dp)

            else -> context?.getDrawable(R.drawable.ic_public_24dp)
        }

        ThemeUtils.setDrawableTint(context, drawable, R.attr.toolbar_icon_tint)
        return drawable
    }


    companion object {
        fun newInstance(): AccountSettingsFragment {
            return AccountSettingsFragment()
        }
    }

}
