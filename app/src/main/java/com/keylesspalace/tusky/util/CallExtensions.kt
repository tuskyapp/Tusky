package com.keylesspalace.tusky.util

import retrofit2.Call
import retrofit2.HttpException

/**
 * Synchronously executes the call and returns the response encapsulated in a kotlin.Result.
 * Since Result is an inline class it is not possible to do this with a Retrofit adapter unfortunately.
 * More efficient then calling a suspending method with runBlocking
 */
fun <T> Call<T>.result(): Result<T> {
    return try {
        val response = execute()
        val responseBody = response.body()
        if (response.isSuccessful && responseBody != null) {
            Result.success(responseBody)
        } else {
            Result.failure(HttpException(response))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
