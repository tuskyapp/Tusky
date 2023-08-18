package com.keylesspalace.tusky.components.filters

import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterV1
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

class FiltersViewModel @Inject constructor(
    private val api: MastodonApi,
    private val eventHub: EventHub
) : ViewModel() {

    enum class LoadingState {
        INITIAL, LOADING, LOADED, ERROR_NETWORK, ERROR_OTHER
    }

    data class State(val filters: List<Filter>, val loadingState: LoadingState)

    val state: Flow<State> get() = _state
    private val _state = MutableStateFlow(State(emptyList(), LoadingState.INITIAL))

    // TODO: Now that FilterRepository exists this code should be updated to use that.
    fun load() {
        this@FiltersViewModel._state.value = _state.value.copy(loadingState = LoadingState.LOADING)

        viewModelScope.launch {
            api.getFilters().fold(
                { filters ->
                    this@FiltersViewModel._state.value = State(filters, LoadingState.LOADED)
                },
                { throwable ->
                    if (throwable is HttpException && throwable.code() == 404) {
                        api.getFiltersV1().fold(
                            { filters ->
                                this@FiltersViewModel._state.value = State(filters.map { it.toFilter() }, LoadingState.LOADED)
                            },
                            { throwable ->
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
                    this@FiltersViewModel._state.value = State(this@FiltersViewModel._state.value.filters.filter { it.id != filter.id }, LoadingState.LOADED)
                    for (context in filter.context) {
                        eventHub.dispatch(PreferenceChangedEvent(context))
                    }
                },
                { throwable ->
                    if (throwable is HttpException && throwable.code() == 404) {
                        api.deleteFilterV1(filter.id).fold(
                            {
                                this@FiltersViewModel._state.value = State(this@FiltersViewModel._state.value.filters.filter { it.id != filter.id }, LoadingState.LOADED)
                            },
                            {
                                Snackbar.make(parent, "Error deleting filter '${filter.title}'", Snackbar.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        Snackbar.make(parent, "Error deleting filter '${filter.title}'", Snackbar.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    companion object {
        /** Preference keys that, if changed, indicate that a filter preference has changed */
        public val FILTER_PREF_KEYS = setOf(
            FilterV1.HOME,
            FilterV1.NOTIFICATIONS,
            FilterV1.THREAD,
            FilterV1.PUBLIC,
            FilterV1.ACCOUNT
        )
    }
}
