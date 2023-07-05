package com.keylesspalace.tusky.components.domainblocks

import android.view.View
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class DomainBlocksViewModel @Inject constructor(
    private val api: MastodonApi
) : ViewModel() {
    val domains: MutableList<String> = mutableListOf()
    val uiEvents = MutableSharedFlow<SnackbarEvent>()
    var nextKey: String? = null
    var currentSource: DomainBlocksPagingSource? = null

    @OptIn(ExperimentalPagingApi::class)
    val pager = Pager(
        config = PagingConfig(pageSize = 20),
        remoteMediator = DomainBlocksRemoteMediator(api, this),
        pagingSourceFactory = {
            DomainBlocksPagingSource(
                viewModel = this
            ).also { source ->
                currentSource = source
            }
        }
    ).flow.cachedIn(viewModelScope)

    fun block(domain: String) {
        viewModelScope.launch {
            api.blockDomain(domain).fold({
                domains.add(domain)
                currentSource?.invalidate()
            }, { e ->
                uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.error_blocking_domain,
                        domain = domain,
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { block(domain) }
                    )
                )
            })
        }
    }

    fun unblock(domain: String) {
        viewModelScope.launch {
            api.unblockDomain(domain).fold({
                domains.remove(domain)
                currentSource?.invalidate()
                uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.confirmation_domain_unmuted,
                        domain = domain,
                        throwable = null,
                        actionText = R.string.action_undo,
                        action = { block(domain) }
                    )
                )
            }, { e ->
                uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.error_unblocking_domain,
                        domain = domain,
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { unblock(domain) }
                    )
                )
            })
        }
    }
}

class SnackbarEvent(
    @StringRes val message: Int,
    val domain: String,
    val throwable: Throwable?,
    @StringRes val actionText: Int,
    val action: (View) -> Unit
)
