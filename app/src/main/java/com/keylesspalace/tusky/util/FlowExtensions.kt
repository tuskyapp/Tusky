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

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Returns a flow that mirrors the original flow, but filters out values that occur within
 * [timeout] of the previously emitted value. The first value is always emitted.
 *
 * Example:
 *
 * ```kotlin
 * flow {
 *     emit(1)
 *     delay(90.milliseconds)
 *     emit(2)
 *     delay(90.milliseconds)
 *     emit(3)
 *     delay(1010.milliseconds)
 *     emit(4)
 *     delay(1010.milliseconds)
 *     emit(5)
 * }.throttleFirst(1000.milliseconds)
 * ```
 *
 * produces the following emissions.
 *
 * ```text
 * 1, 4, 5
 * ```
 *
 * @see kotlinx.coroutines.flow.debounce(Duration)
 * @param timeout Emissions within this duration of the last emission are filtered
 * @param timeSource Used to measure elapsed time. Normally only overridden in tests
 */
fun <T> Flow<T>.throttleFirst(timeout: Duration, timeSource: TimeSource = TimeSource.Monotonic) =
    flow {
        var marker: TimeMark? = null
        collect {
            if (marker == null || marker!!.elapsedNow() >= timeout) {
                emit(it)
                marker = timeSource.markNow()
            }
        }
    }

fun <T> Flow<T>.observe(
    scope: CoroutineScope,
    collector: FlowCollector<T>
): Job = scope.launch {
    collect(collector)
}

fun <T> Flow<T>.observeLatest(
    scope: CoroutineScope,
    action: suspend (value: T) -> Unit
): Job = scope.launch {
    collectLatest(action)
}

fun <T> Flow<T>.observe(
    lifecycleOwner: LifecycleOwner,
    collector: FlowCollector<T>? = null
): Job = lifecycleOwner.lifecycleScope.launch {
    if (collector == null) {
        collect()
    } else {
        collect(collector)
    }
}

fun <T> Flow<T>.observeLatest(
    lifecycleOwner: LifecycleOwner,
    action: suspend (value: T) -> Unit
): Job = observeLatest(lifecycleOwner.lifecycleScope, action)

context(LifecycleOwner)
fun <T> Flow<T>.observe(
    collector: FlowCollector<T>? = null
): Job {
    // Prefer using viewLifecycleOwner in Fragments.
    val realLifecycleOwner = (this@LifecycleOwner as? Fragment)?.viewLifecycleOwner ?: this@LifecycleOwner
    return observe(realLifecycleOwner, collector)
}

context(ViewModel)
fun <T> Flow<T>.observe(
    collector: FlowCollector<T>
): Job = observe(viewModelScope, collector)

context(LifecycleOwner)
fun <T> Flow<T>.observeLatest(
    action: suspend (value: T) -> Unit
): Job {
    // Prefer using viewLifecycleOwner in Fragments.
    val realLifecycleOwner = (this@LifecycleOwner as? Fragment)?.viewLifecycleOwner ?: this@LifecycleOwner
    return observeLatest(realLifecycleOwner, action)
}

context(ViewModel)
fun <T> Flow<T>.observeLatest(
    action: suspend (value: T) -> Unit
): Job = observeLatest(viewModelScope, action)
