/* Copyright 2022 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.components.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.getOrElse
import com.keylesspalace.tusky.network.MastodonApiV1
import com.keylesspalace.tusky.network.MastodonApiV2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class LoginWebViewViewModel @Inject constructor(
    private val api2: MastodonApiV2,
    private val api1: MastodonApiV1
) : ViewModel() {
    val instanceRules: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())

    private var domain: String? = null

    fun init(domain: String) {
        if (this.domain == null) {
            this.domain = domain
            viewModelScope.launch {
                val info = api2.instance().getOrElse {
                    Log.w(TAG, "api2.instance() failed", it)
                    null
                } ?: api1.instance().getOrElse {
                    Log.w(TAG, "api1.instance() failed", it)
                    null
                }

                if (info == null) {
                    Log.w("LoginWebViewViewModel", "failed to load instance info")
                    return@launch
                }

                instanceRules.value = info.rules?.map { rule -> rule.text }.orEmpty()
            }
        }
    }

    companion object {
        private const val TAG = "LoginWebViewModel"
    }
}
