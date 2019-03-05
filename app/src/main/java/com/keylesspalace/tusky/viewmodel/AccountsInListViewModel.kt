package com.keylesspalace.tusky.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.network.MastodonApi
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import javax.inject.Inject

data class State(val accounts: List<Account>, val searchResult: List<Account>?)

class AccountsInListViewModel @Inject constructor(private val api: MastodonApi) : ViewModel() {

    val state: Observable<State> get() = _state
    private val _state = BehaviorSubject.createDefault(State(listOf(), null))
    private val disposable = CompositeDisposable()

    fun load(listId: String) {
        if (_state.value!!.accounts.isEmpty()) {
            api.getAccountsInList(listId, 0).subscribe({ accounts ->
                updateState { copy(accounts = accounts) }
            }, { e ->
                Log.w("AccountsInListVM", e)
                // TODO: handle error
            }).addTo(disposable)
        }
    }

    private inline fun updateState(crossinline fn: State.() -> State) {
        _state.onNext(fn(_state.value!!))
    }
}