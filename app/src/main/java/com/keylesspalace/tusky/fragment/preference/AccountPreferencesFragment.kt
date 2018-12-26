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

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.preference.SwitchPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import android.util.Log
import android.view.View
import com.keylesspalace.tusky.*
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.ThemeUtils
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject


class AccountPreferencesFragment : PreferenceFragmentCompat(),
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener,
        Injectable {

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    private lateinit var notificationPreference: Preference
    private lateinit var tabPreference: Preference
    private lateinit var mutedUsersPreference: Preference
    private lateinit var blockedUsersPreference: Preference

    private lateinit var defaultPostPrivacyPreference: ListPreference
    private lateinit var defaultMediaSensitivityPreference: SwitchPreference
    private lateinit var alwaysShowSensitiveMediaPreference: SwitchPreference
    private lateinit var mediaPreviewEnabledPreference: SwitchPreference

    private val iconSize by lazy {resources.getDimensionPixelSize(R.dimen.preference_icon_size)}

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.account_preferences)

        notificationPreference = findPreference("notificationPreference")
        tabPreference = findPreference("tabPreference")
        mutedUsersPreference = findPreference("mutedUsersPreference")
        blockedUsersPreference = findPreference("blockedUsersPreference")
        defaultPostPrivacyPreference = findPreference("defaultPostPrivacy") as ListPreference
        defaultMediaSensitivityPreference = findPreference("defaultMediaSensitivity") as SwitchPreference
        mediaPreviewEnabledPreference = findPreference("mediaPreviewEnabled") as SwitchPreference
        alwaysShowSensitiveMediaPreference = findPreference("alwaysShowSensitiveMedia") as SwitchPreference

        notificationPreference.icon = IconicsDrawable(context, GoogleMaterial.Icon.gmd_notifications).sizePx(iconSize).color(ThemeUtils.getColor(context, R.attr.toolbar_icon_tint))
        mutedUsersPreference.icon = getTintedIcon(R.drawable.ic_mute_24dp)
        blockedUsersPreference.icon = IconicsDrawable(context, GoogleMaterial.Icon.gmd_block).sizePx(iconSize).color(ThemeUtils.getColor(context, R.attr.toolbar_icon_tint))

        notificationPreference.onPreferenceClickListener = this
        tabPreference.onPreferenceClickListener = this
        mutedUsersPreference.onPreferenceClickListener = this
        blockedUsersPreference.onPreferenceClickListener = this

        defaultPostPrivacyPreference.onPreferenceChangeListener = this
        defaultMediaSensitivityPreference.onPreferenceChangeListener = this
        mediaPreviewEnabledPreference.onPreferenceChangeListener = this
        alwaysShowSensitiveMediaPreference.onPreferenceChangeListener = this

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        accountManager.activeAccount?.let {

            defaultPostPrivacyPreference.value = it.defaultPostPrivacy.serverString()
            defaultPostPrivacyPreference.icon = getIconForVisibility(it.defaultPostPrivacy)

            defaultMediaSensitivityPreference.isChecked = it.defaultMediaSensitivity
            defaultMediaSensitivityPreference.icon = getIconForSensitivity(it.defaultMediaSensitivity)

            mediaPreviewEnabledPreference.isChecked = it.mediaPreviewEnabled
            alwaysShowSensitiveMediaPreference.isChecked = it.alwaysShowSensitiveMedia

        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        when(preference) {
            defaultPostPrivacyPreference -> {
                preference.icon = getIconForVisibility(Status.Visibility.byString(newValue as String))
                syncWithServer(visibility = newValue)
            }
            defaultMediaSensitivityPreference -> {
                preference.icon = getIconForSensitivity(newValue as Boolean)
                syncWithServer(sensitive = newValue)
            }
            mediaPreviewEnabledPreference -> {
                accountManager.activeAccount?.let {
                    it.mediaPreviewEnabled = newValue as Boolean
                    accountManager.saveAccount(it)
                }
            }
            alwaysShowSensitiveMediaPreference -> {
                accountManager.activeAccount?.let {
                    it.alwaysShowSensitiveMedia = newValue as Boolean
                    accountManager.saveAccount(it)
                }
            }
        }

        eventHub.dispatch(PreferenceChangedEvent(preference.key))

        return true
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
                        val intent = PreferencesActivity.newIntent(it, PreferencesActivity.NOTIFICATION_PREFERENCES)
                        it.startActivity(intent)
                        it.overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
                    }

                }
                return true
            }
            tabPreference -> {
                val intent = Intent(context, TabPreferenceActivity::class.java)
                activity?.startActivity(intent)
                activity?.overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
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

    private fun syncWithServer(visibility: String? = null, sensitive: Boolean? = null) {
        mastodonApi.accountUpdateSource(visibility, sensitive)
                .enqueue(object: Callback<Account>{
                    override fun onResponse(call: Call<Account>, response: Response<Account>) {
                        val account = response.body()
                        if(response.isSuccessful && account != null) {

                            accountManager.activeAccount?.let {
                                it.defaultPostPrivacy = account.source?.privacy ?: Status.Visibility.PUBLIC
                                it.defaultMediaSensitivity = account.source?.sensitive ?: false
                                accountManager.saveAccount(it)
                            }
                        } else {
                            Log.e("AccountPreferences", "failed updating settings on server")
                            showErrorSnackbar(visibility, sensitive)
                        }
                    }

                    override fun onFailure(call: Call<Account>, t: Throwable) {
                        Log.e("AccountPreferences", "failed updating settings on server", t)
                        showErrorSnackbar(visibility, sensitive)
                    }

                })
    }

    private fun showErrorSnackbar(visibility: String?, sensitive: Boolean?) {
        view?.let {view ->
            Snackbar.make(view, R.string.pref_failed_to_sync, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_retry) { syncWithServer( visibility, sensitive)}
                    .show()
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
