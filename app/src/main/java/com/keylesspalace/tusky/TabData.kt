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

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.keylesspalace.tusky.components.conversation.ConversationsFragment
import com.keylesspalace.tusky.components.notifications.NotificationsFragment
import com.keylesspalace.tusky.components.timeline.TimelineFragment
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.components.trending.TrendingTagsFragment
import java.util.Objects

/** this would be a good case for a sealed class, but that does not work nice with Room */

const val HOME = "Home"
const val NOTIFICATIONS = "Notifications"
const val LOCAL = "Local"
const val FEDERATED = "Federated"
const val DIRECT = "Direct"
const val TRENDING_TAGS = "TrendingTags"
const val TRENDING_STATUSES = "TrendingStatuses"
const val HASHTAG = "Hashtag"
const val LIST = "List"
const val BOOKMARKS = "Bookmarks"

data class TabData(
    val id: String,
    @StringRes val text: Int,
    @DrawableRes val icon: Int,
    val fragment: (List<String>) -> Fragment,
    val arguments: List<String> = emptyList(),
    val title: (Context) -> String = { context -> context.getString(text) }
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TabData

        if (id != other.id) return false
        return arguments == other.arguments
    }

    override fun hashCode() = Objects.hash(id, arguments)
}

fun List<TabData>.hasTab(id: String): Boolean = this.any { it.id == id }

fun createTabDataFromId(id: String, arguments: List<String> = emptyList()): TabData {
    return when (id) {
        HOME -> TabData(
            id = HOME,
            text = R.string.title_home,
            icon = R.drawable.tab_icon_home,
            fragment = { TimelineFragment.newInstance(TimelineViewModel.Kind.HOME) }
        )
        NOTIFICATIONS -> TabData(
            id = NOTIFICATIONS,
            text = R.string.title_notifications,
            icon = R.drawable.tab_icon_notifications,
            fragment = { NotificationsFragment.newInstance() }
        )
        LOCAL -> TabData(
            id = LOCAL,
            text = R.string.title_public_local,
            icon = R.drawable.tab_icon_local,
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
            icon = R.drawable.tab_icon_direct,
            fragment = { ConversationsFragment.newInstance() }
        )
        TRENDING_TAGS -> TabData(
            id = TRENDING_TAGS,
            text = R.string.title_public_trending_hashtags,
            icon = R.drawable.tab_icon_trending_tags,
            fragment = { TrendingTagsFragment.newInstance() }
        )
        TRENDING_STATUSES -> TabData(
            id = TRENDING_STATUSES,
            text = R.string.title_public_trending_statuses,
            icon = R.drawable.tab_icon_trending_posts,
            fragment = {
                TimelineFragment.newInstance(
                    TimelineViewModel.Kind.PUBLIC_TRENDING_STATUSES
                )
            }
        )
        HASHTAG -> TabData(
            id = HASHTAG,
            text = R.string.hashtags,
            icon = R.drawable.ic_tag_24dp,
            fragment = { args -> TimelineFragment.newHashtagInstance(args) },
            arguments = arguments,
            title = { context ->
                arguments.joinToString(separator = " ") {
                    context.getString(R.string.hashtag_format, it)
                }
            }
        )
        LIST -> TabData(
            id = LIST,
            text = R.string.list,
            icon = R.drawable.tab_icon_list,
            fragment = { args ->
                TimelineFragment.newInstance(
                    TimelineViewModel.Kind.LIST,
                    args.getOrNull(0).orEmpty()
                )
            },
            arguments = arguments,
            title = { arguments.getOrNull(1).orEmpty() }
        )
        BOOKMARKS -> TabData(
            id = BOOKMARKS,
            text = R.string.title_bookmarks,
            icon = R.drawable.tab_icon_bookmarks,
            fragment = { TimelineFragment.newInstance(TimelineViewModel.Kind.BOOKMARKS) }
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
