/* Copyright Tusky Contributors
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
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.components.occurrence

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.keylesspalace.tusky.db.Converters
import java.util.*

@Entity
@TypeConverters(Converters::class)
data class OccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long? = null,
    val type: Type,
    val what: String,
    val startedAt: Date, // TODO or use LocalDateTime (or Long)?
    val finishedAt: Date? = null,
    val code: Int? = null,
    val callTrace: Array<StackTraceElement>,
) {
    companion object {
        fun reduceTrace(stackTrace: Array<StackTraceElement>): String {
            // TODO conditions/transforms here are a bit arbitrary...
            // TODO probably keep at least the last non-Tusky location in the stack; and/or keep the information that some entries were removed

            var tuskyTrace = stackTrace.filter { it.className.startsWith("com.keylesspalace.tusky") && !it.methodName.contains("intercept") }
            if (tuskyTrace.size > 3) {
                tuskyTrace = tuskyTrace.subList(0, 3)
            }

            return tuskyTrace.joinToString("<") { reduceClassName(it.className) + "." + it.methodName + "():" + it.lineNumber }
        }

        private fun reduceClassName(className: String): String {
            return className.substringAfter("com.keylesspalace.tusky.")

//            if (!className.contains('.')) {
//                return className
//            }
//
//            val parts = className.split('.')
//
//            return parts.subList(parts.size-2, parts.size).joinToString(".")
        }
    }

    enum class Type {
        APICALL,
        CRASH;

        companion object {
            fun fromString(type: String): Type {
                return values().first { it.name.equals(type, true) }
            }
        }
    }
}
