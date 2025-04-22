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
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.TabPreferenceActivity
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.accountlist.AccountListActivity
import com.keylesspalace.tusky.components.domainblocks.DomainBlocksActivity
import com.keylesspalace.tusky.components.filters.FiltersActivity
import com.keylesspalace.tusky.components.followedtags.FollowedTagsActivity
import com.keylesspalace.tusky.components.preference.notificationpolicies.NotificationPoliciesActivity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.settings.AccountPreferenceDataStore
import com.keylesspalace.tusky.settings.DefaultReplyVisibility
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.settings.listPreference
import com.keylesspalace.tusky.settings.makePreferenceScreen
import com.keylesspalace.tusky.settings.preference
import com.keylesspalace.tusky.settings.preferenceCategory
import com.keylesspalace.tusky.settings.switchPreference
import com.keylesspalace.tusky.util.getInitialLanguages
import com.keylesspalace.tusky.util.getLocaleList
import com.keylesspalace.tusky.util.getTuskyDisplayName
import com.keylesspalace.tusky.util.icon
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AccountPreferencesFragment : BasePreferencesFragment() {
    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var accountPreferenceDataStore: AccountPreferenceDataStore

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()
        makePreferenceScreen {
            preference {
                setTitle(R.string.pref_title_edit_notification_settings)
                icon = icon(R.drawable.ic_notifications_24dp)
                setOnPreferenceClickListener {
                    openNotificationSystemPrefs()
                    true
                }
            }

            preference {
                setTitle(R.string.title_tab_preferences)
                icon = icon(R.drawable.ic_tabs_24dp)
                setOnPreferenceClickListener {
                    val intent = Intent(context, TabPreferenceActivity::class.java)
                    activity?.startActivityWithSlideInAnimation(intent)
                    true
                }
            }

            preference {
                setTitle(R.string.title_followed_hashtags)
                icon = icon(R.drawable.ic_tag_24dp)
                setOnPreferenceClickListener {
                    val intent = Intent(context, FollowedTagsActivity::class.java)
                    activity?.startActivityWithSlideInAnimation(intent)
                    true
                }
            }

            preference {
                setTitle(R.string.action_view_mutes)
                icon = icon(R.drawable.ic_volume_off_24dp)
                setOnPreferenceClickListener {
                    val intent = Intent(context, AccountListActivity::class.java)
                    intent.putExtra("type", AccountListActivity.Type.MUTES)
                    activity?.startActivityWithSlideInAnimation(intent)
                    true
                }
            }

            preference {
                setTitle(R.string.action_view_blocks)
                icon = icon(R.drawable.ic_block_24dp)
                setOnPreferenceClickListener {
                    val intent = Intent(context, AccountListActivity::class.java)
                    intent.putExtra("type", AccountListActivity.Type.BLOCKS)
                    activity?.startActivityWithSlideInAnimation(intent)
                    true
                }
            }

            preference {
                setTitle(R.string.title_domain_mutes)
                icon = icon(R.drawable.ic_volume_off_24dp)
                setOnPreferenceClickListener {
                    val intent = Intent(context, DomainBlocksActivity::class.java)
                    activity?.startActivityWithSlideInAnimation(intent)
                    true
                }
            }

            preference {
                setTitle(R.string.pref_title_timeline_filters)
                icon = icon(R.drawable.ic_filter_alt_24dp)
                setOnPreferenceClickListener {
                    launchFilterActivity()
                    true
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
                    value = visibility.stringValue
                    icon = getIconForVisibility(visibility)
                    isPersistent = false // its saved to the account and shouldn't be in shared preferences
                    setOnPreferenceChangeListener { _, newValue ->
                        icon = getIconForVisibility(Status.Visibility.fromStringValue(newValue as String))
                        if (accountManager.activeAccount?.defaultReplyPrivacy == DefaultReplyVisibility.MATCH_DEFAULT_POST_VISIBILITY) {
                            findPreference<ListPreference>(PrefKeys.DEFAULT_REPLY_PRIVACY)?.icon = icon
                        }
                        syncWithServer(visibility = newValue)
                        true
                    }
                }

                val activeAccount = accountManager.activeAccount
                if (activeAccount != null) {
                    listPreference {
                        setTitle(R.string.pref_default_reply_privacy)
                        setEntries(R.array.reply_privacy_names)
                        setEntryValues(R.array.reply_privacy_values)
                        key = PrefKeys.DEFAULT_REPLY_PRIVACY
                        setSummaryProvider { entry }
                        val visibility = activeAccount.defaultReplyPrivacy
                        value = visibility.stringValue
                        icon = getIconForVisibility(visibility.toVisibilityOr(activeAccount.defaultPostPrivacy))
                        isPersistent = false // its saved to the account and shouldn't be in shared preferences
                        setOnPreferenceChangeListener { _, newValue ->
                            val newVisibility = DefaultReplyVisibility.fromStringValue(newValue as String)

                            icon = getIconForVisibility(newVisibility.toVisibilityOr(activeAccount.defaultPostPrivacy))

                            viewLifecycleOwner.lifecycleScope.launch {
                                accountManager.updateAccount(activeAccount) { copy(defaultReplyPrivacy = newVisibility) }
                                eventHub.dispatch(PreferenceChangedEvent(key))
                            }
                            true
                        }
                    }
                    preference {
                        setSummary(R.string.pref_default_reply_privacy_explanation)
                        shouldDisableView = false
                        isEnabled = false
                    }
                }

                listPreference {
                    val locales =
                        getLocaleList(getInitialLanguages(null, accountManager.activeAccount))
                    setTitle(R.string.pref_default_post_language)
                    // Explicitly add "System default" to the start of the list
                    entries = (
                        listOf(context.getString(R.string.system_default)) + locales.map {
                            it.getTuskyDisplayName(context)
                        }
                        ).toTypedArray()
                    entryValues = (listOf("") + locales.map { it.language }).toTypedArray()
                    key = PrefKeys.DEFAULT_POST_LANGUAGE
                    icon = icon(R.drawable.ic_translate_24dp)
                    value = accountManager.activeAccount?.defaultPostLanguage.orEmpty()
                    isPersistent = false // This will be entirely server-driven
                    setSummaryProvider { entry }

                    setOnPreferenceChangeListener { _, newValue ->
                        syncWithServer(language = (newValue as String))
                        true
                    }
                }

                switchPreference {
                    setTitle(R.string.pref_default_media_sensitivity)
                    icon = icon(R.drawable.ic_visibility_24dp)
                    key = PrefKeys.DEFAULT_MEDIA_SENSITIVITY
                    val sensitivity = accountManager.activeAccount?.defaultMediaSensitivity == true
                    setDefaultValue(sensitivity)
                    icon = getIconForSensitivity(sensitivity)
                    setOnPreferenceChangeListener { _, newValue ->
                        icon = getIconForSensitivity(newValue as Boolean)
                        syncWithServer(sensitive = newValue)
                        true
                    }
                }
            }

            preferenceCategory(R.string.pref_title_timelines) {
                // TODO having no activeAccount in this fragment does not really make sense, enforce it?
                //   All other locations here make it optional, however.

                switchPreference {
                    key = PrefKeys.MEDIA_PREVIEW_ENABLED
                    setTitle(R.string.pref_title_show_media_preview)
                    preferenceDataStore = accountPreferenceDataStore
                }

                switchPreference {
                    key = PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA
                    setTitle(R.string.pref_title_alway_show_sensitive_media)
                    preferenceDataStore = accountPreferenceDataStore
                }

                switchPreference {
                    key = PrefKeys.ALWAYS_OPEN_SPOILER
                    setTitle(R.string.pref_title_alway_open_spoiler)
                    preferenceDataStore = accountPreferenceDataStore
                }
            }
            preferenceCategory(R.string.pref_title_per_timeline_preferences) {
                preference {
                    setTitle(R.string.pref_title_post_tabs)
                    fragment = TabFilterPreferencesFragment::class.qualifiedName
                }
            }
            preference {
                setTitle(R.string.notification_policies_title)
                setOnPreferenceClickListener {
                    activity?.let {
                        val intent = NotificationPoliciesActivity.newIntent(it)
                        it.startActivityWithSlideInAnimation(intent)
                    }
                    true
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
                val intent = PreferencesActivity.newIntent(
                    it,
                    PreferencesActivity.NOTIFICATION_PREFERENCES
                )
                it.startActivityWithSlideInAnimation(intent)
            }
        }
    }

    private fun syncWithServer(
        visibility: String? = null,
        sensitive: Boolean? = null,
        language: String? = null
    ) {
        // TODO these could also be "datastore backed" preferences (a ServerPreferenceDataStore); follow-up of issue #3204

        viewLifecycleOwner.lifecycleScope.launch {
            mastodonApi.accountUpdateSource(visibility, sensitive, language)
                .fold({ account: Account ->
                    accountManager.activeAccount?.let {
                        accountManager.updateAccount(it) {
                            copy(
                                defaultPostPrivacy = account.source?.privacy
                                    ?: Status.Visibility.PUBLIC,
                                defaultMediaSensitivity = account.source?.sensitive == true,
                                defaultPostLanguage = language.orEmpty()
                            )
                        }
                    }
                }, { t ->
                    Log.e("AccountPreferences", "failed updating settings on server", t)
                    showErrorSnackbar(visibility, sensitive)
                })
        }
    }

    private fun showErrorSnackbar(visibility: String?, sensitive: Boolean?) {
        view?.let { view ->
            Snackbar.make(view, R.string.pref_failed_to_sync, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry) { syncWithServer(visibility, sensitive) }
                .show()
        }
    }

    private fun getIconForVisibility(visibility: Status.Visibility): Drawable? {
        val iconRes = when (visibility) {
            Status.Visibility.PRIVATE -> R.drawable.ic_lock_24dp
            Status.Visibility.UNLISTED -> R.drawable.ic_lock_open_24dp
            Status.Visibility.DIRECT -> R.drawable.ic_mail_24dp
            else -> R.drawable.ic_public_24dp
        }
        return icon(iconRes)
    }

    private fun getIconForSensitivity(sensitive: Boolean): Drawable? = if (sensitive) {
        icon(R.drawable.ic_visibility_off_24dp)
    } else {
        icon(R.drawable.ic_visibility_24dp)
    }

    private fun launchFilterActivity() {
        val intent = Intent(context, FiltersActivity::class.java)
        (activity as? BaseActivity)?.startActivityWithSlideInAnimation(intent)
    }

    companion object {
        fun newInstance() = AccountPreferencesFragment()
    }
}
