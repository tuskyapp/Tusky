package com.keylesspalace.tusky.repository

import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.TimelineDao
import com.keylesspalace.tusky.network.MastodonApi
import dagger.Module

@Module
class RepositoryModule {
    fun providesTimelineRepository(timelineDao: TimelineDao, api: MastodonApi,
                                   accountManager: AccountManager): TimelineRepository {
        return TimelineRepostiryImpl(timelineDao, api, accountManager)
    }
}

