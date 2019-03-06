package com.keylesspalace.tusky.viewmodel

import androidx.lifecycle.ViewModel
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.Either.Left
import com.keylesspalace.tusky.util.Either.Right
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import javax.inject.Inject

data class State(val accounts: Either<Throwable, List<Account>>, val searchResult: List<Account>?)

class AccountsInListViewModel @Inject constructor(private val api: MastodonApi) : ViewModel() {

    val state: Observable<State> get() = _state
    private val _state = BehaviorSubject.createDefault(State(Right(listOf()), null))
    private val disposable = CompositeDisposable()

    fun load(listId: String) {
        val state = _state.value!!
        if (state.accounts.isLeft() || state.accounts.asRight().isEmpty()) {
            api.getAccountsInList(listId, 0).subscribe({ accounts ->
                updateState { copy(accounts = Right(accounts)) }
            }, { e ->
                updateState { copy(accounts = Left(e)) }
            }).addTo(disposable)
        }
    }

    fun addAccountToList(listId: String, account: Account) {
        api.addCountToList(listId, listOf(account.id))
                .subscribe({
                    updateState {
                        copy(accounts = accounts.map { it + account })
                    }
                }, {
                    // TODO: send error to the client
                })
                .addTo(disposable)
    }

    fun deleteAccountFromList(listId: String, accountId: String) {
        api.deleteAccountFromList(listId, listOf(accountId))
                .subscribe({
                    updateState {
                        copy(accounts = accounts.map { accounts ->
                            val withoutAccount = accounts.toMutableList()
                            val index = accounts.indexOfFirst { it.id == accountId }
                            if (index > 0) withoutAccount.removeAt(index)
                            withoutAccount
                        })
                    }
                }, {
                    // TODO: send error to the client
                })
                .addTo(disposable)
    }

    fun search(query: String) {
        when {
            query.isEmpty() -> updateState { copy(searchResult = null) }
            query.isBlank() -> updateState { copy(searchResult = listOf()) }
            else -> api.searchAccounts(query, null, 10, true)
                    .subscribe({ result ->
                        updateState { copy(searchResult = result) }
                    }, {
                        updateState { copy(searchResult = listOf()) }
                    }).addTo(disposable)
        }
    }

    private inline fun updateState(crossinline fn: State.() -> State) {
        _state.onNext(fn(_state.value!!))
    }
}