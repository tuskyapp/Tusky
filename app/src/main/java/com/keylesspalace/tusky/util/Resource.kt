package com.keylesspalace.tusky.util

sealed class Resource<T>(open val data: T?)

class Loading<T> (override val data: T? = null) : Resource<T>(data)

class Success<T> (override val data: T? = null) : Resource<T>(data)

class Error<T> (override val data: T? = null,
                val errorMessage: String? = null,
                var consumed: Boolean = false
): Resource<T>(data)