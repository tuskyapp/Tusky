package com.keylesspalace.tusky.components.conversation

import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.network.MastodonApi
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationsRepository @Inject constructor(val mastodonApi: MastodonApi, val db: AppDatabase) {

    fun deleteCacheForAccount(accountId: Long) {
        Single.fromCallable {
            db.conversationDao().deleteForAccount(accountId)
        }.subscribeOn(Schedulers.io())
                .subscribe()
    }

}