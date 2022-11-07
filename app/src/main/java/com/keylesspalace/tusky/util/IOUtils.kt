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

import android.content.ContentResolver
import android.net.Uri
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

private const val DEFAULT_BLOCKSIZE = 16384

fun Closeable?.closeQuietly() {
    try {
        this?.close()
    } catch (e: IOException) {
        // intentionally unhandled
    }
}

fun Uri.copyToFile(
    contentResolver: ContentResolver,
    file: File,
): Boolean {
    val from: InputStream?
    val to: FileOutputStream

    try {
        from = contentResolver.openInputStream(this)
        to = FileOutputStream(file)
    } catch (e: FileNotFoundException) {
        return false
    }

    if (from == null) return false

    val chunk = ByteArray(DEFAULT_BLOCKSIZE)
    try {
        while (true) {
            val bytes = from.read(chunk, 0, chunk.size)
            if (bytes < 0) break
            to.write(chunk, 0, bytes)
        }
    } catch (e: IOException) {
        return false
    }

    from.closeQuietly()
    to.closeQuietly()
    return true
}
