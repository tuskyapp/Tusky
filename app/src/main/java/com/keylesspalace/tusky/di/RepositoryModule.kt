package com.keylesspalace.tusky.di

import com.google.gson.Gson
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.repository.TimelineRepository
import com.keylesspalace.tusky.repository.TimelineRepositoryImpl
import com.keylesspalace.tusky.util.HtmlConverter
import dagger.Module
import dagger.Provides

@Module
class RepositoryModule {
    @Provides
    fun providesTimelineRepository(db: AppDatabase, mastodonApi: MastodonApi,
                                   accountManager: AccountManager, gson: Gson,
                                   htmlConverter: HtmlConverter): TimelineRepository {
        return TimelineRepositoryImpl(db.timelineDao(), mastodonApi, accountManager, gson,
                htmlConverter)
    }
}