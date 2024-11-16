package com.keylesspalace.tusky.components.notifications.requests

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.entity.NotificationRequest
import com.keylesspalace.tusky.network.MastodonApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@HiltViewModel
class NotificationRequestsViewModel @Inject constructor(
    private val api: MastodonApi
) : ViewModel() {

    var currentSource: NotificationRequestsPagingSource? = null

    val requestData: MutableList<NotificationRequest> = mutableListOf()

    var nextKey: String? = null

    @OptIn(ExperimentalPagingApi::class)
    val pager = Pager(
        config = PagingConfig(
            pageSize = 20,
            initialLoadSize = 20
        ),
        remoteMediator = NotificationRequestsRemoteMediator(api, this),
        pagingSourceFactory = {
            NotificationRequestsPagingSource(
                requests = requestData,
                nextKey = nextKey
            ).also { source ->
                currentSource = source
            }
        }
    ).flow
        .cachedIn(viewModelScope)

    private val _error = MutableSharedFlow<Throwable>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val error: SharedFlow<Throwable> = _error.asSharedFlow()

    fun acceptNotificationRequest(id: String) {
        viewModelScope.launch{
            api.acceptNotificationRequest(id).fold({
                requestData.removeAll { request -> request.id == id }
                currentSource?.invalidate()
            }, { error ->
                Log.w(TAG, "failed to dismiss notifications request", error)
                _error.emit(error)
            })
        }
    }

    fun dismissNotificationRequest(id: String) {
        viewModelScope.launch{
            api.dismissNotificationRequest(id).fold({
                requestData.removeAll { request -> request.id == id }
                currentSource?.invalidate()
            }, { error ->
                Log.w(TAG, "failed to dismiss notifications request", error)
                _error.emit(error)
            })
        }
    }

    companion object {
        private const val TAG = "NotificationRequestsViewModel"
    }
}
