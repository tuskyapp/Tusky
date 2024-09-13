package com.keylesspalace.tusky.components.filters

import android.util.Log
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FilterUpdatedEvent
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.isHttpNotFound
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FiltersViewModel @Inject constructor(
    private val api: MastodonApi,
    private val eventHub: EventHub
) : ViewModel() {

    enum class LoadingState {
        INITIAL,
        LOADING,
        LOADED,
        ERROR_NETWORK,
        ERROR_OTHER
    }

    data class State(val filters: List<Filter>, val loadingState: LoadingState)

    private val _state = MutableStateFlow(State(emptyList(), LoadingState.INITIAL))
    val state: StateFlow<State> = _state.asStateFlow()

    private val loadTrigger = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            observeLoad()
        }
    }

    private suspend fun observeLoad() {
        loadTrigger.collectLatest {
            _state.update { it.copy(loadingState = LoadingState.LOADING) }

            api.getFilters().fold(
                { filters ->
                    _state.value = State(filters, LoadingState.LOADED)
                },
                { throwable ->
                    if (throwable.isHttpNotFound()) {
                        Log.i(TAG, "failed loading filters v2, falling back to v1", throwable)

                        api.getFiltersV1().fold(
                            { filters ->
                                _state.value = State(filters.map { it.toFilter() }, LoadingState.LOADED)
                            },
                            { t ->
                                Log.w(TAG, "failed loading filters v1", t)
                                _state.value = State(emptyList(), LoadingState.ERROR_OTHER)
                            }
                        )
                    } else {
                        Log.w(TAG, "failed loading filters v2", throwable)
                        _state.update { it.copy(loadingState = LoadingState.ERROR_NETWORK) }
                    }
                }
            )
        }
    }

    fun reload() {
        loadTrigger.update { it + 1 }
    }

    suspend fun deleteFilter(filter: Filter, parent: View) {
        // First wait for a pending loading operation to complete
        _state.first { it.loadingState > LoadingState.LOADING }

        api.deleteFilter(filter.id).fold(
            {
                _state.update { currentState ->
                    State(
                        currentState.filters.filter { it.id != filter.id },
                        LoadingState.LOADED
                    )
                }
                eventHub.dispatch(FilterUpdatedEvent(filter.context))
            },
            { throwable ->
                if (throwable.isHttpNotFound()) {
                    api.deleteFilterV1(filter.id).fold(
                        {
                            _state.update { currentState ->
                                State(
                                    currentState.filters.filter { it.id != filter.id },
                                    LoadingState.LOADED
                                )
                            }
                        },
                        {
                            Snackbar.make(
                                parent,
                                parent.context.getString(R.string.error_deleting_filter, filter.title),
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    )
                } else {
                    Snackbar.make(
                        parent,
                        parent.context.getString(R.string.error_deleting_filter, filter.title),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    companion object {
        private const val TAG = "FiltersViewModel"
    }
}
