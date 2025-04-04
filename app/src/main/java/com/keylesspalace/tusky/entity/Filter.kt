package com.keylesspalace.tusky.entity

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date
import kotlinx.parcelize.Parcelize

@JsonClass(generateAdapter = true)
@Parcelize
data class Filter(
    val id: String,
    val title: String,
    val context: List<String>,
    @Json(name = "expires_at") val expiresAt: Date? = null,
    @Json(name = "filter_action") val filterAction: String,
    // This field is mandatory according to the API documentation but is in fact optional in some instances
    val keywords: List<FilterKeyword> = emptyList(),
    // val statuses: List<FilterStatus>,
) : Parcelable {
    enum class Action(val action: String) {
        NONE("none"),
        BLUR("blur"),
        WARN("warn"),
        HIDE("hide");

        companion object {
            fun from(action: String): Action = entries.firstOrNull { it.action == action } ?: WARN
        }
    }
    enum class Kind(val kind: String) {
        HOME("home"),
        NOTIFICATIONS("notifications"),
        PUBLIC("public"),
        THREAD("thread"),
        ACCOUNT("account");

        companion object {
            fun from(kind: String): Kind = entries.firstOrNull { it.kind == kind } ?: PUBLIC
        }
    }

    val action: Action
        get() = Action.from(filterAction)

    val kinds: List<Kind>
        get() = context.map { Kind.from(it) }
}
