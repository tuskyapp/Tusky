package tech.bigfig.roma.di

import com.google.gson.Gson
import tech.bigfig.roma.db.AccountManager
import tech.bigfig.roma.db.AppDatabase
import tech.bigfig.roma.network.MastodonApi
import tech.bigfig.roma.repository.TimelineRepository
import tech.bigfig.roma.repository.TimelineRepositoryImpl
import tech.bigfig.roma.util.HtmlConverter
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