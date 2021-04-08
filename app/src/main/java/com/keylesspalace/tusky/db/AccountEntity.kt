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

package com.keylesspalace.tusky.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.keylesspalace.tusky.TabData
import com.keylesspalace.tusky.defaultTabs

import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Status

@Entity(indices = [Index(value = ["domain", "accountId"],
        unique = true)])
@TypeConverters(Converters::class)
data class AccountEntity(@field:PrimaryKey(autoGenerate = true) var id: Long,
                         val domain: String,
                         var accessToken: String,
                         var isActive: Boolean,
                         var accountId: String = "",
                         var username: String = "",
                         var displayName: String = "",
                         var profilePictureUrl: String = "",
                         var notificationsEnabled: Boolean = true,
                         var notificationsMentioned: Boolean = true,
                         var notificationsFollowed: Boolean = true,
                         var notificationsFollowRequested: Boolean = false,
                         var notificationsReblogged: Boolean = true,
                         var notificationsFavorited: Boolean = true,
                         var notificationsPolls: Boolean = true,
                         var notificationsSubscriptions: Boolean = true,
                         var notificationSound: Boolean = true,
                         var notificationVibration: Boolean = true,
                         var notificationLight: Boolean = true,
                         var defaultPostPrivacy: Status.Visibility = Status.Visibility.PUBLIC,
                         var defaultMediaSensitivity: Boolean = false,
                         var alwaysShowSensitiveMedia: Boolean = false,
                         var alwaysOpenSpoiler: Boolean = false,
                         var mediaPreviewEnabled: Boolean = true,
                         var lastNotificationId: String = "0",
                         var activeNotifications: String = "[]",
                         var emojis: List<Emoji> = emptyList(),
                         var tabPreferences: List<TabData> = defaultTabs(),
                         var notificationsFilter: String = "[\"follow_request\"]") {

    val identifier: String
        get() = "$domain:$accountId"

    val fullName: String
        get() = "@$username@$domain"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccountEntity

        if (id == other.id) return true
        if (domain == other.domain && accountId == other.accountId) return true

        return false
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + domain.hashCode()
        result = 31 * result + accountId.hashCode()
        return result
    }
}
