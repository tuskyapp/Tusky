package com.keylesspalace.tusky.components.timeline.util

import retrofit2.HttpException
import java.io.IOException

fun Throwable.isExpected() = this is IOException || this is HttpException

inline fun ifExpected(
    t: Throwable,
    cb: () -> Unit
) {
    if (t.isExpected()) {
        cb()
    } else {
        throw t
    }
}
