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

package com.keylesspalace.tusky.fragment

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat

import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.util.ThemeUtils
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.keylesspalace.tusky.AccountListActivity
import com.keylesspalace.tusky.AccountPreferencesActivity
import com.keylesspalace.tusky.BuildConfig


class AccountPreferencesFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private lateinit var notificationPreference: Preference
    private lateinit var mutedUsersPreference: Preference
    private lateinit var blockedUsersPreference: Preference

    private lateinit var defaultPostPrivacyPreference: Preference
    private lateinit var defaultMediaSensitivityPreference: Preference


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.account_preferences)

        notificationPreference = findPreference("notificationPreference")
        mutedUsersPreference = findPreference("mutedUsersPreference")
        blockedUsersPreference = findPreference("blockedUsersPreference")
        defaultPostPrivacyPreference = findPreference("defaultPostPrivacy")
        defaultMediaSensitivityPreference = findPreference("defaultMediaSensitivity")

        notificationPreference.icon = IconicsDrawable(context, GoogleMaterial.Icon.gmd_notifications).sizeDp(24).color(ThemeUtils.getColor(context, R.attr.toolbar_icon_tint))
        mutedUsersPreference.icon = getTintedIcon(R.drawable.ic_mute_24dp)
        blockedUsersPreference.icon = IconicsDrawable(context, GoogleMaterial.Icon.gmd_block).sizeDp(24).color(ThemeUtils.getColor(context, R.attr.toolbar_icon_tint))
        defaultPostPrivacyPreference.icon = getIconForVisibility(Status.Visibility.byString("public"))
        defaultMediaSensitivityPreference.icon = getIconForSensitivity(false)

        notificationPreference.onPreferenceClickListener = this
        mutedUsersPreference.onPreferenceClickListener = this
        blockedUsersPreference.onPreferenceClickListener = this

        defaultPostPrivacyPreference.onPreferenceChangeListener = this
        defaultMediaSensitivityPreference.onPreferenceChangeListener = this

    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
        when(preference) {
            defaultPostPrivacyPreference -> {
                preference.icon = getIconForVisibility(Status.Visibility.byString(newValue as String))
                return true
            }
            defaultMediaSensitivityPreference -> {
                preference.icon = getIconForSensitivity(newValue as Boolean)
                return true
            }
        }
        return false
    }

    override fun onPreferenceClick(preference: Preference): Boolean {

        when(preference) {
            notificationPreference -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent()
                    intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    intent.putExtra("android.provider.extra.APP_PACKAGE", BuildConfig.APPLICATION_ID)
                    startActivity(intent)
                } else {
                    activity?.let {
                        val intent = AccountPreferencesActivity.newIntent(it, true)
                        it.startActivity(intent)
                        it.overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
                    }

                }
                return true
            }
            mutedUsersPreference -> {
                val intent = Intent(context, AccountListActivity::class.java)
                intent.putExtra("type", AccountListActivity.Type.MUTES)
                activity?.startActivity(intent)
                activity?.overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
                return true
            }
            blockedUsersPreference -> {
                val intent = Intent(context, AccountListActivity::class.java)
                intent.putExtra("type", AccountListActivity.Type.BLOCKS)
                activity?.startActivity(intent)
                activity?.overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
                return true
            }

            else -> return false
        }

    }

    private fun getIconForVisibility(visibility: Status.Visibility): Drawable? {
        val drawableId = when (visibility) {
            Status.Visibility.PRIVATE -> R.drawable.ic_lock_outline_24dp

            Status.Visibility.UNLISTED -> R.drawable.ic_lock_open_24dp

            else -> R.drawable.ic_public_24dp
        }

        return getTintedIcon(drawableId)
    }

    private fun getIconForSensitivity(sensitive: Boolean): Drawable? {
        val drawableId = if (sensitive) {
            R.drawable.ic_hide_media_24dp
        } else {
            R.drawable.ic_eye_24dp
        }

        return getTintedIcon(drawableId)
    }

    private fun getTintedIcon(iconId: Int): Drawable? {
        val drawable = context?.getDrawable(iconId)
        ThemeUtils.setDrawableTint(context, drawable, R.attr.toolbar_icon_tint)
        return drawable
    }

    companion object {
        fun newInstance(): AccountPreferencesFragment {
            return AccountPreferencesFragment()
        }
    }

}
