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

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.FiltersActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.TabPreferenceActivity
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.accountlist.AccountListActivity
import com.keylesspalace.tusky.components.followedtags.FollowedTagsActivity
import com.keylesspalace.tusky.components.instancemute.InstanceListActivity
import com.keylesspalace.tusky.components.login.LoginActivity
import com.keylesspalace.tusky.components.notifications.currentAccountNeedsMigration
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.settings.AccountPreferenceHandler
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.settings.listPreference
import com.keylesspalace.tusky.settings.makePreferenceScreen
import com.keylesspalace.tusky.settings.preference
import com.keylesspalace.tusky.settings.preferenceCategory
import com.keylesspalace.tusky.settings.switchPreference
import com.keylesspalace.tusky.util.getInitialLanguages
import com.keylesspalace.tusky.util.getLocaleList
import com.keylesspalace.tusky.util.getTuskyDisplayName
import com.keylesspalace.tusky.util.makeIcon
import com.keylesspalace.tusky.util.unsafeLazy
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeRes
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject

class AccountPreferencesFragment : PreferenceFragmentCompat(), Injectable {
    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    private val iconSize by unsafeLazy { resources.getDimensionPixelSize(R.dimen.preference_icon_size) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()
        makePreferenceScreen {
            preference {
                setTitle(R.string.pref_title_edit_notification_settings)
                icon = IconicsDrawable(context, GoogleMaterial.Icon.gmd_notifications).apply {
                    sizeRes = R.dimen.preference_icon_size
                    colorInt = MaterialColors.getColor(context, R.attr.iconColor, Color.BLACK)
                }
                setOnPreferenceClickListener {
                    openNotificationSystemPrefs()
                    true
                }
            }

            preference {
                setTitle(R.string.title_tab_preferences)
                setIcon(R.drawable.ic_tabs)
                setOnPreferenceClickListener {
                    val intent = Intent(context, TabPreferenceActivity::class.java)
                    activity?.startActivity(intent)
                    activity?.overridePendingTransition(
                        R.anim.slide_from_right,
                        R.anim.slide_to_left
                    )
                    true
                }
            }

            preference {
                setTitle(R.string.title_followed_hashtags)
                setIcon(R.drawable.ic_hashtag)
                setOnPreferenceClickListener {
                    val intent = Intent(context, FollowedTagsActivity::class.java)
                    activity?.startActivity(intent)
                    activity?.overridePendingTransition(
                        R.anim.slide_from_right,
                        R.anim.slide_to_left
                    )
                    true
                }
            }

            preference {
                setTitle(R.string.action_view_mutes)
                setIcon(R.drawable.ic_mute_24dp)
                setOnPreferenceClickListener {
                    val intent = Intent(context, AccountListActivity::class.java)
                    intent.putExtra("type", AccountListActivity.Type.MUTES)
                    activity?.startActivity(intent)
                    activity?.overridePendingTransition(
                        R.anim.slide_from_right,
                        R.anim.slide_to_left
                    )
                    true
                }
            }

            preference {
                setTitle(R.string.action_view_blocks)
                icon = IconicsDrawable(context, GoogleMaterial.Icon.gmd_block).apply {
                    sizeRes = R.dimen.preference_icon_size
                    colorInt = MaterialColors.getColor(context, R.attr.iconColor, Color.BLACK)
                }
                setOnPreferenceClickListener {
                    val intent = Intent(context, AccountListActivity::class.java)
                    intent.putExtra("type", AccountListActivity.Type.BLOCKS)
                    activity?.startActivity(intent)
                    activity?.overridePendingTransition(
                        R.anim.slide_from_right,
                        R.anim.slide_to_left
                    )
                    true
                }
            }

            preference {
                setTitle(R.string.title_domain_mutes)
                setIcon(R.drawable.ic_mute_24dp)
                setOnPreferenceClickListener {
                    val intent = Intent(context, InstanceListActivity::class.java)
                    activity?.startActivity(intent)
                    activity?.overridePendingTransition(
                        R.anim.slide_from_right,
                        R.anim.slide_to_left
                    )
                    true
                }
            }

            if (currentAccountNeedsMigration(accountManager)) {
                preference {
                    setTitle(R.string.title_migration_relogin)
                    setIcon(R.drawable.ic_logout)
                    setOnPreferenceClickListener {
                        val intent = LoginActivity.getIntent(context, LoginActivity.MODE_MIGRATION)
                        (activity as BaseActivity).startActivityWithSlideInAnimation(intent)
                        true
                    }
                }
            }

            preferenceCategory(R.string.pref_publishing) {
                listPreference {
                    setTitle(R.string.pref_default_post_privacy)
                    setEntries(R.array.post_privacy_names)
                    setEntryValues(R.array.post_privacy_values)
                    key = PrefKeys.DEFAULT_POST_PRIVACY
                    setSummaryProvider { entry }
                    val visibility = accountManager.activeAccount?.defaultPostPrivacy ?: Status.Visibility.PUBLIC
                    value = visibility.serverString()
                    setIcon(getIconForVisibility(visibility))
                    setOnPreferenceChangeListener { _, newValue ->
                        setIcon(getIconForVisibility(Status.Visibility.byString(newValue as String)))
                        syncWithServer(visibility = newValue)
                        eventHub.dispatch(PreferenceChangedEvent(key))
                        true
                    }
                }

                listPreference {
                    val locales = getLocaleList(getInitialLanguages(null, accountManager.activeAccount))
                    setTitle(R.string.pref_default_post_language)
                    // Explicitly add "System default" to the start of the list
                    entries = (
                        listOf(context.getString(R.string.system_default)) + locales.map {
                            it.getTuskyDisplayName(context)
                        }
                        ).toTypedArray()
                    entryValues = (listOf("") + locales.map { it.language }).toTypedArray()
                    key = PrefKeys.DEFAULT_POST_LANGUAGE
                    icon = makeIcon(requireContext(), GoogleMaterial.Icon.gmd_translate, iconSize)
                    value = accountManager.activeAccount?.defaultPostLanguage.orEmpty()
                    isPersistent = false // This will be entirely server-driven
                    setSummaryProvider { entry }

                    setOnPreferenceChangeListener { _, newValue ->
                        syncWithServer(language = (newValue as String))
                        eventHub.dispatch(PreferenceChangedEvent(key))
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_default_media_sensitivity)
                    setIcon(R.drawable.ic_eye_24dp)
                    key = PrefKeys.DEFAULT_MEDIA_SENSITIVITY
                    isSingleLineTitle = false
                    val sensitivity = accountManager.activeAccount?.defaultMediaSensitivity ?: false
                    setDefaultValue(sensitivity)
                    setIcon(getIconForSensitivity(sensitivity))
                    setOnPreferenceChangeListener { _, newValue ->
                        setIcon(getIconForSensitivity(newValue as Boolean))
                        syncWithServer(sensitive = newValue)
                        eventHub.dispatch(PreferenceChangedEvent(key))
                        true
                    }
                }
            }

            preferenceCategory(R.string.pref_title_timelines) {
                // TODO having no activeAccount in this fragment does not really make sense, enforce it?
                //   All other locations here make it optional, however.
                val accountPreferenceHandler = AccountPreferenceHandler(accountManager.activeAccount!!, accountManager, eventHub)

                switchPreference {
                    key = PrefKeys.MEDIA_PREVIEW_ENABLED
                    setTitle(R.string.pref_title_show_media_preview)
                    isSingleLineTitle = false
                    preferenceDataStore = accountPreferenceHandler
                }

                switchPreference {
                    key = PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA
                    setTitle(R.string.pref_title_alway_show_sensitive_media)
                    isSingleLineTitle = false
                    preferenceDataStore = accountPreferenceHandler
                }

                switchPreference {
                    key = PrefKeys.ALWAYS_OPEN_SPOILER
                    setTitle(R.string.pref_title_alway_open_spoiler)
                    isSingleLineTitle = false
                    preferenceDataStore = accountPreferenceHandler
                }
            }

            preferenceCategory(R.string.pref_title_timeline_filters) {
                preference {
                    setTitle(R.string.pref_title_public_filter_keywords)
                    setOnPreferenceClickListener {
                        launchFilterActivity(Filter.PUBLIC, R.string.pref_title_public_filter_keywords)
                        true
                    }
                }

                preference {
                    setTitle(R.string.title_notifications)
                    setOnPreferenceClickListener {
                        launchFilterActivity(Filter.NOTIFICATIONS, R.string.title_notifications)
                        true
                    }
                }

                preference {
                    setTitle(R.string.title_home)
                    setOnPreferenceClickListener {
                        launchFilterActivity(Filter.HOME, R.string.title_home)
                        true
                    }
                }

                preference {
                    setTitle(R.string.pref_title_thread_filter_keywords)
                    setOnPreferenceClickListener {
                        launchFilterActivity(Filter.THREAD, R.string.pref_title_thread_filter_keywords)
                        true
                    }
                }

                preference {
                    setTitle(R.string.title_accounts)
                    setOnPreferenceClickListener {
                        launchFilterActivity(Filter.ACCOUNT, R.string.title_accounts)
                        true
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.action_view_account_preferences)
    }

    private fun openNotificationSystemPrefs() {
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
    }

    private fun syncWithServer(visibility: String? = null, sensitive: Boolean? = null, language: String? = null) {
        // TODO these could also be "datastore backed" preferences (a ServerPreferenceDataStore); follow-up of issue #3204

        mastodonApi.accountUpdateSource(visibility, sensitive, language)
            .enqueue(object : Callback<Account> {
                override fun onResponse(call: Call<Account>, response: Response<Account>) {
                    val account = response.body()
                    if (response.isSuccessful && account != null) {

                        accountManager.activeAccount?.let {
                            it.defaultPostPrivacy = account.source?.privacy
                                ?: Status.Visibility.PUBLIC
                            it.defaultMediaSensitivity = account.source?.sensitive ?: false
                            it.defaultPostLanguage = language.orEmpty()
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
        view?.let { view ->
            Snackbar.make(view, R.string.pref_failed_to_sync, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry) { syncWithServer(visibility, sensitive) }
                .show()
        }
    }

    @DrawableRes
    private fun getIconForVisibility(visibility: Status.Visibility): Int {
        return when (visibility) {
            Status.Visibility.PRIVATE -> R.drawable.ic_lock_outline_24dp

            Status.Visibility.UNLISTED -> R.drawable.ic_lock_open_24dp

            else -> R.drawable.ic_public_24dp
        }
    }

    @DrawableRes
    private fun getIconForSensitivity(sensitive: Boolean): Int {
        return if (sensitive) {
            R.drawable.ic_hide_media_24dp
        } else {
            R.drawable.ic_eye_24dp
        }
    }

    private fun launchFilterActivity(filterContext: String, titleResource: Int) {
        val intent = Intent(context, FiltersActivity::class.java)
        intent.putExtra(FiltersActivity.FILTERS_CONTEXT, filterContext)
        intent.putExtra(FiltersActivity.FILTERS_TITLE, getString(titleResource))
        activity?.startActivity(intent)
        activity?.overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
    }

    companion object {
        fun newInstance() = AccountPreferencesFragment()
    }
}
