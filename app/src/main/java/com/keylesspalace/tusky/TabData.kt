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
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.keylesspalace.tusky.TabData.Action.FragmentAction
import com.keylesspalace.tusky.TabData.Action.IntentAction
import com.keylesspalace.tusky.components.accountlist.AccountListActivity
import com.keylesspalace.tusky.components.announcements.AnnouncementsActivity
import com.keylesspalace.tusky.components.conversation.ConversationsFragment
import com.keylesspalace.tusky.components.drafts.DraftsActivity
import com.keylesspalace.tusky.components.notifications.NotificationsFragment
import com.keylesspalace.tusky.components.scheduled.ScheduledStatusActivity
import com.keylesspalace.tusky.components.timeline.TimelineFragment
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.components.trending.TrendingFragment
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial.Icon
import kotlinx.parcelize.Parcelize
import java.util.Objects

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

@Parcelize
data class TabData(
    val id: String,
    @StringRes val text: Int,
    val icon: Icon,
    val action: Action,
    val arguments: List<String> = emptyList(),
    val title: (Context) -> String = { context -> context.getString(text) },
    val allowedContexts: List<AllowedContext> = listOf(AllowedContext.TABS, AllowedContext.SIDEBAR)
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TabData

        if (id != other.id) return false
        if (arguments != other.arguments) return false

        return true
    }

    override fun hashCode() = Objects.hash(id, arguments)

    @Parcelize
    sealed class Action : Parcelable {
        data class FragmentAction(val fragment: (args: List<String>) -> Fragment) : Action()
        data class IntentAction(val intent: (Context, args: List<String>, accountLocked: Boolean) -> Intent) : Action()
    }

    enum class AllowedContext {
        SIDEBAR, TABS
    }
}

fun List<TabData>.hasTab(id: String): Boolean = this.find { it.id == id } != null

fun createTabDataFromId(id: String, arguments: List<String> = emptyList()): TabData {
    return when (id) {
        HOME -> TabData(
            id = id,
            text = R.string.title_home,
            icon = Icon.gmd_home,
            action = FragmentAction { TimelineFragment.newInstance(TimelineViewModel.Kind.HOME) }
        )
        NOTIFICATIONS -> TabData(
            id = id,
            text = R.string.title_notifications,
            icon = Icon.gmd_notifications,
            action = FragmentAction { NotificationsFragment.newInstance() }
        )
        LOCAL -> TabData(
            id = id,
            text = R.string.title_public_local,
            icon = Icon.gmd_group,
            action = FragmentAction { TimelineFragment.newInstance(TimelineViewModel.Kind.PUBLIC_LOCAL) }
        )
        FEDERATED -> TabData(
            id = id,
            text = R.string.title_public_federated,
            icon = Icon.gmd_public,
            action = FragmentAction { TimelineFragment.newInstance(TimelineViewModel.Kind.PUBLIC_FEDERATED) }
        )
        DIRECT -> TabData(
            id = id,
            text = R.string.title_direct_messages,
            icon = Icon.gmd_mail,
            action = FragmentAction { ConversationsFragment.newInstance() }
        )
        TRENDING -> TabData(
            id = id,
            text = R.string.title_public_trending_hashtags,
            icon = Icon.gmd_trending_up,
            action = FragmentAction { TrendingFragment.newInstance() }
        )
        HASHTAG -> TabData(
            id = id,
            text = R.string.hashtags,
            icon = Icon.gmd_tag,
            action = FragmentAction { args -> TimelineFragment.newHashtagInstance(args) },
            arguments = arguments,
            title = { context -> arguments.joinToString(separator = " ") { context.getString(R.string.title_tag, it) } }
        )
        LIST -> TabData(
            id = id,
            text = R.string.list,
            icon = Icon.gmd_list,
            action = FragmentAction { args -> TimelineFragment.newInstance(TimelineViewModel.Kind.LIST, args.getOrNull(0).orEmpty()) },
            arguments = arguments,
            title = { arguments.getOrNull(1).orEmpty() }
        )
        EDIT_PROFILE -> TabData(
            id = id,
            text = R.string.action_edit_profile,
            icon = Icon.gmd_person,
            action = IntentAction { context, _, _ -> Intent(context, EditProfileActivity::class.java) },
            arguments = arguments,
            allowedContexts = listOf(TabData.AllowedContext.SIDEBAR)
        )
        FAVOURITES -> TabData(
            id = id,
            text = R.string.action_view_favourites,
            icon = Icon.gmd_star,
            action = IntentAction { context, _, _ -> StatusListActivity.newFavouritesIntent(context) },
            arguments = arguments,
            allowedContexts = listOf(TabData.AllowedContext.SIDEBAR)
        )
        BOOKMARKS -> TabData(
            id = id,
            text = R.string.action_view_bookmarks,
            icon = Icon.gmd_bookmark,
            action = IntentAction { context, _, _ -> StatusListActivity.newBookmarksIntent(context) },
            arguments = arguments,
            allowedContexts = listOf(TabData.AllowedContext.SIDEBAR)
        )
        FOLLOW_REQUESTS -> TabData(
            id = id,
            text = R.string.action_view_follow_requests,
            icon = Icon.gmd_person_add,
            action = IntentAction { context, args, accountLocked ->
                AccountListActivity.newIntent(context, AccountListActivity.Type.FOLLOW_REQUESTS, accountLocked = accountLocked)
            },
            arguments = arguments,
            allowedContexts = listOf(TabData.AllowedContext.SIDEBAR)
        )
        LISTS -> TabData(
            id = id,
            text = R.string.action_lists,
            icon = Icon.gmd_list,
            action = IntentAction { context, _, _ -> ListsActivity.newIntent(context) },
            arguments = arguments,
            allowedContexts = listOf(TabData.AllowedContext.SIDEBAR)
        )
        DRAFTS -> TabData(
            id = id,
            text = R.string.action_access_drafts,
            icon = Icon.gmd_book,
            action = IntentAction { context, _, _ -> DraftsActivity.newIntent(context) },
            arguments = arguments,
            allowedContexts = listOf(TabData.AllowedContext.SIDEBAR)
        )
        SCHEDULED_POSTS -> TabData(
            id = id,
            text = R.string.action_access_scheduled_posts,
            icon = Icon.gmd_schedule,
            action = IntentAction { context, _, _ -> ScheduledStatusActivity.newIntent(context) },
            arguments = arguments,
            allowedContexts = listOf(TabData.AllowedContext.SIDEBAR)
        )
        ANNOUNCEMENTS -> TabData(
            id = id,
            text = R.string.title_announcements,
            icon = Icon.gmd_campaign,
            action = IntentAction { context, _, _ -> AnnouncementsActivity.newIntent(context) },
            arguments = arguments,
            allowedContexts = listOf(TabData.AllowedContext.SIDEBAR)
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

fun defaultSidebarEntries(): List<TabData> {
    return listOf(
        createTabDataFromId(EDIT_PROFILE),
        createTabDataFromId(FAVOURITES),
        createTabDataFromId(BOOKMARKS),
        createTabDataFromId(FOLLOW_REQUESTS),
        createTabDataFromId(LISTS),
        createTabDataFromId(TRENDING),
        createTabDataFromId(FEDERATED),
        createTabDataFromId(DRAFTS),
        createTabDataFromId(SCHEDULED_POSTS),
        createTabDataFromId(ANNOUNCEMENTS)
    )
}
