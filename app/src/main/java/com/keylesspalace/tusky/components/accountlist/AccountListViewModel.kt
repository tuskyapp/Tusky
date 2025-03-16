package com.keylesspalace.tusky.components.accountlist

import android.view.View
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.domainblocks.DomainBlocksPagingSource
import com.keylesspalace.tusky.components.domainblocks.DomainBlocksRemoteMediator
import com.keylesspalace.tusky.components.notifications.requests.details.NotificationRequestDetailsViewModel
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.TimelineAccount
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

    private var factory = InvalidatingPagingSourceFactory {
        AccountListPagingSource(accounts.toList(), nextKey)
    }

    @OptIn(ExperimentalPagingApi::class)
    val domainPager = Pager(
        config = PagingConfig(40),
        remoteMediator = AccountListRemoteMediator(api, this),
        pagingSourceFactory = factory
    ).flow
        .cachedIn(viewModelScope)

    val accounts: MutableList<TimelineAccount> = mutableListOf()
    var nextKey: String? = null

    private val _uiEvents = MutableSharedFlow<SnackbarEvent>()
    val uiEvents: SharedFlow<SnackbarEvent> = _uiEvents.asSharedFlow()

    fun invalidate () {
        factory.invalidate()
    }

    fun mute(accountId: String, notifications: Boolean) {
        val account = accounts.find { it.id == accountId } ?: return
        viewModelScope.launch {
            api.muteAccount(accountId, notifications).fold ({
                accounts.removeIf { it.id == accountId }
                _uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.error_blocking_domain,
                        user = "@${account.username}",
                        throwable = null,
                        actionText = R.string.action_undo,
                        action = { mute(accountId, notifications) }
                    )
                )
            }, { e ->
                _uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.error_blocking_domain,
                        user = "@${account.username}",
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { mute(accountId, notifications) }
                    )
                )
            })
        }
    }

    /*fun unmute(account: TimelineAccount) {
        viewModelScope.launch {
            api.unmuteAccount(id).fold({
                    accounts.
                }, { error ->
                    _uiEvents.emit(
                        SnackbarEvent(
                            message = R.string.error_blocking_domain,
                            user = user,
                            throwable = error,
                            actionText = R.string.action_retry,
                            action = { onMute(mute, id, position, notifications) }
                        )
                    )
                })
        }
    }*/

    fun onBlock(block: Boolean, id: String, position: Int) {

    }
    fun onRespondToFollowRequest(accept: Boolean, accountIdRequestingFollow: String, position: Int) {

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
    val throwable: Throwable? = null,
    )
