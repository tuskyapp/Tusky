package com.keylesspalace.tusky.components.accountlist

import android.view.View
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.onFailure
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.network.MastodonApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = AccountListViewModel.Factory::class)
class AccountListViewModel @AssistedInject constructor(
    private val api: MastodonApi,
    @Assisted("type") val type: AccountListActivity.Type,
    @Assisted("id") val accountId: String?
) : ViewModel() {

    private val factory = InvalidatingPagingSourceFactory {
        AccountListPagingSource(accounts.toList(), nextKey)
    }

    @OptIn(ExperimentalPagingApi::class)
    val accountPager = Pager(
        config = PagingConfig(40),
        remoteMediator = AccountListRemoteMediator(api, this, fetchRelationships = type == AccountListActivity.Type.MUTES),
        pagingSourceFactory = factory
    ).flow
        .cachedIn(viewModelScope)

    val accounts: MutableList<AccountViewData> = mutableListOf()
    var nextKey: String? = null

    private val _uiEvents = MutableSharedFlow<SnackbarEvent>()
    val uiEvents: SharedFlow<SnackbarEvent> = _uiEvents.asSharedFlow()

    fun invalidate() {
        factory.invalidate()
    }

    // this is called by the mute notification toggle
    fun mute(accountId: String, notifications: Boolean) {
        val accountViewData = accounts.find { it.id == accountId } ?: return
        viewModelScope.launch {
            api.muteAccount(accountId, notifications).onFailure { e ->
                _uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.error_blocking_domain,
                        user = "@${accountViewData.account.username}",
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { mute(accountId, notifications) }
                    )
                )
            }
        }
    }

    // this is called when unmuting is undone
    private fun remute(accountViewData: AccountViewData) {
        viewModelScope.launch {
            api.muteAccount(accountViewData.id).fold({
                accounts.add(accountViewData)
                invalidate()
            }, { e ->
                _uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.mute_failure,
                        user = "@${accountViewData.account.username}",
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { block(accountViewData) }
                    )
                )
            })
        }
    }

    fun unmute(accountId: String) {
        val accountViewData = accounts.find { it.id == accountId } ?: return
        viewModelScope.launch {
            api.unmuteAccount(accountId).fold({
                accounts.removeIf { it.id == accountId }
                invalidate()
                _uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.unmute_success,
                        user = "@${accountViewData.account.username}",
                        throwable = null,
                        actionText = R.string.action_undo,
                        action = { remute(accountViewData) }
                    )
                )
            }, { error ->
                _uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.unmute_failure,
                        user = "@${accountViewData.account.username}",
                        throwable = error,
                        actionText = R.string.action_retry,
                        action = { unmute(accountId) }
                    )
                )
            })
        }
    }

    fun unblock(accountId: String) {
        val accountViewData = accounts.find { it.id == accountId } ?: return
        viewModelScope.launch {
            api.unblockAccount(accountId).fold({
                accounts.removeIf { it.id == accountId }
                invalidate()
                _uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.unblock_success,
                        user = "@${accountViewData.account.username}",
                        throwable = null,
                        actionText = R.string.action_undo,
                        action = { block(accountViewData) }
                    )
                )
            }, { e ->
                _uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.unblock_failure,
                        user = "@${accountViewData.account.username}",
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { unblock(accountId) }
                    )
                )
            })
        }
    }

    private fun block(accountViewData: AccountViewData) {
        viewModelScope.launch {
            api.blockAccount(accountViewData.id).fold({
                accounts.add(accountViewData)
                invalidate()
            }, { e ->
                _uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.block_failure,
                        user = "@${accountViewData.account.username}",
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { block(accountViewData) }
                    )
                )
            })
        }
    }

    fun respondToFollowRequest(accept: Boolean, accountId: String) {
        val accountViewData = accounts.find { it.id == accountId } ?: return
        viewModelScope.launch {
            if (accept) {
                api.authorizeFollowRequest(accountId)
            } else {
                api.rejectFollowRequest(accountId)
            }.fold({
                accounts.removeIf { it.id == accountId }
                invalidate()
            }, { e ->
                _uiEvents.emit(
                    SnackbarEvent(
                        message = if (accept) R.string.accept_follow_request_failure else R.string.reject_follow_request_failure,
                        user = "@${accountViewData.account.username}",
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { respondToFollowRequest(accept, accountId) }
                    )
                )
            })
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("type") type: AccountListActivity.Type,
            @Assisted("id") accountId: String?
        ): AccountListViewModel
    }
}

class SnackbarEvent(
    @StringRes val message: Int,
    val user: String,
    @StringRes val actionText: Int,
    val action: (View) -> Unit,
    val throwable: Throwable? = null
)
