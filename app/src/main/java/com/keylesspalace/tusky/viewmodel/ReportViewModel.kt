package com.keylesspalace.tusky.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.keylesspalace.tusky.fragment.report.Screen
import com.keylesspalace.tusky.network.MastodonApi
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

/**
 * Created by pandasoft (joelpyska1@gmail.com) on 2019-05-28.
 */
class ReportViewModel @Inject constructor(private val mastodonApi: MastodonApi) : ViewModel() {
    private val disposables = CompositeDisposable()

    private val navigationMutable = MutableLiveData<Screen>()
    val navigation: LiveData<Screen> = navigationMutable

    private var statusContent: String? = null
    private var statusId: String? = null
    lateinit var accountUserName: String
    lateinit var accountId: String

    fun init(accountId: String, userName: String, statusId: String?, statusContent: String?) {
        this.accountId = accountId
        this.accountUserName = userName
        this.statusId = statusId
        this.statusContent = statusContent
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }

    fun navigateTo(screen: Screen){
        navigationMutable.value = screen
    }
    fun navigated(){
        navigationMutable.value = null
    }
}