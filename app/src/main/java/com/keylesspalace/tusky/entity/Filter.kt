package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName
import java.util.Date

data class Filter(
    val id: String,
    val title: String,
    val context: List<String>,
    @SerializedName("expires_at") val expiresAt: Date?,
    @SerializedName("filter_action") private val filterAction: String,
    val keywords: List<FilterKeyword>,
    // val statuses: List<FilterStatus>,
 ) {
    enum class Action(val action: String) {
        NONE("none"),
        WARN("warn"),
        HIDE("hide");

        companion object {
            fun fromString(action: String): Action = values().firstOrNull { it.action == action } ?: WARN;
        }
    }
    enum class Kind(val kind: String) {
        HOME("home"),
        NOTIFICATIONS("notifications"),
        PUBLIC("public"),
        THREAD("thread"),
        ACCOUNT("account");

        companion object {
            fun fromString(kind: String): Kind = values().firstOrNull { it.kind == kind } ?: PUBLIC
        }
    }

    val action: Action
        get() = Action.fromString(filterAction)

    val kinds: List<Kind>
        get() = context.map { Kind.fromString(it) }
}