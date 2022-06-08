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

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.settings.AppTheme
import com.keylesspalace.tusky.settings.PrefData
import com.keylesspalace.tusky.settings.PrefStore
import com.keylesspalace.tusky.settings.PreferenceOption
import com.keylesspalace.tusky.settings.PreferenceParent
import com.keylesspalace.tusky.settings.clickPreference
import com.keylesspalace.tusky.settings.customListPreference
import com.keylesspalace.tusky.settings.editTextPreference
import com.keylesspalace.tusky.settings.getBlocking
import com.keylesspalace.tusky.settings.listPreference
import com.keylesspalace.tusky.settings.makePreferenceScreen
import com.keylesspalace.tusky.settings.preferenceCategory
import com.keylesspalace.tusky.settings.switchPreference
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.util.deserialize
import com.keylesspalace.tusky.util.serialize
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

class PreferencesFragment : Fragment(), Injectable {

    @Inject
    lateinit var okhttpclient: OkHttpClient

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var prefStore: PrefStore
    lateinit var prefs: PrefData

    private var updateTrigger: (() -> Unit)? = null
    private fun updatePrefs(updater: (PrefData) -> PrefData) {
        lifecycleScope.launch {
            prefStore.updateData(updater)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // TODO: border between categories?
        val viewRoot = ScrollView(inflater.context)
        val rootLayout = LinearLayout(inflater.context).apply {
            orientation = LinearLayout.VERTICAL
            viewRoot.addView(this)
        }

        prefs = prefStore.getBlocking()
        lifecycleScope.launch {
            prefStore.data.collect {
                prefs = it
                // trigger update?
                withContext(Dispatchers.Main) {
                    updateTrigger?.invoke()
                }
            }
        }

        this.updateTrigger = makePreferenceScreen(rootLayout) {
            appearanceCategory()
            browserCategory()
            filtersCategory()
            wellbeingCategory()
            preferenceCategory(R.string.pref_title_proxy_settings) {
                switchPreference(
                    getString(R.string.pref_title_http_proxy_enable),
                    { prefs.httpProxyEnabled }
                ) {
                    updatePrefs { data -> data.copy(httpProxyEnabled = it) }
                }
                editTextPreference(
                    getString(R.string.pref_title_http_proxy_server),
                    { prefs.httpProxyServer },
                ) {
                    updatePrefs { data -> data.copy(httpProxyServer = it) }
                }
                editTextPreference(
                    getString(R.string.pref_title_http_proxy_port),
                    { prefs.httpProxyPort },
                ) {
                    updatePrefs { data -> data.copy(httpProxyPort = it) }
                }
            }
        }
        return viewRoot
    }

    private fun PreferenceParent.wellbeingCategory() {
        preferenceCategory(R.string.pref_title_wellbeing_mode) {
            switchPreference(
                getString(R.string.limit_notifications),
                { prefs.limitedNotifications }
            ) {
                updatePrefs { data -> data.copy(limitedNotifications = it) }
                for (account in accountManager.accounts) {
                    val notificationFilter =
                        deserialize(account.notificationsFilter).toMutableSet()

                    if (it) {
                        notificationFilter.add(Notification.Type.FAVOURITE)
                        notificationFilter.add(Notification.Type.FOLLOW)
                        notificationFilter.add(Notification.Type.REBLOG)
                    } else {
                        notificationFilter.remove(Notification.Type.FAVOURITE)
                        notificationFilter.remove(Notification.Type.FOLLOW)
                        notificationFilter.remove(Notification.Type.REBLOG)
                    }

                    account.notificationsFilter = serialize(notificationFilter)
                    accountManager.saveAccount(account)
                }
            }

            switchPreference(
                getString(R.string.wellbeing_hide_stats_posts),
                { prefs.hideStatsPosts },
            ) { updatePrefs { data -> data.copy(hideStatsPosts = it) } }
            switchPreference(
                getString(R.string.wellbeing_hide_stats_profile),
                { prefs.hideStatsProfile },
            ) { updatePrefs { data -> data.copy(hideStatsProfile = it) } }
        }
    }

    private fun PreferenceParent.browserCategory() {
        preferenceCategory(R.string.pref_title_browser_settings) {
            switchPreference(
                getString(R.string.pref_title_custom_tabs),
                { prefs.customTabs },
            ) { updatePrefs { data -> data.copy(customTabs = it) } }
        }
    }

    private fun PreferenceParent.filtersCategory() {
        preferenceCategory(R.string.pref_title_timeline_filters) {
            clickPreference(getString(R.string.pref_title_status_tabs)) {
                val activity = activity as BaseActivity
                val intent = PreferencesActivity.newIntent(
                    activity,
                    PreferencesActivity.TAB_FILTER_PREFERENCES
                )
                activity.startActivityWithSlideInAnimation(intent)
            }
        }
    }

    private fun PreferenceParent.appearanceCategory() {
        preferenceCategory(R.string.pref_title_appearance_settings) {
            val themeOptions = listOf(
                AppTheme.NIGHT.value to R.string.app_them_dark,
                AppTheme.DAY.value to R.string.app_theme_light,
                AppTheme.BLACK.value to R.string.app_theme_black,
                AppTheme.AUTO.value to R.string.app_theme_auto,
                AppTheme.AUTO_SYSTEM.value to R.string.app_theme_system,
            ).map(::PreferenceOption)
            listPreference(
                getString(R.string.pref_title_app_theme),
                themeOptions,
                { prefs.appTheme },
                { makeIcon(GoogleMaterial.Icon.gmd_palette) },
            ) {
                updatePrefs { data -> data.copy(appTheme = it) }
            }

            val emojiSelector = EmojiSelector(context, okhttpclient, prefs.emojiFont) {
                updatePrefs { data -> data.copy(emojiFont = it) }
            }
            customListPreference(
                getString(R.string.emoji_style),
                {
                    emojiSelector.summary
                },
                { makeIcon(GoogleMaterial.Icon.gmd_sentiment_satisfied) },
            ) {
                emojiSelector.showSelectionDialog()

            }

            val languageNames = resources.getStringArray(R.array.language_entries)
            val languageValues = resources.getStringArray(R.array.language_values)
            val languageOptions = languageNames
                .zip(languageValues)
                .map { PreferenceOption(it.first, it.second) }
            listPreference(
                getString(R.string.pref_title_language),
                languageOptions,
                { prefs.language },
                { makeIcon(GoogleMaterial.Icon.gmd_translate) }
            ) {
                updatePrefs { data -> data.copy(language = it) }
            }

            val textSizeOptions = listOf(
                "smallest" to R.string.status_text_size_smallest,
                "small" to R.string.status_text_size_small,
                "medium" to R.string.status_text_size_medium,
                "large" to R.string.status_text_size_large,
                "largest" to R.string.status_text_size_largest,
            ).map(::PreferenceOption)
            listPreference(
                getString(R.string.pref_status_text_size),
                textSizeOptions,
                { prefs.statusTextSize },
                { makeIcon(GoogleMaterial.Icon.gmd_format_size) }
            ) {
                updatePrefs { data -> data.copy(statusTextSize = it) }
            }

            val mainNavOptions = listOf(
                "top" to R.string.pref_main_nav_position_option_top,
                "bottom" to R.string.pref_main_nav_position_option_bottom,
            ).map(::PreferenceOption)
            listPreference(
                getString(R.string.pref_main_nav_position),
                mainNavOptions,
                { prefs.mainNavPosition }
            ) {
                updatePrefs { data -> data.copy(mainNavPosition = it) }
            }

            switchPreference(
                getString(R.string.pref_title_hide_top_toolbar),
                { prefs.hideTopToolbar }
            ) {
                updatePrefs { data -> data.copy(hideTopToolbar = it) }
            }
            switchPreference(
                getString(R.string.pref_title_hide_follow_button),
                { prefs.hideFab }
            ) {
                updatePrefs { data -> data.copy(hideFab = it) }
            }
            switchPreference(
                getString(R.string.pref_title_absolute_time),
                { prefs.useAbsoluteTime }
            ) {
                updatePrefs { data -> data.copy(useAbsoluteTime = it) }
            }
            switchPreference(
                getString(R.string.pref_title_bot_overlay),
                { prefs.showBotOverlay },
                { makeIcon(R.drawable.ic_bot_24dp) },
            ) {
                updatePrefs { data -> data.copy(showBotOverlay = it) }
            }
            switchPreference(
                getString(R.string.pref_title_animate_gif_avatars),
                { prefs.animateAvatars }
            ) {
                updatePrefs { data -> data.copy(animateAvatars = it) }
            }
            switchPreference(
                getString(R.string.pref_title_animate_custom_emojis),
                { prefs.animateEmojis }
            ) {
                updatePrefs { data -> data.copy(animateEmojis = it) }
            }
            switchPreference(
                getString(R.string.pref_title_gradient_for_media),
                { prefs.useBlurhash }
            ) {
                updatePrefs { data -> data.copy(useBlurhash = it) }
            }
            switchPreference(
                getString(R.string.pref_title_show_cards_in_timelines),
                { prefs.showCardsInTimelines }
            ) {
                updatePrefs { data -> data.copy(showCardsInTimelines = it) }
            }
            switchPreference(
                getString(R.string.pref_title_show_notifications_filter),
                { prefs.showNotificationsFilter }
            ) {
                updatePrefs { data -> data.copy(showNotificationsFilter = it) }
            }
            switchPreference(
                getString(R.string.pref_title_confirm_reblogs),
                { prefs.confirmReblogs }
            ) {
                updatePrefs { data -> data.copy(confirmReblogs = it) }
            }
            switchPreference(
                getString(R.string.pref_title_confirm_favourites),
                { prefs.confirmFavourites }
            ) {
                updatePrefs { data -> data.copy(confirmFavourites = it) }
            }
            switchPreference(
                getString(R.string.pref_title_enable_swipe_for_tabs),
                { prefs.enableSwipeForTabs }
            ) {
                updatePrefs { data -> data.copy(enableSwipeForTabs = it) }
            }
        }
    }

    companion object {
        fun newInstance(): PreferencesFragment {
            return PreferencesFragment()
        }
    }
}

fun Fragment.makeIcon(icon: GoogleMaterial.Icon): IconicsDrawable {
    val context = requireContext()
    return IconicsDrawable(context, icon).apply {
        size = IconicsSize.res(R.dimen.preference_icon_size)
        colorInt = ThemeUtils.getColor(context, R.attr.iconColor)
    }
}

fun Fragment.makeIcon(@DrawableRes res: Int): Drawable {
    val context = requireContext()
    return AppCompatResources.getDrawable(context, res)!!.apply {
        setTint(ThemeUtils.getColor(context, R.attr.iconColor))
    }
}