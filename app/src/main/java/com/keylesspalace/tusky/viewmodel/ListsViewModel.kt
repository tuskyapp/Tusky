package com.keylesspalace.tusky.viewmodel

import androidx.lifecycle.ViewModel
import com.keylesspalace.tusky.entity.MastoList
import com.keylesspalace.tusky.network.MastodonApi
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import java.io.IOException
import java.net.ConnectException
import javax.inject.Inject


internal class ListsViewModel @Inject constructor(private val api: MastodonApi) : ViewModel() {
    enum class LoadingState {
        INITIAL, LOADING, LOADED, ERROR_NETWORK, ERROR_OTHER
    }

    data class State(val lists: List<MastoList>, val loadingState: LoadingState)

    val state: Observable<State> get() = _state
    private var _state = BehaviorSubject.createDefault(State(listOf(), LoadingState.INITIAL))
    private val disposable = CompositeDisposable()

    fun retryLoading() {
        loadIfNeeded()
    }

    private fun loadIfNeeded() {
        val state = _state.value!!
        if (state.loadingState == LoadingState.LOADING || !state.lists.isEmpty()) return
        updateState {
            copy(loadingState = LoadingState.LOADING)
        }

        api.getLists().subscribe({ lists ->
            updateState {
                copy(
                        lists = lists,
                        loadingState = LoadingState.LOADED
                )
            }
        }, { err ->
            updateState {
                copy(loadingState = if (err is IOException || err is ConnectException)
                    LoadingState.ERROR_NETWORK else LoadingState.ERROR_OTHER)
            }
        }).addTo(disposable)
    }

    private inline fun updateState(crossinline fn: State.() -> State) {
        _state.onNext(fn(_state.value!!))
    }

    fun createNewList(listName: String) {
        api.createList(listName).subscribe({ list ->
            updateState {
                copy(lists = lists + list)
            }
        }, {
            // TODO: handle error here
        }).addTo(disposable)
    }
}
