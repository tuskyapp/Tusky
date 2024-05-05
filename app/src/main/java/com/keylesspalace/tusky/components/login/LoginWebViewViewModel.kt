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
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.isHttpNotFound
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginWebViewViewModel @Inject constructor(
    private val api: MastodonApi
) : ViewModel() {

    private val _instanceRules = MutableStateFlow(emptyList<String>())
    val instanceRules = _instanceRules.asStateFlow()

    private var domain: String? = null

    fun init(domain: String) {
        if (this.domain == null) {
            this.domain = domain
            viewModelScope.launch {
                api.getInstance(domain).fold(
                    { instance ->
                        _instanceRules.value = instance.rules.map { rule -> rule.text }
                    },
                    { throwable ->
                        if (throwable.isHttpNotFound()) {
                            api.getInstanceV1(domain).fold(
                                { instance ->
                                    _instanceRules.value = instance.rules.map { rule -> rule.text }
                                },
                                { throwable2 ->
                                    Log.w(
                                        "LoginWebViewViewModel",
                                        "failed to load instance info",
                                        throwable2
                                    )
                                }
                            )
                        } else {
                            Log.w(
                                "LoginWebViewViewModel",
                                "failed to load instance info",
                                throwable
                            )
                        }
                    }
                )
            }
        }
    }
}
