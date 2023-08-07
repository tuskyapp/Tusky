/*
 * Copyright 2023 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.core.database.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
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
        }
    }

    val action: Action
        get() = Action.from(filterAction)

    val kinds: List<Kind>
        get() = context.map { Kind.from(it) }
}
