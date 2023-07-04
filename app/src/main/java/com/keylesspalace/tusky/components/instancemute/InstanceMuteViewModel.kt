package com.keylesspalace.tusky.components.instancemute

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

class InstanceMuteViewModel @Inject constructor(
    private val api: MastodonApi
) : ViewModel() {
    val domains: MutableList<String> = mutableListOf()
    val uiEvents = MutableSharedFlow<InstanceMuteEvent>()
    var nextKey: String? = null
    var currentSource: InstanceMutePagingSource? = null

    @OptIn(ExperimentalPagingApi::class)
    val pager = Pager(
        config = PagingConfig(pageSize = 20),
        remoteMediator = InstanceMuteRemoteMediator(api, this),
        pagingSourceFactory = {
            InstanceMutePagingSource(
                viewModel = this
            ).also { source ->
                currentSource = source
            }
        }
    ).flow.cachedIn(viewModelScope)

    fun mute(domain: String) {
        viewModelScope.launch {
            api.blockDomain(domain).fold({
                domains.add(domain)
                currentSource?.invalidate()
            }, { e ->
                Log.w(TAG, "Error muting domain $domain", e)
                uiEvents.emit(InstanceMuteEvent.MuteError(domain))
            })
        }
    }

    fun unmute(domain: String) {
        viewModelScope.launch {
            api.unblockDomain(domain).fold({
                domains.remove(domain)
                currentSource?.invalidate()
                uiEvents.emit(InstanceMuteEvent.UnmuteSuccess(domain))
            }, { e ->
                Log.w(TAG, "Error unmuting domain $domain", e)
                uiEvents.emit(InstanceMuteEvent.UnmuteError(domain))
            })
        }
    }

    companion object {
        private const val TAG = "InstanceMuteViewModel"
    }
}

sealed class InstanceMuteEvent {
    data class UnmuteSuccess(val domain: String) : InstanceMuteEvent()
    data class UnmuteError(val domain: String) : InstanceMuteEvent()
    data class MuteError(val domain: String) : InstanceMuteEvent()
}
