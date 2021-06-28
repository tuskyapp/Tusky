/* Copyright 2021 Tusky Contributors
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

package com.keylesspalace.tusky.components.search

import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.keylesspalace.tusky.components.search.adapter.SearchPagingSourceFactory
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.DeletedStatus
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.RxAwareViewModel
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

class SearchViewModel @Inject constructor(
    mastodonApi: MastodonApi,
    private val timelineCases: TimelineCases,
    private val accountManager: AccountManager
) : RxAwareViewModel() {

    var currentQuery: String = ""

    var activeAccount: AccountEntity?
        get() = accountManager.activeAccount
        set(value) {
            accountManager.activeAccount = value
        }

    val mediaPreviewEnabled = activeAccount?.mediaPreviewEnabled ?: false
    val alwaysShowSensitiveMedia = activeAccount?.alwaysShowSensitiveMedia ?: false
    val alwaysOpenSpoiler = activeAccount?.alwaysOpenSpoiler ?: false

    private val loadedStatuses: MutableList<Pair<Status, StatusViewData.Concrete>> = mutableListOf()

    private val statusesPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Status, loadedStatuses) {
        it.statuses.map { status -> Pair(status, status.toViewData(alwaysShowSensitiveMedia, alwaysOpenSpoiler)) }
            .apply {
                loadedStatuses.addAll(this)
            }
    }
    private val accountsPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Account) {
        it.accounts
    }
    private val hashtagsPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Hashtag) {
        it.hashtags
    }

    val statusesFlow = Pager(
        config = PagingConfig(pageSize = DEFAULT_LOAD_SIZE, initialLoadSize = DEFAULT_LOAD_SIZE),
        pagingSourceFactory = statusesPagingSourceFactory
    ).flow
        .cachedIn(viewModelScope)

    val accountsFlow = Pager(
        config = PagingConfig(pageSize = DEFAULT_LOAD_SIZE, initialLoadSize = DEFAULT_LOAD_SIZE),
        pagingSourceFactory = accountsPagingSourceFactory
    ).flow
        .cachedIn(viewModelScope)

    val hashtagsFlow = Pager(
        config = PagingConfig(pageSize = DEFAULT_LOAD_SIZE, initialLoadSize = DEFAULT_LOAD_SIZE),
        pagingSourceFactory = hashtagsPagingSourceFactory
    ).flow
        .cachedIn(viewModelScope)

    fun search(query: String) {
        loadedStatuses.clear()
        statusesPagingSourceFactory.newSearch(query)
        accountsPagingSourceFactory.newSearch(query)
        hashtagsPagingSourceFactory.newSearch(query)
    }

    fun removeItem(status: Pair<Status, StatusViewData.Concrete>) {
        timelineCases.delete(status.first.id)
            .subscribe(
                {
                    if (loadedStatuses.remove(status))
                        statusesPagingSourceFactory.invalidate()
                },
                {
                    err ->
                    Log.d(TAG, "Failed to delete status", err)
                }
            )
            .autoDispose()
    }

    fun expandedChange(status: Pair<Status, StatusViewData.Concrete>, expanded: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            loadedStatuses[idx] = Pair(status.first, status.second.copy(isExpanded = expanded))
            statusesPagingSourceFactory.invalidate()
        }
    }

    fun reblog(status: Pair<Status, StatusViewData.Concrete>, reblog: Boolean) {
        timelineCases.reblog(status.first.id, reblog)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { setRebloggedForStatus(status, reblog) },
                { t -> Log.d(TAG, "Failed to reblog status ${status.first.id}", t) }
            )
            .autoDispose()
    }

    private fun setRebloggedForStatus(status: Pair<Status, StatusViewData.Concrete>, reblog: Boolean) {
        status.first.reblogged = reblog
        status.first.reblog?.reblogged = reblog
        statusesPagingSourceFactory.invalidate()
    }

    fun contentHiddenChange(status: Pair<Status, StatusViewData.Concrete>, isShowing: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            loadedStatuses[idx] = Pair(status.first, status.second.copy(isShowingContent = isShowing))
            statusesPagingSourceFactory.invalidate()
        }
    }

    fun collapsedChange(status: Pair<Status, StatusViewData.Concrete>, collapsed: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            loadedStatuses[idx] = Pair(status.first, status.second.copy(isCollapsed = collapsed))
            statusesPagingSourceFactory.invalidate()
        }
    }

    fun voteInPoll(status: Pair<Status, StatusViewData.Concrete>, choices: MutableList<Int>) {
        val votedPoll = status.first.actionableStatus.poll!!.votedCopy(choices)
        updateStatus(status, votedPoll)
        timelineCases.voteInPoll(status.first.id, votedPoll.id, choices)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { newPoll -> updateStatus(status, newPoll) },
                { t -> Log.d(TAG, "Failed to vote in poll: ${status.first.id}", t) }
            )
            .autoDispose()
    }

    private fun updateStatus(status: Pair<Status, StatusViewData.Concrete>, newPoll: Poll) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            val newStatus = status.first.copy(poll = newPoll)
            val newViewData = status.second.copy(status = newStatus)
            loadedStatuses[idx] = Pair(newStatus, newViewData)
            statusesPagingSourceFactory.invalidate()
        }
    }

    fun favorite(status: Pair<Status, StatusViewData.Concrete>, isFavorited: Boolean) {
        status.first.favourited = isFavorited
        statusesPagingSourceFactory.invalidate()
        timelineCases.favourite(status.first.id, isFavorited)
            .onErrorReturnItem(status.first)
            .subscribe()
            .autoDispose()
    }

    fun bookmark(status: Pair<Status, StatusViewData.Concrete>, isBookmarked: Boolean) {
        status.first.bookmarked = isBookmarked
        statusesPagingSourceFactory.invalidate()
        timelineCases.bookmark(status.first.id, isBookmarked)
            .onErrorReturnItem(status.first)
            .subscribe()
            .autoDispose()
    }

    fun getAllAccountsOrderedByActive(): List<AccountEntity> {
        return accountManager.getAllAccountsOrderedByActive()
    }

    fun muteAccount(accountId: String, notifications: Boolean, duration: Int?) {
        timelineCases.mute(accountId, notifications, duration)
    }

    fun pinAccount(status: Status, isPin: Boolean) {
        timelineCases.pin(status.id, isPin)
    }

    fun blockAccount(accountId: String) {
        timelineCases.block(accountId)
    }

    fun deleteStatus(id: String): Single<DeletedStatus> {
        return timelineCases.delete(id)
    }

    fun muteConversation(status: Pair<Status, StatusViewData.Concrete>, mute: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            val newStatus = status.first.copy(muted = mute)
            val newPair = Pair(
                newStatus,
                status.second.copy(status = newStatus)
            )
            loadedStatuses[idx] = newPair
            statusesPagingSourceFactory.invalidate()
        }
        timelineCases.muteConversation(status.first.id, mute)
            .onErrorReturnItem(status.first)
            .subscribe()
            .autoDispose()
    }

    companion object {
        private const val TAG = "SearchViewModel"
        private const val DEFAULT_LOAD_SIZE = 20
    }
}
