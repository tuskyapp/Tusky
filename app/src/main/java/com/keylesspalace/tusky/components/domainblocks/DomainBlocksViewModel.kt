package com.keylesspalace.tusky.components.domainblocks

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class DomainBlocksViewModel @Inject constructor(
    private val api: MastodonApi
) : ViewModel() {
    val domains: MutableList<String> = mutableListOf()
    val uiEvents = MutableSharedFlow<DomainBlockEvent>()
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
                Log.w(TAG, "Error blocking domain $domain", e)
                uiEvents.emit(DomainBlockEvent.BlockError(domain))
            })
        }
    }

    fun unblock(domain: String) {
        viewModelScope.launch {
            api.unblockDomain(domain).fold({
                domains.remove(domain)
                currentSource?.invalidate()
                uiEvents.emit(DomainBlockEvent.BlockSuccess(domain))
            }, { e ->
                Log.w(TAG, "Error unblocking domain $domain", e)
                uiEvents.emit(DomainBlockEvent.UnblockError(domain))
            })
        }
    }

    companion object {
        private const val TAG = "DomainBlocksViewModel"
    }
}

sealed class DomainBlockEvent {
    data class BlockSuccess(val domain: String) : DomainBlockEvent()
    data class UnblockError(val domain: String) : DomainBlockEvent()
    data class BlockError(val domain: String) : DomainBlockEvent()
}
