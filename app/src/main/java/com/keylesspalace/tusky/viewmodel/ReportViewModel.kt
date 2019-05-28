package com.keylesspalace.tusky.viewmodel

import androidx.lifecycle.ViewModel
import javax.inject.Inject

/**
 * Created by pandasoft (joelpyska1@gmail.com) on 2019-05-28.
 */
class ReportViewModel @Inject constructor() : ViewModel() {
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

}