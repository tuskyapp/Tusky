/* Copyright 2019 Conny Duck
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

package com.keylesspalace.tusky

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.keylesspalace.tusky.components.conversation.ConversationsFragment
import com.keylesspalace.tusky.fragment.NotificationsFragment
import com.keylesspalace.tusky.fragment.TimelineFragment

/** this would be a good case for a sealed class, but that does not work nice with Room */

const val HOME = "Home"
const val NOTIFICATIONS = "Notifications"
const val LOCAL = "Local"
const val FEDERATED = "Federated"
const val DIRECT = "Direct"
const val HASHTAG = "Hashtag"

data class TabData(val id: String,
                   @StringRes val text: Int,
                   @DrawableRes val icon: Int,
                   val fragment: (List<String>) -> Fragment,
                   val arguments: List<String> = emptyList())

fun createTabDataFromId(id: String, arguments: List<String> = emptyList()): TabData {
    return when (id) {
        HOME -> TabData(HOME, R.string.title_home, R.drawable.ic_home_24dp, { TimelineFragment.newInstance(TimelineFragment.Kind.HOME,true) })
        NOTIFICATIONS -> TabData(NOTIFICATIONS, R.string.title_notifications, R.drawable.ic_notifications_24dp, { NotificationsFragment.newInstance() })
        LOCAL -> TabData(LOCAL, R.string.title_public_local, R.drawable.ic_local_24dp, { TimelineFragment.newInstance(TimelineFragment.Kind.PUBLIC_LOCAL,true) })
        FEDERATED -> TabData(FEDERATED, R.string.title_public_federated, R.drawable.ic_public_24dp, { TimelineFragment.newInstance(TimelineFragment.Kind.PUBLIC_FEDERATED,true) })
        DIRECT -> TabData(DIRECT, R.string.title_direct_messages, R.drawable.reblog_direct_dark, { ConversationsFragment.newInstance() })
        HASHTAG -> TabData(HASHTAG, R.string.hashtag, R.drawable.ic_hashtag, { args -> TimelineFragment.newInstance(TimelineFragment.Kind.TAG, args.getOrNull(0).orEmpty(),true) }, arguments)
        else -> throw IllegalArgumentException("unknown tab type")
    }
}

fun defaultTabs(): List<TabData> {
    return listOf(
            createTabDataFromId(HOME),
            createTabDataFromId(NOTIFICATIONS),
            createTabDataFromId(LOCAL),
            createTabDataFromId(FEDERATED)
    )
}