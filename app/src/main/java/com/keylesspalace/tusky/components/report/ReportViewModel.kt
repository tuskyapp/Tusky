/* Copyright 2019 Joel Pyska
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

package com.keylesspalace.tusky.components.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.components.report.adapter.StatusesPagingSource
import com.keylesspalace.tusky.components.report.model.StatusViewState
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.Error
import com.keylesspalace.tusky.util.Loading
import com.keylesspalace.tusky.util.Resource
import com.keylesspalace.tusky.util.Success
import com.keylesspalace.tusky.util.toViewData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub
) : ViewModel() {

    private val navigationMutable = MutableStateFlow(null as Screen?)
    val navigation: StateFlow<Screen?> = navigationMutable.asStateFlow()

    private val muteStateMutable = MutableStateFlow(null as Resource<Boolean>?)
    val muteState: StateFlow<Resource<Boolean>?> = muteStateMutable.asStateFlow()

    private val blockStateMutable = MutableStateFlow(null as Resource<Boolean>?)
    val blockState: StateFlow<Resource<Boolean>?> = blockStateMutable.asStateFlow()

    private val reportingStateMutable = MutableStateFlow(null as Resource<Boolean>?)
    var reportingState: StateFlow<Resource<Boolean>?> = reportingStateMutable.asStateFlow()

    private val checkUrlMutable = MutableStateFlow(null as String?)
    val checkUrl: StateFlow<String?> = checkUrlMutable.asStateFlow()

    private val accountIdFlow = MutableSharedFlow<String>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val statusesFlow = accountIdFlow.flatMapLatest { accountId ->
        Pager(
            initialKey = statusId,
            config = PagingConfig(
                pageSize = 20,
                initialLoadSize = 20
            ),
            pagingSourceFactory = { StatusesPagingSource(accountId, mastodonApi) }
        ).flow
    }
        .map { pagingData ->
            /* TODO: refactor reports to use the isShowingContent / isExpanded / isCollapsed attributes from StatusViewData.Concrete
             instead of StatusViewState */
            pagingData.map { status -> status.toViewData(false, false, false, filter = null) }
        }
        .cachedIn(viewModelScope)

    private val selectedIds = HashSet<String>()
    val statusViewState = StatusViewState()

    var reportNote: String = ""
    var isRemoteNotify = false

    private var statusId: String? = null
    lateinit var accountUserName: String
    lateinit var accountId: String
    var isRemoteAccount: Boolean = false
    var remoteServer: String? = null

    fun init(accountId: String, userName: String, statusId: String?) {
        this.accountId = accountId
        this.accountUserName = userName
        this.statusId = statusId
        statusId?.let {
            selectedIds.add(it)
        }

        isRemoteAccount = userName.contains('@')
        if (isRemoteAccount) {
            remoteServer = userName.substring(userName.indexOf('@') + 1)
        }

        obtainRelationship()

        viewModelScope.launch {
            accountIdFlow.emit(accountId)
        }
    }

    fun navigateTo(screen: Screen) {
        navigationMutable.value = screen
    }

    fun navigated() {
        navigationMutable.value = null
    }

    private fun obtainRelationship() {
        val ids = listOf(accountId)
        muteStateMutable.value = Loading()
        blockStateMutable.value = Loading()
        viewModelScope.launch {
            mastodonApi.relationships(ids).fold(
                { data ->
                    updateRelationship(data.getOrNull(0))
                },
                {
                    updateRelationship(null)
                }
            )
        }
    }

    private fun updateRelationship(relationship: Relationship?) {
        if (relationship != null) {
            muteStateMutable.value = Success(relationship.muting)
            blockStateMutable.value = Success(relationship.blocking)
        } else {
            muteStateMutable.value = Error(false)
            blockStateMutable.value = Error(false)
        }
    }

    fun toggleMute() {
        val alreadyMuted = muteStateMutable.value?.data == true
        viewModelScope.launch {
            if (alreadyMuted) {
                mastodonApi.unmuteAccount(accountId)
            } else {
                mastodonApi.muteAccount(accountId)
            }.fold(
                { relationship ->
                    val muting = relationship.muting
                    muteStateMutable.value = Success(muting)
                    if (muting) {
                        eventHub.dispatch(MuteEvent(accountId))
                    }
                },
                { t ->
                    muteStateMutable.value = Error(false, t.message)
                }
            )
        }

        muteStateMutable.value = Loading()
    }

    fun toggleBlock() {
        val alreadyBlocked = blockStateMutable.value?.data == true
        viewModelScope.launch {
            if (alreadyBlocked) {
                mastodonApi.unblockAccount(accountId)
            } else {
                mastodonApi.blockAccount(accountId)
            }.fold({ relationship ->
                val blocking = relationship.blocking
                blockStateMutable.value = Success(blocking)
                if (blocking) {
                    eventHub.dispatch(BlockEvent(accountId))
                }
            }, { t ->
                blockStateMutable.value = Error(false, t.message)
            })
        }
        blockStateMutable.value = Loading()
    }

    fun doReport() {
        reportingStateMutable.value = Loading()
        viewModelScope.launch {
            mastodonApi.report(
                accountId,
                selectedIds.toList(),
                reportNote,
                if (isRemoteAccount) isRemoteNotify else null
            )
                .fold({
                    reportingStateMutable.value = Success(true)
                }, { error ->
                    reportingStateMutable.value = Error(cause = error)
                })
        }
    }

    fun checkClickedUrl(url: String?) {
        checkUrlMutable.value = url
    }

    fun urlChecked() {
        checkUrlMutable.value = null
    }

    fun setStatusChecked(status: Status, checked: Boolean) {
        if (checked) {
            selectedIds.add(status.id)
        } else {
            selectedIds.remove(status.id)
        }
    }

    fun isStatusChecked(id: String): Boolean {
        return selectedIds.contains(id)
    }
}
