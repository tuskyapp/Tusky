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

package com.keylesspalace.tusky.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Returns a flow that mirrors the original flow, but filters out values that occur within
 * [timeoutMillis] of the previously emitted value. The first value is always emitted.
 *
 * Example:
 *
 * ```kotlin
 * flow {
 *     emit(1)
 *     delay(90)
 *     emit(2)
 *     delay(90)
 *     emit(3)
 *     delay(1010)
 *     emit(4)
 *     delay(1010)
 *     emit(5)
 * }.throttleFirst(1000)
 * ```
 *
 * produces the following emissions.
 *
 * ```text
 * 1, 4, 5
 * ```
 *
 * @see kotlinx.coroutines.flow.debounce
 */
fun <T> Flow<T>.throttleFirst(timeoutMillis: Long): Flow<T> = flow {
    var lastEmitTime = 0L
    collect {
        val currentTime = System.currentTimeMillis()
        val emit = currentTime - lastEmitTime > timeoutMillis
        if (emit) {
            lastEmitTime = currentTime
            emit(it)
        }
    }
}
