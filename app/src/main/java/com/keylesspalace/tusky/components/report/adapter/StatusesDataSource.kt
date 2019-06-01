package com.keylesspalace.tusky.components.report.adapter

import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import androidx.paging.ItemKeyedDataSource
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.NetworkState
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import java.util.concurrent.Executor

class StatusesDataSource(private val accountId: String,
                         private val mastodonApi: MastodonApi,
                         private val disposables: CompositeDisposable,
                         private val retryExecutor: Executor) : ItemKeyedDataSource<String, Status>() {

    val networkStateAfter = MutableLiveData<NetworkState>()
    val networkStateBefore = MutableLiveData<NetworkState>()

    private var retryAfter: (() -> Any)? = null
    private var retryBefore: (() -> Any)? = null
    private var retryInitial: (() -> Any)? = null

    val initialLoad = MutableLiveData<NetworkState>()
    fun retryAllFailed() {
        var prevRetry = retryInitial
        retryInitial = null
        prevRetry?.let {
            retryExecutor.execute {
                it.invoke()
            }
        }

        prevRetry = retryAfter
        retryAfter = null
        prevRetry?.let {
            retryExecutor.execute {
                it.invoke()
            }
        }

        prevRetry = retryBefore
        retryBefore = null
        prevRetry?.let {
            retryExecutor.execute {
                it.invoke()
            }
        }
    }
    @SuppressLint("CheckResult")
    override fun loadInitial(params: LoadInitialParams<String>, callback: LoadInitialCallback<Status>) {
        networkStateAfter.postValue( NetworkState.LOADED)
        networkStateBefore.postValue(NetworkState.LOADED)
        retryAfter = null
        retryBefore = null
        retryInitial = null
        initialLoad.postValue(NetworkState.LOADING)
                mastodonApi.statusObservable(params.requestedInitialKey).zipWith(
                    mastodonApi.accountStatusesObservable(accountId,  params.requestedInitialKey,  null, params.requestedLoadSize-1, null, null, null),
                        BiFunction { status: Status, list: List<Status> ->
                            val ret = ArrayList<Status>()
                            ret.add(status)
                            ret.addAll(list)
                            return@BiFunction ret
                        })
                .doOnSubscribe {
                    disposables.add(it)
                }
                .subscribe(
                        {
                            callback.onResult(it)
                            initialLoad.postValue(NetworkState.LOADED)
                        },
                        {
                            retryInitial = {
                                loadInitial(params,callback)
                            }
                            initialLoad.postValue(NetworkState.error(it.message))
                        }
                )
    }

    @SuppressLint("CheckResult")
    override fun loadAfter(params: LoadParams<String>, callback: LoadCallback<Status>) {
        networkStateAfter.postValue(NetworkState.LOADING)
        retryAfter = null
        mastodonApi.accountStatusesObservable(accountId, params.key,  null, params.requestedLoadSize, null, null, null)
                .doOnSubscribe {
                    disposables.add(it)
                }
                .subscribe(
                        {
                            callback.onResult(it)
                            networkStateAfter.postValue(NetworkState.LOADED)
                        },
                        {
                            retryAfter = {
                                loadAfter(params,callback)
                            }
                            networkStateAfter.postValue(NetworkState.error(it.message))
                        }
                )    }

    @SuppressLint("CheckResult")
    override fun loadBefore(params: LoadParams<String>, callback: LoadCallback<Status>) {
        networkStateBefore.postValue(NetworkState.LOADING)
        retryBefore = null
        mastodonApi.accountStatusesObservable(accountId, null,  params.key, params.requestedLoadSize, null, null, null)
                .doOnSubscribe {
                    disposables.add(it)
                }
                .subscribe(
                        {
                            callback.onResult(it)
                            networkStateBefore.postValue(NetworkState.LOADED)
                        },
                        {
                            retryBefore = {
                                loadBefore(params,callback)
                            }
                            networkStateBefore.postValue(NetworkState.error(it.message))
                        }
                )
    }

    override fun getKey(item: Status): String = item.id
}