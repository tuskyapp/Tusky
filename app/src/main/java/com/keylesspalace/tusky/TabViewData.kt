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
import com.keylesspalace.tusky.components.trending.TrendingFragment
import com.keylesspalace.tusky.core.database.model.TabData
import com.keylesspalace.tusky.core.database.model.TabKind

/**
 * Wrap a [TabData] with additional information to display a tab with that data.
 *
 * @param tabData wrapped [TabData]
 * @param text text to use for this tab when displayed in lists
 * @param icon icon to use when displaying the tab
 * @param fragment [Fragment] to display the tab's contents
 * @param title title to display in the action bar if this tab is active
 */
data class TabViewData(
    val tabData: TabData,
    @StringRes val text: Int,
    @DrawableRes val icon: Int,
    val fragment: (List<String>) -> Fragment,
    val title: (Context) -> String = { context -> context.getString(text) }
) {
    val kind get() = this.tabData.kind

    val arguments get() = this.tabData.arguments

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TabViewData

        if (tabData != other.tabData) return false

        return true
    }

    override fun hashCode() = tabData.hashCode()

    companion object {
        fun from(tabKind: TabKind) = from(TabData.from(tabKind))

        fun from(tabData: TabData) = when (tabData.kind) {
            TabKind.HOME -> TabViewData(
                tabData = tabData,
                text = R.string.title_home,
                icon = R.drawable.ic_home_24dp,
                fragment = { TimelineFragment.newInstance(TimelineViewModel.Kind.HOME) }
            )
            TabKind.NOTIFICATIONS -> TabViewData(
                tabData = tabData,
                text = R.string.title_notifications,
                icon = R.drawable.ic_notifications_24dp,
                fragment = { NotificationsFragment.newInstance() }
            )
            TabKind.LOCAL -> TabViewData(
                tabData = tabData,
                text = R.string.title_public_local,
                icon = R.drawable.ic_local_24dp,
                fragment = { TimelineFragment.newInstance(TimelineViewModel.Kind.PUBLIC_LOCAL) }
            )
            TabKind.FEDERATED -> TabViewData(
                tabData = tabData,
                text = R.string.title_public_federated,
                icon = R.drawable.ic_public_24dp,
                fragment = { TimelineFragment.newInstance(TimelineViewModel.Kind.PUBLIC_FEDERATED) }
            )
            TabKind.DIRECT -> TabViewData(
                tabData = tabData,
                text = R.string.title_direct_messages,
                icon = R.drawable.ic_reblog_direct_24dp,
                fragment = { ConversationsFragment.newInstance() }
            )
            TabKind.TRENDING -> TabViewData(
                tabData = tabData,
                text = R.string.title_public_trending_hashtags,
                icon = R.drawable.ic_trending_up_24px,
                fragment = { TrendingFragment.newInstance() }
            )
            TabKind.HASHTAG -> TabViewData(
                tabData = tabData,
                text = R.string.hashtags,
                icon = R.drawable.ic_hashtag,
                fragment = { args -> TimelineFragment.newHashtagInstance(args) },
                title = { context ->
                    tabData.arguments.joinToString(separator = " ") {
                        context.getString(
                            R.string.title_tag,
                            it
                        )
                    }
                }
            )
            TabKind.LIST -> TabViewData(
                tabData = tabData,
                text = R.string.list,
                icon = R.drawable.ic_list,
                fragment = { args ->
                    TimelineFragment.newInstance(
                        TimelineViewModel.Kind.LIST,
                        args.getOrNull(0).orEmpty()
                    )
                },
                title = { tabData.arguments.getOrNull(1).orEmpty() }
            )
        }
    }
}
