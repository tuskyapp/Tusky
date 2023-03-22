package com.keylesspalace.tusky.appstore

import com.google.gson.Gson
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class CacheUpdater @Inject constructor(
    eventHub: EventHub,
    accountManager: AccountManager,
    appDatabase: AppDatabase,
    gson: Gson
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        val timelineDao = appDatabase.timelineDao()

        scope.launch {
            eventHub.events.collect { event ->
                val accountId = accountManager.activeAccount?.id ?: return@collect
                when (event) {
                    is StatusChangedEvent -> {
                        val status = event.status

                        timelineDao.update(
                            accountId = accountId,
                            statusId = status.id,
                            content = status.content,
                            editedAt = status.editedAt?.time,
                            emojis = gson.toJson(status.emojis),
                            reblogsCount = status.reblogsCount,
                            favouritesCount = status.favouritesCount,
                            repliesCount = status.repliesCount,
                            reblogged = status.reblogged,
                            bookmarked = status.bookmarked,
                            favourited = status.favourited,
                            sensitive = status.sensitive,
                            spoilerText = status.spoilerText,
                            visibility = status.visibility,
                            attachments = gson.toJson(status.attachments),
                            mentions = gson.toJson(status.mentions),
                            tags = gson.toJson(status.tags),
                            poll = gson.toJson(status.poll),
                            muted = status.muted,
                            pinned = status.pinned ?: false,
                            card = gson.toJson(status.card),
                            language = status.language,
                            filtered = status.filtered
                        )
                    }
                    is UnfollowEvent ->
                        timelineDao.removeAllByUser(accountId, event.accountId)
                    is StatusDeletedEvent ->
                        timelineDao.delete(accountId, event.statusId)
                    is PollVoteEvent -> {
                        val pollString = gson.toJson(event.poll)
                        timelineDao.setVoted(accountId, event.statusId, pollString)
                    }
                }
            }
        }
    }

    fun stop() {
        this.scope.cancel()
    }
}
