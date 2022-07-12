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

package com.keylesspalace.tusky.network

import android.util.Log
import com.keylesspalace.tusky.db.AccountManager
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class InstanceSwitchAuthInterceptor(private val accountManager: AccountManager) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest: Request = chain.request()

        // only switch domains if the request comes from retrofit
        return if (originalRequest.url.host == MastodonApi.PLACEHOLDER_DOMAIN) {

            val builder: Request.Builder = originalRequest.newBuilder()
            val instanceHeader = originalRequest.header(MastodonApi.DOMAIN_HEADER)

            if (instanceHeader != null) {
                // use domain explicitly specified in custom header
                builder.url(swapHost(originalRequest.url, instanceHeader))
                builder.removeHeader(MastodonApi.DOMAIN_HEADER)
            } else {
                val currentAccount = accountManager.activeAccount

                if (currentAccount != null) {
                    val accessToken = currentAccount.accessToken
                    if (accessToken.isNotEmpty()) {
                        // use domain of current account
                        builder.url(swapHost(originalRequest.url, currentAccount.domain))
                            .header("Authorization", "Bearer %s".format(accessToken))
                    }
                }
            }

            val newRequest: Request = builder.build()

            if (MastodonApi.PLACEHOLDER_DOMAIN == newRequest.url.host) {
                Log.w("ISAInterceptor", "no user logged in or no domain header specified - can't make request to " + newRequest.url)
                return Response.Builder()
                    .code(400)
                    .message("Bad Request")
                    .protocol(Protocol.HTTP_2)
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .request(chain.request())
                    .build()
            }

            chain.proceed(newRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }

    companion object {
        private fun swapHost(url: HttpUrl, host: String): HttpUrl {
            return url.newBuilder().host(host).build()
        }
    }
}
