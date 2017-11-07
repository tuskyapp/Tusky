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
package com.keylesspalace.tusky.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Created by charlag on 05/11/17.
 *
 * Class to represent sum type/tagged union/variant/ADT e.t.c.
 * It is either Left or Right.
 */
public final class Either<L, R> {

    /**
     * Constructs Left instance of either
     * @param left Object to be considered Left
     * @param <L> Left type
     * @param <R> Right type
     * @return new instance of Either which contains left.
     */
    public static <L, R> Either<L, R> left(L left) {
        return new Either<>(left, false);
    }

    /**
     * Constructs Right instance of either
     * @param right Object to be considered Right
     * @param <L> Left type
     * @param <R> Right type
     * @return new instance of Either which contains right.
     */
    public static <L, R> Either<L, R> right(R right) {
        return new Either<>(right, true);
    }

    private final Object value;
    // we need it because of the types erasure
    private boolean isRight;

    private Either(Object value, boolean isRight) {
        this.value = value;
        this.isRight = isRight;
    }

    public boolean isRight() {
        return isRight;
    }

    /**
     * Try to get contained object as a Left or throw an exception.
     * @throws AssertionError If contained value is Right
     * @return contained value as Right
     */
    public @NonNull L getAsLeft() {
        if (isRight) {
            throw new AssertionError("Tried to get the Either as Left while it is Right");
        }
        //noinspection unchecked
        return (L) value;
    }

    /**
     * Try to get contained object as a Right or throw an exception.
     * @throws AssertionError If contained value is Left
     * @return contained value as Right
     */
    public @NonNull R getAsRight() {
        if (!isRight) {
            throw new AssertionError("Tried to get the Either as Right while it is Left");
        }
        //noinspection unchecked
        return (R) value;
    }

    /**
     * Same as {@link #getAsLeft()} but returns {@code null} is the value if Right instead of
     * throwing an exception.
     * @return contained value as Left or null
     */
    public @Nullable L getAsLeftOrNull() {
        if (isRight) {
            return null;
        }
        //noinspection unchecked
        return (L) value;
    }

    /**
     * Same as {@link #getAsRightOrNull()} but returns {@code null} is the value if Left instead of
     * throwing an exception.
     * @return contained value as Right or null
     */
    public @Nullable R getAsRightOrNull() {
        if (!isRight) {
            return null;
        }
        //noinspection unchecked
        return (R) value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof Either)) return false;
        Either that = (Either) obj;
        return this.isRight == that.isRight &&
                (this.value == that.value ||
                        this.value != null && this.value.equals(that.value));
    }
}
