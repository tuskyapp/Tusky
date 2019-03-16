/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

@file:JvmName("ListUtils")

package tech.bigfig.roma.util

import java.util.LinkedHashSet
import java.util.ArrayList


/**
 * @return true if list is null or else return list.isEmpty()
 */
fun isEmpty(list: List<*>?): Boolean {
    return list == null || list.isEmpty()
}

/**
 * @return a new ArrayList containing the elements without duplicates in the same order
 */
fun <T> removeDuplicates(list: List<T>): ArrayList<T> {
    val set = LinkedHashSet(list)
    return ArrayList(set)
}

inline fun <T> List<T>.withoutFirstWhich(predicate: (T) -> Boolean): List<T> {
    val newList = toMutableList()
    val index = newList.indexOfFirst(predicate)
    if (index != -1) {
        newList.removeAt(index)
    }
    return newList
}

inline fun <T> List<T>.replacedFirstWhich(replacement: T, predicate: (T) -> Boolean): List<T> {
    val newList = toMutableList()
    val index = newList.indexOfFirst(predicate)
    if (index != -1) {
        newList[index] = replacement
    }
    return newList
}