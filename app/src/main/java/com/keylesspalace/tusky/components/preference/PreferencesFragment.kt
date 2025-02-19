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

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.settings.AppTheme
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.settings.emojiPreference
import com.keylesspalace.tusky.settings.listPreference
import com.keylesspalace.tusky.settings.makePreferenceScreen
import com.keylesspalace.tusky.settings.preference
import com.keylesspalace.tusky.settings.preferenceCategory
import com.keylesspalace.tusky.settings.sliderPreference
import com.keylesspalace.tusky.settings.switchPreference
import com.keylesspalace.tusky.util.LocaleManager
import com.keylesspalace.tusky.util.icon
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import dagger.hilt.android.AndroidEntryPoint
import de.c1710.filemojicompat_ui.views.picker.preference.EmojiPickerPreference
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PreferencesFragment : BasePreferencesFragment() {

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var localeManager: LocaleManager

    enum class ReadingOrder {
        /** User scrolls up, reading statuses oldest to newest */
        OLDEST_FIRST,

        /** User scrolls down, reading statuses newest to oldest. Default behaviour. */
        NEWEST_FIRST;

        companion object {
            fun from(s: String?): ReadingOrder {
                s ?: return NEWEST_FIRST

                return try {
                    valueOf(s.uppercase())
                } catch (_: Throwable) {
                    NEWEST_FIRST
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        makePreferenceScreen {
            preferenceCategory(R.string.pref_title_appearance_settings) {
                listPreference {
                    setDefaultValue(AppTheme.DEFAULT.value)
                    setEntries(R.array.app_theme_names)
                    entryValues = AppTheme.stringValues()
                    key = PrefKeys.APP_THEME
                    setSummaryProvider { entry }
                    setTitle(R.string.pref_title_app_theme)
                    icon = icon(GoogleMaterial.Icon.gmd_palette)
                }

                emojiPreference(requireActivity()) {
                    setTitle(R.string.emoji_style)
                    icon = icon(GoogleMaterial.Icon.gmd_sentiment_satisfied)
                }

                listPreference {
                    setDefaultValue("default")
                    setEntries(R.array.language_entries)
                    setEntryValues(R.array.language_values)
                    key = PrefKeys.LANGUAGE + "_" // deliberately not the actual key, the real handling happens in LocaleManager
                    setSummaryProvider { entry }
                    setTitle(R.string.pref_title_language)
                    icon = icon(GoogleMaterial.Icon.gmd_translate)
                    preferenceDataStore = localeManager
                }

                sliderPreference {
                    key = PrefKeys.UI_TEXT_SCALE_RATIO
                    setDefaultValue(100F)
                    valueTo = 150F
                    valueFrom = 50F
                    stepSize = 5F
                    setTitle(R.string.pref_ui_text_size)
                    format = "%.0f%%"
                    decrementIcon = icon(GoogleMaterial.Icon.gmd_zoom_out)
                    incrementIcon = icon(GoogleMaterial.Icon.gmd_zoom_in)
                    icon = icon(GoogleMaterial.Icon.gmd_format_size)
                }

                listPreference {
                    setDefaultValue("medium")
                    setEntries(R.array.post_text_size_names)
                    setEntryValues(R.array.post_text_size_values)
                    key = PrefKeys.STATUS_TEXT_SIZE
                    setSummaryProvider { entry }
                    setTitle(R.string.pref_post_text_size)
                    icon = icon(GoogleMaterial.Icon.gmd_format_size)
                }

                listPreference {
                    setDefaultValue(ReadingOrder.NEWEST_FIRST.name)
                    setEntries(R.array.reading_order_names)
                    setEntryValues(R.array.reading_order_values)
                    key = PrefKeys.READING_ORDER
                    setSummaryProvider { entry }
                    setTitle(R.string.pref_title_reading_order)
                    icon = icon(GoogleMaterial.Icon.gmd_sort)
                }

                listPreference {
                    setDefaultValue("top")
                    setEntries(R.array.pref_main_nav_position_options)
                    setEntryValues(R.array.pref_main_nav_position_values)
                    key = PrefKeys.MAIN_NAV_POSITION
                    setSummaryProvider { entry }
                    setTitle(R.string.pref_main_nav_position)
                }

                listPreference {
                    setDefaultValue("disambiguate")
                    setEntries(R.array.pref_show_self_username_names)
                    setEntryValues(R.array.pref_show_self_username_values)
                    key = PrefKeys.SHOW_SELF_USERNAME
                    setSummaryProvider { entry }
                    setTitle(R.string.pref_title_show_self_username)
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.HIDE_TOP_TOOLBAR
                    setTitle(R.string.pref_title_hide_top_toolbar)
                }

                switchPreference {
                    setDefaultValue(true)
                    key = PrefKeys.SHOW_NOTIFICATIONS_FILTER
                    setTitle(R.string.pref_title_show_notifications_filter)
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.ABSOLUTE_TIME_VIEW
                    setTitle(R.string.pref_title_absolute_time)
                }

                switchPreference {
                    setDefaultValue(true)
                    key = PrefKeys.SHOW_BOT_OVERLAY
                    setTitle(R.string.pref_title_bot_overlay)
                    icon = icon(R.drawable.ic_bot_24dp)
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.ANIMATE_GIF_AVATARS
                    setTitle(R.string.pref_title_animate_gif_avatars)
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.ANIMATE_CUSTOM_EMOJIS
                    setTitle(R.string.pref_title_animate_custom_emojis)
                }

                switchPreference {
                    setDefaultValue(true)
                    key = PrefKeys.USE_BLURHASH
                    setTitle(R.string.pref_title_gradient_for_media)
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.SHOW_CARDS_IN_TIMELINES
                    setTitle(R.string.pref_title_show_cards_in_timelines)
                }

                switchPreference {
                    setDefaultValue(true)
                    key = PrefKeys.CONFIRM_REBLOGS
                    setTitle(R.string.pref_title_confirm_reblogs)
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.CONFIRM_FAVOURITES
                    setTitle(R.string.pref_title_confirm_favourites)
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.CONFIRM_FOLLOWS
                    setTitle(R.string.pref_title_confirm_follows)
                }

                switchPreference {
                    setDefaultValue(true)
                    key = PrefKeys.ENABLE_SWIPE_FOR_TABS
                    setTitle(R.string.pref_title_enable_swipe_for_tabs)
                }

                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.SHOW_STATS_INLINE
                    setTitle(R.string.pref_title_show_stat_inline)
                }
            }

            preferenceCategory(R.string.pref_title_browser_settings) {
                switchPreference {
                    setDefaultValue(false)
                    key = PrefKeys.CUSTOM_TABS
                    setTitle(R.string.pref_title_custom_tabs)
                }
            }

            preferenceCategory(R.string.pref_title_wellbeing_mode) {
                switchPreference {
                    title = getString(R.string.limit_notifications)
                    setDefaultValue(false)
                    key = PrefKeys.WELLBEING_LIMITED_NOTIFICATIONS
                    setOnPreferenceChangeListener { _, value ->
                        for (account in accountManager.accounts) {
                            val notificationFilter = account.notificationsFilter.toMutableSet()

                            if (value == true) {
                                notificationFilter.add(Notification.Type.Favourite)
                                notificationFilter.add(Notification.Type.Follow)
                                notificationFilter.add(Notification.Type.Reblog)
                            } else {
                                notificationFilter.remove(Notification.Type.Favourite)
                                notificationFilter.remove(Notification.Type.Follow)
                                notificationFilter.remove(Notification.Type.Reblog)
                            }

                            lifecycleScope.launch {
                                accountManager.updateAccount(account) { copy(notificationsFilter = notificationFilter) }
                            }
                        }
                        true
                    }
                }

                switchPreference {
                    title = getString(R.string.wellbeing_hide_stats_posts)
                    setDefaultValue(false)
                    key = PrefKeys.WELLBEING_HIDE_STATS_POSTS
                }

                switchPreference {
                    title = getString(R.string.wellbeing_hide_stats_profile)
                    setDefaultValue(false)
                    key = PrefKeys.WELLBEING_HIDE_STATS_PROFILE
                }
            }

            preferenceCategory(R.string.pref_title_proxy_settings) {
                preference {
                    setTitle(R.string.pref_title_http_proxy_settings)
                    fragment = ProxyPreferencesFragment::class.qualifiedName
                    summaryProvider = ProxyPreferencesFragment.SummaryProvider
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.action_view_preferences)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (!EmojiPickerPreference.onDisplayPreferenceDialog(this, preference)) {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    companion object {
        fun newInstance(): PreferencesFragment {
            return PreferencesFragment()
        }
    }
}
