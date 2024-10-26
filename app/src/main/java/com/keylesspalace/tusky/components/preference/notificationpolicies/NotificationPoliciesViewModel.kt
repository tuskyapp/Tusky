package com.keylesspalace.tusky.components.preference.notificationpolicies

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.entity.NotificationPolicy
import com.keylesspalace.tusky.network.MastodonApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltViewModel
class NotificationPoliciesViewModel @Inject constructor(
    private val api: MastodonApi
) : ViewModel() {

    private val _state: MutableStateFlow<State> = MutableStateFlow(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _error = MutableSharedFlow<Throwable>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val error: SharedFlow<Throwable> = _error.asSharedFlow()

    init {
        loadPolicy()
    }

    fun loadPolicy() {
        _state.value = State.Loading
        viewModelScope.launch {
            api.notificationPolicy().fold({ notificationPolicy ->
                _state.value = State.Loaded(notificationPolicy)
            }, { error ->
                Log.w(TAG, "failed to load notifications policy", error)
                when (error) {
                    is HttpException -> if (error.code() == 404) {
                        _state.value = State.Unsupported
                    } else {
                        _state.value = State.GenericError
                    }
                    else -> {
                        _state.value = State.NetworkError
                    }
                }
            })
        }
    }

    fun updatePolicy(
        forNotFollowing: String? = null,
        forNotFollowers: String? = null,
        forNewAccounts: String? = null,
        forPrivateMentions: String? = null,
        forLimitedAccounts: String? = null
    ) {
        viewModelScope.launch {
            api.updateNotificationPolicy(
                forNotFollowing = forNotFollowing,
                forNotFollowers = forNotFollowers,
                forNewAccounts = forNewAccounts,
                forPrivateMentions = forPrivateMentions,
                forLimitedAccounts = forLimitedAccounts
            ).fold({ notificationPolicy ->
                _state.value = State.Loaded(notificationPolicy)
            }, { error ->
                Log.w(TAG, "failed to update notifications policy", error)
                _error.emit(error)
            })
        }
    }

    companion object {
        private const val TAG = "NotificationPoliciesViewModel"
    }

    sealed class State {
        data object Loading : State()
        data class Loaded(
            val policy: NotificationPolicy
        ) : State()
        data object NetworkError : State()
        data object Unsupported : State()
        data object GenericError : State()
    }
}
