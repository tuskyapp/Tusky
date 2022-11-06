package com.keylesspalace.tusky.util

import android.content.ContentResolver
import android.net.Uri
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

object IOUtils {

    private const val DEFAULT_BLOCKSIZE = 16384

    fun closeQuietly(stream: Closeable?) {
        try {
            stream?.close()
        } catch (e: IOException) {
            // intentionally unhandled
        }
    }

    fun copyToFile(
        contentResolver: ContentResolver,
        uri: Uri,
        file: File,
    ): Boolean {
        val from: InputStream?
        val to: FileOutputStream

        try {
            from = contentResolver.openInputStream(uri)
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

        closeQuietly(from)
        closeQuietly(to)
        return true
    }
}
