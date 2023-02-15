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

import com.keylesspalace.tusky.AboutActivity
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.EditProfileActivity
import com.keylesspalace.tusky.FiltersActivity
import com.keylesspalace.tusky.LicenseActivity
import com.keylesspalace.tusky.ListsActivity
import com.keylesspalace.tusky.MainActivity
import com.keylesspalace.tusky.SplashActivity
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.TabPreferenceActivity
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.components.accountlist.AccountListActivity
import com.keylesspalace.tusky.components.announcements.AnnouncementsActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.components.drafts.DraftsActivity
import com.keylesspalace.tusky.components.followedtags.FollowedTagsActivity
import com.keylesspalace.tusky.components.instancemute.InstanceListActivity
import com.keylesspalace.tusky.components.login.LoginActivity
import com.keylesspalace.tusky.components.login.LoginWebViewActivity
import com.keylesspalace.tusky.components.preference.PreferencesActivity
import com.keylesspalace.tusky.components.report.ReportActivity
import com.keylesspalace.tusky.components.scheduled.ScheduledStatusActivity
import com.keylesspalace.tusky.components.search.SearchActivity
import com.keylesspalace.tusky.components.trending.TrendingActivity
import com.keylesspalace.tusky.components.viewthread.ViewThreadActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector

/**
 * Created by charlag on 3/24/18.
 */

@Module
abstract class ActivitiesModule {

    @ContributesAndroidInjector
    abstract fun contributesBaseActivity(): BaseActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesMainActivity(): MainActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesAccountActivity(): AccountActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesListsActivity(): ListsActivity

    @ContributesAndroidInjector
    abstract fun contributesComposeActivity(): ComposeActivity

    @ContributesAndroidInjector
    abstract fun contributesEditProfileActivity(): EditProfileActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesAccountListActivity(): AccountListActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesViewThreadActivity(): ViewThreadActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesStatusListActivity(): StatusListActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesSearchActivity(): SearchActivity

    @ContributesAndroidInjector
    abstract fun contributesAboutActivity(): AboutActivity

    @ContributesAndroidInjector
    abstract fun contributesLoginActivity(): LoginActivity

    @ContributesAndroidInjector
    abstract fun contributesLoginWebViewActivity(): LoginWebViewActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesPreferencesActivity(): PreferencesActivity

    @ContributesAndroidInjector
    abstract fun contributesViewMediaActivity(): ViewMediaActivity

    @ContributesAndroidInjector
    abstract fun contributesLicenseActivity(): LicenseActivity

    @ContributesAndroidInjector
    abstract fun contributesTabPreferenceActivity(): TabPreferenceActivity

    @ContributesAndroidInjector
    abstract fun contributesFiltersActivity(): FiltersActivity

    @ContributesAndroidInjector
    abstract fun contributesFollowedTagsActivity(): FollowedTagsActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesReportActivity(): ReportActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesInstanceListActivity(): InstanceListActivity

    @ContributesAndroidInjector
    abstract fun contributesScheduledStatusActivity(): ScheduledStatusActivity

    @ContributesAndroidInjector
    abstract fun contributesAnnouncementsActivity(): AnnouncementsActivity

    @ContributesAndroidInjector
    abstract fun contributesDraftActivity(): DraftsActivity

    @ContributesAndroidInjector
    abstract fun contributesSplashActivity(): SplashActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesTrendingActivity(): TrendingActivity
}
