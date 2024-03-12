package com.keylesspalace.tusky.components.timeline.util

import com.google.gson.JsonParseException
import java.io.IOException
import retrofit2.HttpException

fun Throwable.isExpected() =
    this is IOException || this is HttpException || this is JsonParseException

inline fun <T> ifExpected(t: Throwable, cb: () -> T): T {
    if (t.isExpected()) {
        return cb()
    } else {
        throw t
    }
}
