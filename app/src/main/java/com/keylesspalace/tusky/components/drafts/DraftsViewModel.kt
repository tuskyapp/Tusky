package com.keylesspalace.tusky.components.drafts

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.paging.PagedList
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.Listing
import com.keylesspalace.tusky.util.NetworkState
import com.keylesspalace.tusky.util.RxAwareViewModel
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class DraftsViewModel @Inject constructor(
    val eventHub: EventHub,
    val database: AppDatabase
) : ViewModel() {



}
