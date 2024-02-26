package com.keylesspalace.tusky.util

import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.fold

fun <T> NetworkResult<T>.asResult(): Result<T> = fold(
    { Result.success(it) },
    { Result.failure(it) },
)
