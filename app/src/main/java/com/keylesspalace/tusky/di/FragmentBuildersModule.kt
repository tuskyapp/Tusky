/* Copyright 2018 charlag
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

package com.keylesspalace.tusky.di

import com.keylesspalace.tusky.AccountsInListFragment
import com.keylesspalace.tusky.components.account.list.ListSelectionFragment
import com.keylesspalace.tusky.components.account.media.AccountMediaFragment
import com.keylesspalace.tusky.components.accountlist.AccountListFragment
import com.keylesspalace.tusky.components.conversation.ConversationsFragment
import com.keylesspalace.tusky.components.domainblocks.DomainBlocksFragment
import com.keylesspalace.tusky.components.preference.AccountPreferencesFragment
import com.keylesspalace.tusky.components.preference.NotificationPreferencesFragment
import com.keylesspalace.tusky.components.preference.PreferencesFragment
import com.keylesspalace.tusky.components.preference.TabFilterPreferencesFragment
import com.keylesspalace.tusky.components.report.fragments.ReportDoneFragment
import com.keylesspalace.tusky.components.report.fragments.ReportNoteFragment
import com.keylesspalace.tusky.components.report.fragments.ReportStatusesFragment
import com.keylesspalace.tusky.components.search.fragments.SearchAccountsFragment
import com.keylesspalace.tusky.components.search.fragments.SearchHashtagsFragment
import com.keylesspalace.tusky.components.search.fragments.SearchStatusesFragment
import com.keylesspalace.tusky.components.timeline.TimelineFragment
import com.keylesspalace.tusky.components.trending.TrendingTagsFragment
import com.keylesspalace.tusky.components.viewthread.ViewThreadFragment
import com.keylesspalace.tusky.components.viewthread.edits.ViewEditsFragment
import com.keylesspalace.tusky.fragment.NotificationsFragment
import com.keylesspalace.tusky.fragment.ViewVideoFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class FragmentBuildersModule {
    @ContributesAndroidInjector
    abstract fun accountListFragment(): AccountListFragment

    @ContributesAndroidInjector
    abstract fun accountMediaFragment(): AccountMediaFragment

    @ContributesAndroidInjector
    abstract fun viewThreadFragment(): ViewThreadFragment

    @ContributesAndroidInjector
    abstract fun viewEditsFragment(): ViewEditsFragment

    @ContributesAndroidInjector
    abstract fun timelineFragment(): TimelineFragment

    @ContributesAndroidInjector
    abstract fun notificationsFragment(): NotificationsFragment

    @ContributesAndroidInjector
    abstract fun notificationPreferencesFragment(): NotificationPreferencesFragment

    @ContributesAndroidInjector
    abstract fun accountPreferencesFragment(): AccountPreferencesFragment

    @ContributesAndroidInjector
    abstract fun conversationsFragment(): ConversationsFragment

    @ContributesAndroidInjector
    abstract fun accountInListsFragment(): AccountsInListFragment

    @ContributesAndroidInjector
    abstract fun reportStatusesFragment(): ReportStatusesFragment

    @ContributesAndroidInjector
    abstract fun reportNoteFragment(): ReportNoteFragment

    @ContributesAndroidInjector
    abstract fun reportDoneFragment(): ReportDoneFragment

    @ContributesAndroidInjector
    abstract fun instanceListFragment(): DomainBlocksFragment

    @ContributesAndroidInjector
    abstract fun searchStatusesFragment(): SearchStatusesFragment

    @ContributesAndroidInjector
    abstract fun searchAccountFragment(): SearchAccountsFragment

    @ContributesAndroidInjector
    abstract fun searchHashtagsFragment(): SearchHashtagsFragment

    @ContributesAndroidInjector
    abstract fun preferencesFragment(): PreferencesFragment

    @ContributesAndroidInjector
    abstract fun listsForAccountFragment(): ListSelectionFragment

    @ContributesAndroidInjector
    abstract fun trendingTagsFragment(): TrendingTagsFragment

    @ContributesAndroidInjector
    abstract fun viewVideoFragment(): ViewVideoFragment

    @ContributesAndroidInjector
    abstract fun tabFilterPreferencesFragment(): TabFilterPreferencesFragment
}
