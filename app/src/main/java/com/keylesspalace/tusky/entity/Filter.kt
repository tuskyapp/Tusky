package com.keylesspalace.tusky.entity

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.keylesspalace.tusky.components.timeline.TimelineKind
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class Filter(
    val id: String,
    val title: String,
    val context: List<String>,
    @SerializedName("expires_at") val expiresAt: Date?,
    @SerializedName("filter_action") private val filterAction: String,
    val keywords: List<FilterKeyword>
    // val statuses: List<FilterStatus>,
) : Parcelable {
    enum class Action(val action: String) {
        NONE("none"),
        WARN("warn"),
        HIDE("hide");

        companion object {
            fun from(action: String): Action = values().firstOrNull { it.action == action } ?: WARN
        }
    }
    enum class Kind(val kind: String) {
        HOME("home"),
        NOTIFICATIONS("notifications"),
        PUBLIC("public"),
        THREAD("thread"),
        ACCOUNT("account");

        companion object {
            fun from(kind: String): Kind = values().firstOrNull { it.kind == kind } ?: PUBLIC

            fun from(kind: TimelineKind): Kind = when (kind) {
                is TimelineKind.Home, is TimelineKind.UserList -> HOME
                is TimelineKind.PublicFederated,
                is TimelineKind.PublicLocal,
                is TimelineKind.Tag,
                is TimelineKind.Favourites -> Filter.Kind.PUBLIC
                is TimelineKind.User -> Filter.Kind.ACCOUNT
                else -> Filter.Kind.PUBLIC
            }
        }
    }

    val action: Action
        get() = Action.from(filterAction)

    val kinds: List<Kind>
        get() = context.map { Kind.from(it) }
}
