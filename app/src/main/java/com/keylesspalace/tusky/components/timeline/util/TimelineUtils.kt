package com.keylesspalace.tusky.components.timeline.util

import retrofit2.HttpException
import java.io.IOException

fun Throwable.isExpected() = this is IOException || this is HttpException

inline fun <T> ifExpected(
    t: Throwable,
    cb: () -> T
): T {
    if (t.isExpected()) {
        return cb()
    } else {
        throw t
    }
}
