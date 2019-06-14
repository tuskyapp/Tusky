package com.keylesspalace.tusky.components.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.paging.PagedList
import com.keylesspalace.tusky.components.search.adapter.SearchRepository
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.Listing
import com.keylesspalace.tusky.util.NetworkState
import com.keylesspalace.tusky.util.ViewDataUtils
import com.keylesspalace.tusky.viewdata.StatusViewData
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class SearchViewModel @Inject constructor(private val mastodonApi: MastodonApi) : ViewModel() {
    private val disposables = CompositeDisposable()

    private val statusesRepository = SearchRepository<Pair<Status, StatusViewData.Concrete>>(mastodonApi)
    private val accountsRepository = SearchRepository<Account>(mastodonApi)
    private val hastagsRepository = SearchRepository<HashTag>(mastodonApi)
    var alwaysShowSensitiveMedia: Boolean = false

    private val repoResultStatus = MutableLiveData<Listing<Pair<Status, StatusViewData.Concrete>>>()
    val statuses: LiveData<PagedList<Pair<Status, StatusViewData.Concrete>>> = Transformations.switchMap(repoResultStatus) { it.pagedList }
    val networkStateStatus: LiveData<NetworkState> = Transformations.switchMap(repoResultStatus) { it.networkState }
    val networkStateStatusRefresh: LiveData<NetworkState> = Transformations.switchMap(repoResultStatus) { it.refreshState }

    private val repoResultAccount = MutableLiveData<Listing<Account>>()
    val accounts: LiveData<PagedList<Account>> = Transformations.switchMap(repoResultAccount) { it.pagedList }
    val networkStateAccount: LiveData<NetworkState> = Transformations.switchMap(repoResultAccount) { it.networkState }
    val networkStateAccountRefresh: LiveData<NetworkState> = Transformations.switchMap(repoResultAccount) { it.refreshState }

    private val repoResultHashTag = MutableLiveData<Listing<HashTag>>()
    val hashtags: LiveData<PagedList<HashTag>> = Transformations.switchMap(repoResultHashTag) { it.pagedList }
    val networkStateHashTag: LiveData<NetworkState> = Transformations.switchMap(repoResultHashTag) { it.networkState }
    val networkStateHashTagRefresh: LiveData<NetworkState> = Transformations.switchMap(repoResultHashTag) { it.refreshState }


    fun search(query: String?) {
        repoResultStatus.value = statusesRepository.getSearchData(SearchType.Status, query, disposables) {
            it?.statuses?.map { status -> Pair(status, ViewDataUtils.statusToViewData(status, alwaysShowSensitiveMedia)!!) }
                    ?: emptyList()
        }
        repoResultAccount.value = accountsRepository.getSearchData(SearchType.Account, query, disposables) {
            it?.accounts ?: emptyList()
        }
        repoResultHashTag.value = hastagsRepository.getSearchData(SearchType.Hashtag, query, disposables) {
            it?.hashtags ?: emptyList()
        }

    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }

    fun retryStatusSearch() {
        repoResultStatus.value?.retry?.invoke()
    }
}