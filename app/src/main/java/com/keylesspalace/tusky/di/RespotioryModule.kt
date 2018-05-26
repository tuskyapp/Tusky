package com.keylesspalace.tusky.di

import com.google.gson.Gson
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.repository.TimelineRepository
import com.keylesspalace.tusky.repository.TimelineRepostiryImpl
import dagger.Module
import dagger.Provides

@Module
class RespotioryModule {
    @Provides
    fun providesTimelineRepository(db: AppDatabase, mastodonApi: MastodonApi,
                                   accountManager: AccountManager, gson: Gson): TimelineRepository {
        return TimelineRepostiryImpl(db.timelineDao(), mastodonApi, accountManager, gson)
    }
}