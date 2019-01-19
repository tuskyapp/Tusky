/* Copyright 2017 Andrew Dawson
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

/**
 * Created by charlag on 05/11/17.
 *
 * Class to represent sum type/tagged union/variant/ADT e.t.c.
 * It is either Left or Right.
 */
sealed class Either<out L, out R> {
    data class Left<out L, out R>(val value: L) : Either<L, R>()
    data class Right<out L, out R>(val value: R) : Either<L, R>()

    fun isRight() = this is Right

    fun asLeftOrNull() = (this as? Left<L, R>)?.value

    fun asRightOrNull() = (this as? Right<L, R>)?.value

    fun asLeft(): L = (this as Left<L, R>).value

    fun asRight(): R = (this as Right<L, R>).value
}