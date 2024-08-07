/* Copyright 2024 Tusky contributors
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

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout

class FailingCall(private val request: Request) : Call {

    private var isExecuted: Boolean = false

    override fun cancel() { }

    override fun clone(): Call {
        return FailingCall(request())
    }

    override fun enqueue(responseCallback: Callback) {
        isExecuted = true
        responseCallback.onResponse(this, failingResponse())
    }

    override fun execute(): Response {
        isExecuted = true
        return failingResponse()
    }

    override fun isCanceled(): Boolean = false

    override fun isExecuted(): Boolean = isExecuted

    override fun request(): Request = request

    override fun timeout(): Timeout {
        return Timeout.NONE
    }

    private fun failingResponse(): Response {
        return Response.Builder()
            .request(request)
            .code(400)
            .message("Bad Request")
            .protocol(Protocol.HTTP_1_1)
            .body("".toResponseBody())
            .build()
    }
}
