package com.keylesspalace.tusky.components.filters

import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.isHttpNotFound
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun load() {
        this@FiltersViewModel._state.value = _state.value.copy(loadingState = LoadingState.LOADING)

        viewModelScope.launch {
            api.getFilters().fold(
                { filters ->
                    this@FiltersViewModel._state.value = State(filters, LoadingState.LOADED)
                },
                { throwable ->
                    if (throwable.isHttpNotFound()) {
                        api.getFiltersV1().fold(
                            { filters ->
                                this@FiltersViewModel._state.value = State(filters.map { it.toFilter() }, LoadingState.LOADED)
                            },
                            { _ ->
                                // TODO log errors (also below)
                                this@FiltersViewModel._state.value = _state.value.copy(loadingState = LoadingState.ERROR_OTHER)
                            }
                        )
                        this@FiltersViewModel._state.value = _state.value.copy(loadingState = LoadingState.ERROR_OTHER)
                    } else {
                        this@FiltersViewModel._state.value = _state.value.copy(loadingState = LoadingState.ERROR_NETWORK)
                    }
                }
            )
        }
    }

    fun deleteFilter(filter: Filter, parent: View) {
        viewModelScope.launch {
            api.deleteFilter(filter.id).fold(
                {
                    this@FiltersViewModel._state.value = State(
                        this@FiltersViewModel._state.value.filters.filter {
                            it.id != filter.id
                        },
                        LoadingState.LOADED
                    )
                    for (context in filter.context) {
                        eventHub.dispatch(PreferenceChangedEvent(context))
                    }
                },
                { throwable ->
                    if (throwable.isHttpNotFound()) {
                        api.deleteFilterV1(filter.id).fold(
                            {
                                this@FiltersViewModel._state.value = State(
                                    this@FiltersViewModel._state.value.filters.filter {
                                        it.id != filter.id
                                    },
                                    LoadingState.LOADED
                                )
                            },
                            {
                                Snackbar.make(
                                    parent,
                                    "Error deleting filter '${filter.title}'",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                        )
                    } else {
                        Snackbar.make(
                            parent,
                            "Error deleting filter '${filter.title}'",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }
}
