package com.keylesspalace.tusky.util

import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException

/**
 * checks if this throwable indicates an error causes by a 4xx/5xx server response and
 * tries to retrieve the error message the server sent
 * @return the error message, or null if this is no server error or it had no error message
 */
fun Throwable.getServerErrorMessage(): String? {
    if (this is HttpException) {
        val errorResponse = response()?.errorBody()?.string()
        return if (!errorResponse.isNullOrBlank()) {
            try {
                JSONObject(errorResponse).getString("error")
            } catch (e: JSONException) {
                null
            }
        } else {
            null
        }
    }
    return null
}
