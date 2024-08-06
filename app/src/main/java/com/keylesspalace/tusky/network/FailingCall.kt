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
