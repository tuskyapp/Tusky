package com.keylesspalace.tusky.components.report

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.paging.PagedList
import com.keylesspalace.tusky.components.conversation.ConversationEntity
import com.keylesspalace.tusky.components.report.adapter.StatusesRepository
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.*
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

/**
 * Created by pandasoft (joelpyska1@gmail.com) on 2019-05-28.
 */
class ReportViewModel @Inject constructor(
        private val mastodonApi: MastodonApi,
        private val statusesRepository: StatusesRepository) : ViewModel() {
    private val disposables = CompositeDisposable()

    private val navigationMutable = MutableLiveData<Screen>()
    val navigation: LiveData<Screen> = navigationMutable

    private val muteStateMutable = MutableLiveData<Resource<Boolean>>()
    val muteState: LiveData<Resource<Boolean>> = muteStateMutable

    private val blockStateMutable = MutableLiveData<Resource<Boolean>>()
    val blockState: LiveData<Resource<Boolean>> = blockStateMutable

    private val reportingStateMutable = MutableLiveData<Resource<Boolean>>()
    var reportingState: LiveData<Resource<Boolean>> = reportingStateMutable

    private val repoResult = MutableLiveData<BiListing<Status>>()
    val statuses: LiveData<PagedList<Status>> = Transformations.switchMap(repoResult) { it.pagedList }
    val networkStateAfter: LiveData<NetworkState> = Transformations.switchMap(repoResult) { it.networkStateAfter }
    val networkStateBefore: LiveData<NetworkState> = Transformations.switchMap(repoResult) { it.networkStateBefore }
    val networkStateRefresh: LiveData<NetworkState> = Transformations.switchMap(repoResult) { it.refreshState }

    val selectedIds = HashSet<String>()
    var reportNote: String? = null
    var isRemoteNotify = false

    private var statusContent: String? = null
    private var statusId: String? = null
    lateinit var accountUserName: String
    lateinit var accountId: String

    fun init(accountId: String, userName: String, statusId: String?, statusContent: String?) {
        this.accountId = accountId
        this.accountUserName = userName
        this.statusId = statusId
        statusId?.let {
            selectedIds.add(it)
        }
        this.statusContent = statusContent
        obtainRelationship()
        repoResult.value = statusesRepository.getStatuses(accountId, statusId, disposables)
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }

    fun navigateTo(screen: Screen) {
        navigationMutable.value = screen
    }

    fun navigated() {
        navigationMutable.value = null
    }


    private fun obtainRelationship() {
        val ids = listOf(accountId)
        muteStateMutable.value = Loading()
        blockStateMutable.value = Loading()
        disposables.add(
                mastodonApi.relationshipsObservable(ids)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                { data ->
                                    updateRelationship(data.getOrNull(0))

                                },
                                { error ->
                                    updateRelationship(null)
                                }
                        ))
    }


    private fun updateRelationship(relationship: Relationship?) {
        if (relationship != null) {
            muteStateMutable.value = Success(relationship.muting)
            blockStateMutable.value = Success(relationship.blocking)
        } else {
            muteStateMutable.value = Error(false)
            blockStateMutable.value = Error(false)
        }
    }

    fun toggleMute() {
        val single: Single<Relationship> = if (muteStateMutable.value?.data == true) {
            mastodonApi.unmuteAccountObservable(accountId)
        } else {
            mastodonApi.muteAccountObservable(accountId)
        }
        muteStateMutable.value = Loading()
        disposables.add(
                single
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                { relationship ->
                                    muteStateMutable.value = Success(relationship?.muting == true)
                                },
                                { error ->
                                    muteStateMutable.value = Error(false, error.message)
                                }
                        ))
    }

    fun toggleBlock() {
        val single: Single<Relationship> = if (blockStateMutable.value?.data == true) {
            mastodonApi.unblockAccountObservable(accountId)
        } else {
            mastodonApi.blockAccountObservable(accountId)
        }
        blockStateMutable.value = Loading()
        disposables.add(
                single
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                { relationship ->
                                    blockStateMutable.value = Success(relationship?.blocking == true)
                                },
                                { error ->
                                    blockStateMutable.value = Error(false, error.message)
                                }
                        ))
    }

    fun doReport() {
        reportingStateMutable.value = Loading()
        disposables.add(
                mastodonApi.reportObservable(accountId, selectedIds.toList(), reportNote, isRemoteNotify)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                {
                                    reportingStateMutable.value = Success(true)
                                },
                                { error ->
                                    reportingStateMutable.value = Error(cause = error)
                                }
                        )
        )
    }

    fun retryStatusLoad() {
        repoResult.value?.retry?.invoke()
    }

    fun refreshStatuses() {
        repoResult.value?.refresh?.invoke()
    }

}