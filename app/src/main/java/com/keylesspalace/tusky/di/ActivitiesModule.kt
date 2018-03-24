package com.keylesspalace.tusky.di

import com.keylesspalace.tusky.*
import dagger.Module
import dagger.android.ContributesAndroidInjector

/**
 * Created by charlag on 3/24/18.
 */

@Module
abstract class ActivitiesModule {
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
    abstract fun contributesModalTimelineActivity(): ModalTimelineActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesViewTagActivity(): ViewTagActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesViewThreadActivity(): ViewThreadActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contributesFavouritesActivity(): FavouritesActivity

    @ContributesAndroidInjector(modules = [FragmentBuildersModule::class])
    abstract fun contribtutesSearchAvtivity(): SearchActivity

    @ContributesAndroidInjector()
    abstract fun contributesAboutActivity(): AboutActivity
}