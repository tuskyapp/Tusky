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

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.GzipSource
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

data class RequestResponse(
    /** The original request */
    val req: Request,

    /** The body of the response, may be null if an exception occurred */
    val body: String? = null,

    /** The exception if [body] is null */
    val e: Exception? = null
)

/** okhttp interceptor that records the last 5 HTTP requests and responses */
object RecordResponseInterceptor : Interceptor {
    val queries = RingBuffer<RequestResponse>(5)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            queries.add(RequestResponse(request, e = e))
            throw e
        }

        // The response can't be consumed twice, so clone the underlying buffer.
        val responseBody = response.body!!
        val headers = response.headers
        val source = responseBody.source()
        source.request(Long.MAX_VALUE)
        var buffer = source.buffer

        // Decompress the buffer if necessary
        if ("gzip".equals(headers["Content-Encoding"], ignoreCase = true)) {
            GzipSource(buffer.clone()).use { gzippedResponseBody ->
                buffer = Buffer()
                buffer.writeAll(gzippedResponseBody)
            }
        }

        val contentType = responseBody.contentType()

        // TODO: Check the contentType? Probably not necessary here, as this should only get
        // called for API requests...
        val charset: Charset = contentType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8

        queries.add(RequestResponse(request, body = buffer.clone().readString(charset)))

        return response
    }
}

/**
 * A ring buffer with space for [capacity] items. Adding new items to the buffer will drop older
 * items.
 */
class RingBuffer<T>(capacity: Int) : Iterable<T> {
    private val data: Array<Any?>
    private var capacity: Int = 0
    private var tail: Int

    private val head: Int
        get() = if (capacity == data.size) (tail + 1) % capacity else 0

    /** Number of items in the buffer */
    val size: Int
        get() = capacity

    /** Add an item to the buffer, overwriting the oldest item in the buffer */
    fun add(item: T) {
        tail = (tail + 1) % data.size
        data[tail] = item
        if (capacity < data.size) capacity++
    }

    /** Get an item from the buffer */
    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T = when {
        capacity == 0 || index > capacity || index < 0 -> throw IndexOutOfBoundsException("$index")
        capacity == data.size -> data[(head + index) % data.size]
        else -> data[index]
    } as T

    /** Buffer as a list. */
    @Suppress("UNCHECKED_CAST")
    fun toList(): List<T> = iterator().asSequence().toList()

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private val index: AtomicInteger = AtomicInteger(0)
        override fun hasNext(): Boolean = index.get() < size
        override fun next(): T = get(index.getAndIncrement())
    }

    init {
        this.data = arrayOfNulls(capacity)
        this.tail = -1
    }
}
