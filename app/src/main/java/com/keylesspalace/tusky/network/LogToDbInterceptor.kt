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

package com.keylesspalace.tusky.network

import com.keylesspalace.tusky.db.OccurrenceDao
import com.keylesspalace.tusky.db.OccurrenceEntity
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.TimeUnit

class LogToDbInterceptor(private val dao: OccurrenceDao, private val accountId: Long?) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()

        val startTime = Calendar.getInstance().time

        val what = OccurrenceEntity(
            accountId = accountId,
            type = OccurrenceEntity.Type.APICALL,
            what = request.method + " " + request.url.toString(),
            startedAt = startTime,
            callTrace = OccurrenceEntity.reduceTrace(Throwable().stackTrace),
        )
        // TODO all stack traces here have no hint where they might have originated (always ThreadPool)
        //   found kotlinx.coroutines.stacktrace.recovery but that should be on by default?

        val entityId: Long
        runBlocking {
            // TODO runBlocking is the right thing to do here?
            entityId = dao.insertOrReplace(what)
        }

        val startNs = System.nanoTime()
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            throw e
        }

        val finishTime = Calendar.getInstance().time

        val afterWhat = what.copy(
            id = entityId,
            finishedAt = finishTime,
            code = response.code,
        )

        runBlocking {
            dao.insertOrReplace(afterWhat)
        }


        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

        val responseBody = response.body!!
        val contentLength = responseBody.contentLength()


// network.LogToDbInterceptor.intercept:25; network.InstanceSwitchAuthInterceptor.intercept:70; di.NetworkModule$providesHttpClient$$inlined$-addInterceptor$1.intercept:1086
        return response
    }
}
