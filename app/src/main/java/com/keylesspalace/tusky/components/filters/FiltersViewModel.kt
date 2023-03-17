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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

class FiltersViewModel @Inject constructor(
    private val api: MastodonApi,
    private val eventHub: EventHub
) : ViewModel() {
    val filters: MutableStateFlow<List<Filter>> = MutableStateFlow(listOf())
    val error: MutableStateFlow<Throwable?> = MutableStateFlow(null)

    fun load() {
        viewModelScope.launch {
            api.getFilters().fold(
                { filters ->
                    this@FiltersViewModel.filters.value = filters
                },
                { throwable ->
                    if (throwable is HttpException && throwable.code() == 404) {
                        api.getFiltersV1().fold(
                            { filters ->
                                this@FiltersViewModel.filters.value = filters.map { it.toFilter() }
                            },
                            { throwable ->
                                error.value = throwable
                            }
                        )
                    } else {
                        error.value = throwable
                    }
                }
            )
        }
    }

    fun deleteFilter(filter: Filter, parent: View) {
        viewModelScope.launch {
            api.deleteFilter(filter.id).fold(
                {
                    filters.value = filters.value.filter { it.id != filter.id }
                    for (context in filter.context) {
                        eventHub.dispatch(PreferenceChangedEvent(context))
                    }
                },
                { throwable ->
                    if (throwable is HttpException && throwable.code() == 404) {
                        api.deleteFilterV1(filter.id).fold(
                            {
                                filters.value = filters.value.filter { it.id != filter.id }
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
}
