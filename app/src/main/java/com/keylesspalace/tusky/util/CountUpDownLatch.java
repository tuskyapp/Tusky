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

/**
 * This is a synchronization primitive related to {@link java.util.concurrent.CountDownLatch}
 * except that it starts at zero and can count upward.
 * <p>
 * The intended use case is for waiting for all tasks to be finished when the number of tasks isn't
 * known ahead of time, or may change while waiting.
 */
public class CountUpDownLatch {
    private int count;

    public CountUpDownLatch() {
        this.count = 0;
    }

    public synchronized void countDown() {
        count--;
        notifyAll();
    }

    public synchronized void countUp() {
        count++;
        notifyAll();
    }

    public synchronized void await() throws InterruptedException {
        while (count != 0) {
            wait();
        }
    }
}
