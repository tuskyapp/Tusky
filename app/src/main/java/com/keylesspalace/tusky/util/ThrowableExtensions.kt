package com.keylesspalace.tusky.util

import android.content.Context
import com.keylesspalace.tusky.R
import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

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

/** @return A drawable resource to accompany the error message for this throwable */
fun Throwable.getDrawableRes(): Int = when (this) {
    is IOException -> R.drawable.errorphant_offline
    is HttpException -> {
        if (this.code() == 404) {
            R.drawable.elephant_friend_empty
        } else {
            R.drawable.errorphant_offline
        }
    }
    else -> R.drawable.errorphant_error
}

/** @return A string error message for this throwable */
fun Throwable.getErrorString(context: Context): String = getServerErrorMessage() ?: when (this) {
    is IOException -> context.getString(R.string.error_network)
    is HttpException -> if (this.code() == 404) context.getString(R.string.error_404_not_found) else context.getString(R.string.error_generic)
    else -> context.getString(R.string.error_generic)
}
