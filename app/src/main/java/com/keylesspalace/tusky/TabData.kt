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

import com.keylesspalace.tusky.components.conversation.ConversationsFragment
import com.keylesspalace.tusky.components.timeline.TimelineFragment
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.components.trending.TrendingFragment
import com.keylesspalace.tusky.core.database.model.TabData
import com.keylesspalace.tusky.fragment.NotificationsFragment

const val HOME = "Home"
const val NOTIFICATIONS = "Notifications"
const val LOCAL = "Local"
const val FEDERATED = "Federated"
const val DIRECT = "Direct"
const val TRENDING = "Trending"
const val HASHTAG = "Hashtag"
const val LIST = "List"

fun List<TabData>.hasTab(id: String): Boolean = this.find { it.id == id } != null

fun createTabDataFromId(id: String, arguments: List<String> = emptyList()): TabData {
    return when (id) {
        HOME -> TabData(
            id = HOME,
            text = R.string.title_home,
            icon = R.drawable.ic_home_24dp,
            fragment = { TimelineFragment.newInstance(TimelineViewModel.Kind.HOME) }
        )
        NOTIFICATIONS -> TabData(
            id = NOTIFICATIONS,
            text = R.string.title_notifications,
            icon = R.drawable.ic_notifications_24dp,
            fragment = { NotificationsFragment.newInstance() }
        )
        LOCAL -> TabData(
            id = LOCAL,
            text = R.string.title_public_local,
            icon = R.drawable.ic_local_24dp,
            fragment = { TimelineFragment.newInstance(TimelineViewModel.Kind.PUBLIC_LOCAL) }
        )
        FEDERATED -> TabData(
            id = FEDERATED,
            text = R.string.title_public_federated,
            icon = R.drawable.ic_public_24dp,
            fragment = { TimelineFragment.newInstance(TimelineViewModel.Kind.PUBLIC_FEDERATED) }
        )
        DIRECT -> TabData(
            id = DIRECT,
            text = R.string.title_direct_messages,
            icon = R.drawable.ic_reblog_direct_24dp,
            fragment = { ConversationsFragment.newInstance() }
        )
        TRENDING -> TabData(
            id = TRENDING,
            text = R.string.title_public_trending_hashtags,
            icon = R.drawable.ic_trending_up_24px,
            fragment = { TrendingFragment.newInstance() }
        )
        HASHTAG -> TabData(
            id = HASHTAG,
            text = R.string.hashtags,
            icon = R.drawable.ic_hashtag,
            fragment = { args -> TimelineFragment.newHashtagInstance(args) },
            arguments = arguments,
            title = { context -> arguments.joinToString(separator = " ") { context.getString(R.string.title_tag, it) } }
        )
        LIST -> TabData(
            id = LIST,
            text = R.string.list,
            icon = R.drawable.ic_list,
            fragment = { args -> TimelineFragment.newInstance(TimelineViewModel.Kind.LIST, args.getOrNull(0).orEmpty()) },
            arguments = arguments,
            title = { arguments.getOrNull(1).orEmpty() }
        )
        else -> throw IllegalArgumentException("unknown tab type")
    }
}

fun defaultTabs(): List<TabData> {
    return listOf(
        createTabDataFromId(HOME),
        createTabDataFromId(NOTIFICATIONS),
        createTabDataFromId(LOCAL),
        createTabDataFromId(DIRECT)
    )
}
