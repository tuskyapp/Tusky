/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.network

import com.keylesspalace.tusky.BlackBox
import okhttp3.Interceptor
import okhttp3.Response

/**
 * okhttp interceptor that records the requests and results to/from the
 * `/api/v1/notifications` endpoint.
 *
 * **No content of notifications is recorded.**
 */
object BlackBoxInterceptor : Interceptor {
    private const val TAG = "BlackBoxInterceptor"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Ignore anything outside the notifications endpoint
        if (!request.url.encodedPath.startsWith("/api/v1/notifications")) {
            return chain.proceed(request)
        }

        BlackBox.add(TAG, "${request.method} ${request.url}")

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            BlackBox.add(TAG, e)
            throw e
        }

        BlackBox.add(TAG, "${response.code} ${request.url}")

        return response
    }
}
