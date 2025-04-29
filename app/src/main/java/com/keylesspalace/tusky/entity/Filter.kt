package com.keylesspalace.tusky.entity

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date
import kotlinx.parcelize.Parcelize

@JsonClass(generateAdapter = true)
@Parcelize
data class Filter(
    val id: String = "",
    val title: String = "",
    val context: List<Kind>,
    @Json(name = "expires_at") val expiresAt: Date? = null,
    @Json(name = "filter_action") val action: Action,
    // This field is mandatory according to the API documentation but is in fact optional in some instances
    val keywords: List<FilterKeyword> = emptyList(),
    // val statuses: List<FilterStatus>,
) : Parcelable {
    enum class Action(val action: String) {
        @Json(name = "none")
        NONE("none"),

        @Json(name = "blur")
        BLUR("blur"),

        @Json(name = "warn")
        WARN("warn"),

        @Json(name = "hide")
        HIDE("hide");

        // Retrofit will call toString when sending this class as part of a form-urlencoded body.
        override fun toString() = action

        companion object {
            fun from(action: String): Action = entries.firstOrNull { it.action == action } ?: WARN
        }
    }
    enum class Kind(val kind: String) {
        @Json(name = "home")
        HOME("home"),

        @Json(name = "notifications")
        NOTIFICATIONS("notifications"),

        @Json(name = "public")
        PUBLIC("public"),

        @Json(name = "thread")
        THREAD("thread"),

        @Json(name = "account")
        ACCOUNT("account");

        // Retrofit will call toString when sending this class as part of a form-urlencoded body.
        override fun toString() = kind

        companion object {
            fun from(kind: String): Kind = entries.firstOrNull { it.kind == kind } ?: PUBLIC
        }
    }
}
