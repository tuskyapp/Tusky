package com.keylesspalace.tusky.components.timeline.util

import com.google.gson.JsonParseException
import retrofit2.HttpException
import java.io.IOException

fun Throwable.isExpected() = this is IOException || this is HttpException || this is JsonParseException

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
