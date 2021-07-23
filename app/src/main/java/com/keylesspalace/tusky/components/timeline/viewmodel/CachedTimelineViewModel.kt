package com.keylesspalace.tusky.components.timeline.viewmodel

import android.content.SharedPreferences
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import com.google.gson.Gson
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.components.timeline.TimelineRemoteMediator
import com.keylesspalace.tusky.components.timeline.TimelineRepository
import com.keylesspalace.tusky.components.timeline.toStatus
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CachedTimelineViewModel @Inject constructor(
    private val timelineCases: TimelineCases,
    private val api: MastodonApi,
    private val eventHub: EventHub,
    private val accountManager: AccountManager,
    private val sharedPreferences: SharedPreferences,
    private val filterModel: FilterModel,
    private val db: AppDatabase,
    private val gson: Gson
) : TimelineViewModel(timelineCases, api, eventHub, accountManager, sharedPreferences, filterModel) {

    @ExperimentalPagingApi
    override val statuses = Pager(
        config = PagingConfig(pageSize = 10),
        remoteMediator = TimelineRemoteMediator(accountManager.activeAccount!!.id, api, db, gson),
        pagingSourceFactory = { db.timelineDao().getStatusesForAccount(accountManager.activeAccount!!.id) }
    ).flow
        .map { it.map { item ->
            when (val status = item.toStatus(gson)) {
                is Either.Right -> status.value.toViewData(
                    alwaysShowSensitiveMedia,
                    alwaysOpenSpoilers
                )
                is Either.Left -> StatusViewData.Placeholder(status.value.id, false)
            }
        }
        }

    override fun updatePoll(status: StatusViewData.Concrete, newPoll: Poll) {
        TODO("Not yet implemented")
    }

    override fun changeExpanded(expanded: Boolean, status: StatusViewData.Concrete) {
        TODO("Not yet implemented")
    }

    override fun changeContentHidden(isShowing: Boolean, status: StatusViewData.Concrete) {
        TODO("Not yet implemented")
    }

    override fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData.Concrete) {
        TODO("Not yet implemented")
    }

    override fun removeAllByAccountId(accountId: String) {
        TODO("Not yet implemented")
    }

    override fun removeAllByInstance(instance: String) {
        TODO("Not yet implemented")
    }

    override fun handleReblogEvent(reblogEvent: ReblogEvent) {
        TODO("Not yet implemented")
    }

    override fun handleFavEvent(favEvent: FavoriteEvent) {
        TODO("Not yet implemented")
    }

    override fun handleBookmarkEvent(bookmarkEvent: BookmarkEvent) {
        TODO("Not yet implemented")
    }

    override fun handlePinEvent(pinEvent: PinEvent) {
        TODO("Not yet implemented")
    }
}

