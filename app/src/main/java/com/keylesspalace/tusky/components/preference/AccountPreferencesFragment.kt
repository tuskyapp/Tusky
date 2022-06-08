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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.AccountListActivity
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.FiltersActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.TabPreferenceActivity
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.instancemute.InstanceListActivity
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.settings.PreferenceOption
import com.keylesspalace.tusky.settings.clickPreference
import com.keylesspalace.tusky.settings.listPreference
import com.keylesspalace.tusky.settings.makePreferenceScreen
import com.keylesspalace.tusky.settings.preferenceCategory
import com.keylesspalace.tusky.settings.switchPreference
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject

class AccountPreferencesFragment : Fragment(), Injectable {
    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    private var updateTrigger: (() -> Unit)? = null

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

        updateTrigger = makePreferenceScreen(rootLayout) {
            clickPreference(
                getString(R.string.pref_title_edit_notification_settings),
                { makeIcon(GoogleMaterial.Icon.gmd_notifications) }
            ) {
                openNotificationPrefs()
            }
            clickPreference(
                getString(R.string.title_tab_preferences),
                { makeIcon(R.drawable.ic_tabs) }
            ) {
                val activity = requireActivity() as BaseActivity
                val intent = Intent(context, TabPreferenceActivity::class.java)
                activity.startActivityWithSlideInAnimation(intent)
            }
            clickPreference(
                getString(R.string.action_view_mutes),
                { makeIcon(R.drawable.ic_mute_24dp) }
            ) {
                val activity = requireActivity() as BaseActivity
                val intent = AccountListActivity.newIntent(context, AccountListActivity.Type.MUTES)

                activity.startActivityWithSlideInAnimation(intent)
            }
            clickPreference(
                getString(R.string.action_view_blocks),
                { makeIcon(GoogleMaterial.Icon.gmd_block) }
            ) {
                val activity = requireActivity() as BaseActivity
                val intent = AccountListActivity.newIntent(context, AccountListActivity.Type.BLOCKS)

                activity.startActivityWithSlideInAnimation(intent)
            }

            clickPreference(
                getString(R.string.title_domain_mutes),
                { makeIcon(R.drawable.ic_mute_24dp) }
            ) {
                val activity = requireActivity() as BaseActivity
                val intent = Intent(context, InstanceListActivity::class.java)

                activity.startActivityWithSlideInAnimation(intent)
            }

            preferenceCategory(R.string.pref_publishing) {
                val privacyOptions = listOf(
                    "public" to R.string.post_privacy_public,
                    "unlisted" to R.string.post_privacy_unlisted,
                    "private" to R.string.post_privacy_followers_only
                ).map(::PreferenceOption)
                listPreference(
                    getString(R.string.pref_default_post_privacy),
                    privacyOptions,
                    { activeAccount.defaultPostPrivacy.serverString() },
                    { getIconForVisibility(activeAccount.defaultPostPrivacy) }
                ) { newValue ->
                    syncWithServer(visibility = newValue)
                    eventHub.dispatch(PreferenceChangedEvent(PrefKeys.DEFAULT_POST_PRIVACY))
                }

                switchPreference(
                    getString(R.string.pref_default_media_sensitivity),
                    { activeAccount.defaultMediaSensitivity },
                    { getIconForSensitivity(activeAccount.defaultMediaSensitivity) }
                ) {
                    updateAccount { account -> account.mediaPreviewEnabled = it }
                    eventHub.dispatch(PreferenceChangedEvent(PrefKeys.DEFAULT_MEDIA_SENSITIVITY))
                }
            }

            preferenceCategory(R.string.pref_title_timelines) {
                switchPreference(
                    getString(R.string.pref_title_timelines),
                    { activeAccount.mediaPreviewEnabled },
                ) {
                    updateAccount { account -> account.mediaPreviewEnabled = it }
                    eventHub.dispatch(PreferenceChangedEvent(PrefKeys.MEDIA_PREVIEW_ENABLED))
                }

                switchPreference(
                    getString(R.string.pref_title_alway_show_sensitive_media),
                    { activeAccount.alwaysShowSensitiveMedia }
                ) {
                    updateAccount { account -> account.alwaysShowSensitiveMedia = it }
                    eventHub.dispatch(PreferenceChangedEvent(PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA))
                }

                switchPreference(
                    getString(R.string.pref_title_alway_open_spoiler),
                    { activeAccount.alwaysOpenSpoiler }
                ) {
                    updateAccount { account -> account.alwaysOpenSpoiler = it }
                    eventHub.dispatch(PreferenceChangedEvent(PrefKeys.ALWAYS_OPEN_SPOILER))
                }

            }

            preferenceCategory(R.string.pref_title_timeline_filters) {
                clickPreference(getString(R.string.pref_title_public_filter_keywords)) {
                    launchFilterActivity(Filter.PUBLIC, R.string.pref_title_public_filter_keywords)
                }

                clickPreference(getString(R.string.title_notifications)) {
                    launchFilterActivity(Filter.NOTIFICATIONS, R.string.title_notifications)
                }

                clickPreference(getString(R.string.pref_title_thread_filter_keywords)) {
                    launchFilterActivity(Filter.THREAD, R.string.pref_title_thread_filter_keywords)
                }

                clickPreference(getString(R.string.title_accounts)) {
                    launchFilterActivity(Filter.ACCOUNT, R.string.title_accounts)
                }
            }
        }

        return viewRoot
    }

    private val activeAccount: AccountEntity
        get() = accountManager.activeAccount!!

    private fun openNotificationPrefs() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent()
            intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
            intent.putExtra("android.provider.extra.APP_PACKAGE", BuildConfig.APPLICATION_ID)
            startActivity(intent)
        } else {
            activity?.let {
                val intent =
                    PreferencesActivity.newIntent(it, PreferencesActivity.NOTIFICATION_PREFERENCES)
                it.startActivity(intent)
                it.overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
            }
        }
    }

    private inline fun updateAccount(changer: (AccountEntity) -> Unit) {
        accountManager.activeAccount?.let { account ->
            changer(account)
            accountManager.saveAccount(account)
            updateTrigger?.invoke()
        }
    }

    private fun syncWithServer(visibility: String? = null, sensitive: Boolean? = null) {
        mastodonApi.accountUpdateSource(visibility, sensitive)
            .enqueue(object : Callback<Account> {
                override fun onResponse(call: Call<Account>, response: Response<Account>) {
                    val account = response.body()
                    if (response.isSuccessful && account != null) {
                        updateAccount {
                            it.defaultPostPrivacy = account.source?.privacy
                                ?: Status.Visibility.PUBLIC
                            it.defaultMediaSensitivity = account.source?.sensitive ?: false
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

    private fun getIconForVisibility(visibility: Status.Visibility): Drawable {
        @DrawableRes
        val res = when (visibility) {
            Status.Visibility.PRIVATE -> R.drawable.ic_lock_outline_24dp
            Status.Visibility.UNLISTED -> R.drawable.ic_lock_open_24dp
            else -> R.drawable.ic_public_24dp
        }
        return makeIcon(res)
    }

    private fun getIconForSensitivity(sensitive: Boolean): Drawable {
        @DrawableRes
        val res = if (sensitive) {
            R.drawable.ic_hide_media_24dp
        } else {
            R.drawable.ic_eye_24dp
        }
        return makeIcon(res)
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
