package com.keylesspalace.tusky.util

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.util.function.Consumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Simple reimplementation of RxJava's Single using a Kotlin coroutine,
 * intended to be consumed by legacy Java code only.
 */
class Single<T>(private val producer: suspend CoroutineScope.() -> Result<T>) {
    fun subscribe(
        owner: LifecycleOwner,
        onSuccess: Consumer<T>,
        onError: Consumer<Throwable>
    ): Job {
        return owner.lifecycleScope.launch {
            producer().fold(
                onSuccess = { onSuccess.accept(it) },
                onFailure = { onError.accept(it) }
            )
        }
    }
}
