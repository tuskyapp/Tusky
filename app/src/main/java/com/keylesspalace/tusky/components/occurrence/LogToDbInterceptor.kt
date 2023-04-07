/* Copyright Tusky Contributors
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

package com.keylesspalace.tusky.components.occurrence

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class LogToDbInterceptor(private val occurrenceRespository: OccurrenceRepository) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val what = request.method + " " + request.url.toString()

        val entityId = occurrenceRespository.handleApiCallStart(what)

        val response: Response
        try {
            response = chain.proceed(request)
            occurrenceRespository.handleApiCallFinish(entityId, response.code)
        } catch (e: Exception) {
            // TODO this case is used? If so add its message to the occurrence entity?
            occurrenceRespository.handleApiCallFinish(entityId, 600)

            throw e
        }

        return response
    }
}
