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

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException


object LogReader {
    fun getLogFile(context: Context): Uri {
        return try {

            val logFile = File(context.cacheDir, "log.txt")
            logFile.delete()
            logFile.createNewFile()
            // -d means print and exit, -T gets last lines, -f outputs to file
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-T", "1500", "-f", logFile.absolutePath))
            try {
                process.waitFor()
            } catch (ignored: InterruptedException) {
            }
            if (process.exitValue() != 0) {
                val error: String = process.errorStream.bufferedReader().readText()
                throw RuntimeException("Reading logs failed: " + process.exitValue() + ", " + error)
            }
            Uri.fromFile(logFile)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}
