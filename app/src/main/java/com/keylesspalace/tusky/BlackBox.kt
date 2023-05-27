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

package com.keylesspalace.tusky

import java.util.concurrent.atomic.AtomicInteger

/**
 * Record data about interactions with notifications, which the user
 * can opt-in to providing to the developers to track down the cause
 * of https://github.com/tuskyapp/Tusky/issues/3689
 */
object BlackBox {
    val blackbox = RingBuffer<String>(200)

    fun add(tag: String, data: Any) = blackbox.add("${System.currentTimeMillis()}: $tag: $data")
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
