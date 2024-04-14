package com.keylesspalace.tusky.util

sealed interface Resource<T> {
    val data: T?
}

class Loading<T>(override val data: T? = null) : Resource<T>

class Success<T>(override val data: T? = null) : Resource<T>

class Error<T>(
    override val data: T? = null,
    val errorMessage: String? = null,
    var consumed: Boolean = false,
    val cause: Throwable? = null
) : Resource<T>
