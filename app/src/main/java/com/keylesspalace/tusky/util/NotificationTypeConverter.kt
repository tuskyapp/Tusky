/* Copyright 2019 Joel Pyska
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

package com.keylesspalace.tusky.util

import com.keylesspalace.tusky.entity.Notification
import org.json.JSONArray

/**
 * Serialize to string array and deserialize notifications type
 */

fun serialize(data: Array<Set<Notification.Type>>?): String {
    val array = JSONArray()
    data?.forEach { innerArray ->
        val filterArray = JSONArray()
        innerArray.forEach {
            filterArray.put(it.presentation)
        }
        array.put(filterArray)
    }
    return array.toString()
}

private fun deserializeInternal(array: JSONArray): Set<Notification.Type> {
    val ret = HashSet<Notification.Type>()
    for (i in 0 until array.length()) {
        val item = array.getString(i)
        val type = Notification.Type.byString(item)
        if (type != Notification.Type.UNKNOWN) {
            ret.add(type)
        }
    }
    return ret
}

// This performs an implied conversion from AppDatabase 51 to 52.
private fun deserializeSingleFallback(array: JSONArray): Array<Set<Notification.Type>> {
    val orig = deserializeInternal(array)
    return arrayOf(orig, HashSet(orig))
}

fun deserialize(data: String?): Array<Set<Notification.Type>> {
    val ret = mutableListOf<Set<Notification.Type>>()
    data?.let {
        val array = JSONArray(data)
        for (i in 0 until array.length()) {
            val filterArray = array.optJSONArray(i)
            if (filterArray == null) {
                return deserializeSingleFallback(array)
            }

            ret.add(deserializeInternal(filterArray))
        }
    }
    return ret.toTypedArray()
}
