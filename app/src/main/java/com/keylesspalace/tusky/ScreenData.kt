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
import android.content.Intent
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.keylesspalace.tusky.components.accountlist.AccountListActivity
import com.keylesspalace.tusky.components.announcements.AnnouncementsActivity
import com.keylesspalace.tusky.components.conversation.ConversationsFragment
import com.keylesspalace.tusky.components.drafts.DraftsActivity
import com.keylesspalace.tusky.components.notifications.NotificationsFragment
import com.keylesspalace.tusky.components.scheduled.ScheduledStatusActivity
import com.keylesspalace.tusky.components.tabs.TabActivity
import com.keylesspalace.tusky.components.timeline.TimelineFragment
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.components.trending.TrendingFragment
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial.Icon

/** this would be a good case for a sealed class, but that does not work nice with Room */

const val HOME = "Home"
const val NOTIFICATIONS = "Notifications"
const val LOCAL = "Local"
const val FEDERATED = "Federated"
const val DIRECT = "Direct"
const val TRENDING = "Trending"
const val HASHTAG = "Hashtag"
const val LIST = "List"
const val EDIT_PROFILE = "Edit profile"
const val FAVOURITES = "Favourites"
const val BOOKMARKS = "Bookmarks"
const val FOLLOW_REQUESTS = "Follow requests"
const val LISTS = "Lists"
const val DRAFTS = "Drafts"
const val SCHEDULED_POSTS = "Scheduled posts"
const val ANNOUNCEMENTS = "Announcements"

open class ScreenData(
    val id: String,
    @StringRes val text: Int,
    val icon: Icon,
    val arguments: List<String> = emptyList(),
    val intentAction: (Context, List<String>, Boolean) -> Intent,
    val title: (Context) -> String = { context -> context.getString(text) },
    val unique: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScreenData

        if (id != other.id) return false
        if (arguments != other.arguments) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + arguments.hashCode()
        return result
    }

    open fun withArguments(args: List<String>): ScreenData = ScreenData(
        id = id,
        text = text,
        icon = icon,
        arguments = args,
        intentAction = intentAction,
        title = title
    )
}

class TabScreenData(
    id: String,
    text: Int,
    icon: Icon,
    arguments: List<String> = emptyList(),
    val fragmentAction: (List<String>) -> Fragment,
    title: (Context) -> String = { context -> context.getString(text) },
    unique: Boolean = false
) : ScreenData(
    id = id,
    text = text,
    icon = icon,
    arguments = arguments,
    intentAction = { context, screenData, accountLocked ->
        TabActivity.getIntent(
            context,
            id,
            screenData,
            accountLocked
        )
    },
    title = title,
    unique = unique
) {
    override fun withArguments(args: List<String>) = TabScreenData(
        id = id,
        text = text,
        icon = icon,
        arguments = args,
        fragmentAction = fragmentAction,
        title = title
    )
}

fun createScreenDataFromId(id: String, arguments: List<String> = emptyList()): ScreenData {
    return when (id) {
        HOME -> TabScreenData(
            id = id,
            text = R.string.title_home,
            icon = Icon.gmd_home,
            fragmentAction = { TimelineFragment.newInstance(TimelineViewModel.Kind.HOME) }
        )

        NOTIFICATIONS -> TabScreenData(
            id = id,
            text = R.string.title_notifications,
            icon = Icon.gmd_notifications,
            fragmentAction = { NotificationsFragment.newInstance() }
        )

        LOCAL -> TabScreenData(
            id = id,
            text = R.string.title_public_local,
            icon = Icon.gmd_group,
            fragmentAction = { TimelineFragment.newInstance(TimelineViewModel.Kind.PUBLIC_LOCAL) }
        )

        FEDERATED -> TabScreenData(
            id = id,
            text = R.string.title_public_federated,
            icon = Icon.gmd_public,
            fragmentAction = { TimelineFragment.newInstance(TimelineViewModel.Kind.PUBLIC_FEDERATED) }
        )

        DIRECT -> TabScreenData(
            id = id,
            text = R.string.title_direct_messages,
            icon = Icon.gmd_mail,
            fragmentAction = { ConversationsFragment.newInstance() }
        )

        TRENDING -> TabScreenData(
            id = id,
            text = R.string.title_public_trending_hashtags,
            icon = Icon.gmd_trending_up,
            fragmentAction = { TrendingFragment.newInstance() }
        )

        HASHTAG -> TabScreenData(
            id = id,
            text = R.string.hashtags,
            icon = Icon.gmd_tag,
            fragmentAction = { args -> TimelineFragment.newHashtagInstance(args) },
            arguments = arguments,
            title = { context -> arguments.joinToString(separator = " ") { context.getString(R.string.title_tag, it) } }
        )

        LIST -> TabScreenData(
            id = id,
            text = R.string.list,
            icon = Icon.gmd_list,
            fragmentAction = { args -> TimelineFragment.newInstance(TimelineViewModel.Kind.LIST, args.getOrNull(0).orEmpty()) },
            arguments = arguments,
            title = { arguments.getOrNull(1).orEmpty() }
        )

        EDIT_PROFILE -> ScreenData(
            id = id,
            text = R.string.action_edit_profile,
            icon = Icon.gmd_person,
            intentAction = { context, _, _ -> Intent(context, EditProfileActivity::class.java) },
            arguments = arguments
        )

        FAVOURITES -> ScreenData(
            id = id,
            text = R.string.action_view_favourites,
            icon = Icon.gmd_star,
            intentAction = { context, _, _ -> StatusListActivity.newFavouritesIntent(context) },
            arguments = arguments
        )

        BOOKMARKS -> ScreenData(
            id = id,
            text = R.string.action_view_bookmarks,
            icon = Icon.gmd_bookmark,
            intentAction = { context, _, _ -> StatusListActivity.newBookmarksIntent(context) },
            arguments = arguments
        )

        FOLLOW_REQUESTS -> ScreenData(
            id = id,
            text = R.string.action_view_follow_requests,
            icon = Icon.gmd_person_add,
            intentAction = { context, args, accountLocked -> AccountListActivity.newIntent(context, AccountListActivity.Type.FOLLOW_REQUESTS, accountLocked = accountLocked) },
            arguments = arguments
        )

        LISTS -> ScreenData(
            id = id,
            text = R.string.action_lists,
            icon = Icon.gmd_list,
            intentAction = { context, _, _ -> ListsActivity.newIntent(context) },
            arguments = arguments
        )

        DRAFTS -> ScreenData(
            id = id,
            text = R.string.action_access_drafts,
            icon = Icon.gmd_book,
            intentAction = { context, _, _ -> DraftsActivity.newIntent(context) },
            arguments = arguments
        )

        SCHEDULED_POSTS -> ScreenData(
            id = id,
            text = R.string.action_access_scheduled_posts,
            icon = Icon.gmd_schedule,
            intentAction = { context, _, _ -> ScheduledStatusActivity.newIntent(context) },
            arguments = arguments
        )

        ANNOUNCEMENTS -> ScreenData(
            id = id,
            text = R.string.title_announcements,
            icon = Icon.gmd_campaign,
            intentAction = { context, _, _ -> AnnouncementsActivity.newIntent(context) },
            arguments = arguments
        )

        else -> throw IllegalArgumentException("unknown tab type")
    }
}

fun defaultTabs(): List<ScreenData> {
    return listOf(
        createScreenDataFromId(HOME),
        createScreenDataFromId(NOTIFICATIONS),
        createScreenDataFromId(LOCAL),
        createScreenDataFromId(DIRECT)
    )
}

fun defaultSidebarEntries(): List<ScreenData> {
    return listOf(
        createScreenDataFromId(EDIT_PROFILE),
        createScreenDataFromId(FAVOURITES),
        createScreenDataFromId(BOOKMARKS),
        createScreenDataFromId(FOLLOW_REQUESTS),
        createScreenDataFromId(LISTS),
        createScreenDataFromId(TRENDING),
        createScreenDataFromId(FEDERATED),
        createScreenDataFromId(DRAFTS),
        createScreenDataFromId(SCHEDULED_POSTS),
        createScreenDataFromId(ANNOUNCEMENTS)
    )
}
