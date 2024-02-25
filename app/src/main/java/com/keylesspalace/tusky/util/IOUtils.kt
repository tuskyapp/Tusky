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

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import java.io.Closeable
import java.io.File
import java.io.IOException
import okio.buffer
import okio.sink
import okio.source

fun Closeable.closeQuietly() {
    try {
        close()
    } catch (e: IOException) {
        // intentionally unhandled
    }
}

@SuppressLint("Recycle") // The linter can't tell that the InputStream gets closed through the source
fun Uri.copyToFile(contentResolver: ContentResolver, file: File): Boolean {
    return try {
        val inputStream = contentResolver.openInputStream(this) ?: return false
        inputStream.source().use { source ->
            file.sink().buffer().use { bufferedSink ->
                bufferedSink.writeAll(source)
            }
        }
        true
    } catch (e: IOException) {
        false
    }
}
