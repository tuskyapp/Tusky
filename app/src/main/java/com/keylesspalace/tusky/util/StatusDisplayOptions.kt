/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.util

import android.content.SharedPreferences
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.settings.PrefKeys

data class StatusDisplayOptions(
    @get:JvmName("animateAvatars")
    val animateAvatars: Boolean,
    @get:JvmName("mediaPreviewEnabled")
    val mediaPreviewEnabled: Boolean,
    @get:JvmName("useAbsoluteTime")
    val useAbsoluteTime: Boolean,
    @get:JvmName("showBotOverlay")
    val showBotOverlay: Boolean,
    @get:JvmName("useBlurhash")
    val useBlurhash: Boolean,
    @get:JvmName("cardViewMode")
    val cardViewMode: CardViewMode,
    @get:JvmName("confirmReblogs")
    val confirmReblogs: Boolean,
    @get:JvmName("confirmFavourites")
    val confirmFavourites: Boolean,
    @get:JvmName("hideStats")
    val hideStats: Boolean,
    @get:JvmName("animateEmojis")
    val animateEmojis: Boolean,
    @get:JvmName("showStatsInline")
    val showStatsInline: Boolean,
    @get:JvmName("showSensitiveMedia")
    val showSensitiveMedia: Boolean,
    @get:JvmName("openSpoiler")
    val openSpoiler: Boolean
) {

    /**
     * @return a new StatusDisplayOptions adapted to whichever preference changed.
     */
    fun make(preferences: SharedPreferences, key: String, account: AccountEntity) = when (key) {
        PrefKeys.ANIMATE_GIF_AVATARS -> copy(
            animateAvatars = preferences.getBoolean(key, false)
        )
        PrefKeys.MEDIA_PREVIEW_ENABLED -> copy(
            mediaPreviewEnabled = account.mediaPreviewEnabled
        )
        PrefKeys.ABSOLUTE_TIME_VIEW -> copy(
            useAbsoluteTime = preferences.getBoolean(key, false)
        )
        PrefKeys.SHOW_BOT_OVERLAY -> copy(
            showBotOverlay = preferences.getBoolean(key, true)
        )
        PrefKeys.USE_BLURHASH -> copy(
            useBlurhash = preferences.getBoolean(key, true)
        )
        PrefKeys.CONFIRM_FAVOURITES -> copy(
            confirmFavourites = preferences.getBoolean(key, false)
        )
        PrefKeys.CONFIRM_REBLOGS -> copy(
            confirmReblogs = preferences.getBoolean(key, true)
        )
        PrefKeys.WELLBEING_HIDE_STATS_POSTS -> copy(
            hideStats = preferences.getBoolean(key, false)
        )
        PrefKeys.ANIMATE_CUSTOM_EMOJIS -> copy(
            animateEmojis = preferences.getBoolean(key, false)
        )
        PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> copy(
            showSensitiveMedia = account.alwaysShowSensitiveMedia
        )
        PrefKeys.ALWAYS_OPEN_SPOILER -> copy(
            openSpoiler = account.alwaysOpenSpoiler
        )
        else -> {
            this
        }
    }

    companion object {
        /** Preference keys that, if changed, affect StatusDisplayOptions */
        val prefKeys = setOf(
            PrefKeys.ABSOLUTE_TIME_VIEW,
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA,
            PrefKeys.ALWAYS_OPEN_SPOILER,
            PrefKeys.ANIMATE_CUSTOM_EMOJIS,
            PrefKeys.ANIMATE_GIF_AVATARS,
            PrefKeys.CONFIRM_FAVOURITES,
            PrefKeys.CONFIRM_REBLOGS,
            PrefKeys.MEDIA_PREVIEW_ENABLED,
            PrefKeys.SHOW_BOT_OVERLAY,
            PrefKeys.USE_BLURHASH,
            PrefKeys.WELLBEING_HIDE_STATS_POSTS
        )

        fun from(preferences: SharedPreferences, account: AccountEntity) = StatusDisplayOptions(
            animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
            mediaPreviewEnabled = account.mediaPreviewEnabled,
            useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
            showBotOverlay = preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
            useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true),
            cardViewMode = CardViewMode.NONE,
            confirmReblogs = preferences.getBoolean(PrefKeys.CONFIRM_REBLOGS, true),
            confirmFavourites = preferences.getBoolean(PrefKeys.CONFIRM_FAVOURITES, false),
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            showStatsInline = preferences.getBoolean(PrefKeys.SHOW_STATS_INLINE, false),
            showSensitiveMedia = account.alwaysShowSensitiveMedia,
            openSpoiler = account.alwaysOpenSpoiler
        )
    }
}
