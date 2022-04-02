package com.keylesspalace.tusky.util

import android.content.Context
import android.util.Log
import android.util.Pair
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.keylesspalace.tusky.R
import de.c1710.filemojicompat.FileEmojiCompatConfig
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.toLongOrDefault
import okio.Source
import okio.buffer
import okio.sink
import java.io.EOFException
import java.io.File
import java.io.FilenameFilter
import java.io.IOException
import kotlin.math.max

/**
 * This class bundles information about an emoji font as well as many convenient actions.
 */
class EmojiCompatFont(
    val name: String,
    private val display: String,
    @StringRes val caption: Int,
    @DrawableRes val img: Int,
    val url: String,
    // The version is stored as a String in the x.xx.xx format (to be able to compare versions)
    val version: String
) {

    private val versionCode = getVersionCode(version)

    // A list of all available font files and whether they are older than the current version or not
    // They are ordered by their version codes in ascending order
    private var existingFontFileCache: List<Pair<File, List<Int>>>? = null

    val id: Int
        get() = FONTS.indexOf(this)

    fun getDisplay(context: Context): String {
        return if (this !== SYSTEM_DEFAULT) display else context.getString(R.string.system_default)
    }

    /**
     * This method will return the actual font file (regardless of its existence) for
     * the current version (not necessarily the latest!).
     *
     * @return The font (TTF) file or null if called on SYSTEM_FONT
     */
    private fun getFontFile(context: Context): File? {
        return if (this !== SYSTEM_DEFAULT) {
            val directory = File(context.getExternalFilesDir(null), DIRECTORY)
            File(directory, "$name$version.ttf")
        } else {
            null
        }
    }

    fun getConfig(context: Context): FileEmojiCompatConfig {
        return FileEmojiCompatConfig(context, getLatestFontFile(context))
    }

    fun isDownloaded(context: Context): Boolean {
        return this === SYSTEM_DEFAULT || getFontFile(context)?.exists() == true || fontFileExists(context)
    }

    /**
     * Checks whether there is already a font version that satisfies the current version, i.e. it
     * has a higher or equal version code.
     *
     * @param context The Context
     * @return Whether there is a font file with a higher or equal version code to the current
     */
    private fun fontFileExists(context: Context): Boolean {
        val existingFontFiles = getExistingFontFiles(context)
        return if (existingFontFiles.isNotEmpty()) {
            compareVersions(existingFontFiles.last().second, versionCode) >= 0
        } else {
            false
        }
    }

    /**
     * Deletes any older version of a font
     *
     * @param context The current Context
     */
    private fun deleteOldVersions(context: Context) {
        val existingFontFiles = getExistingFontFiles(context)
        Log.d(TAG, "deleting old versions...")
        Log.d(TAG, String.format("deleteOldVersions: Found %d other font files", existingFontFiles.size))
        for (fileExists in existingFontFiles) {
            if (compareVersions(fileExists.second, versionCode) < 0) {
                val file = fileExists.first
                // Uses side effects!
                Log.d(
                    TAG,
                    String.format(
                        "Deleted %s successfully: %s", file.absolutePath,
                        file.delete()
                    )
                )
            }
        }
    }

    /**
     * Loads all font files that are inside the files directory into an ArrayList with the information
     * on whether they are older than the currently available version or not.
     *
     * @param context The Context
     */
    private fun getExistingFontFiles(context: Context): List<Pair<File, List<Int>>> {
        // Only load it once
        existingFontFileCache?.let {
            return it
        }
        // If we call this on the system default font, just return nothing...
        if (this === SYSTEM_DEFAULT) {
            existingFontFileCache = emptyList()
            return emptyList()
        }

        val directory = File(context.getExternalFilesDir(null), DIRECTORY)
        // It will search for old versions using a regex that matches the font's name plus
        // (if present) a version code. No version code will be regarded as version 0.
        val fontRegex = "$name(\\d+(\\.\\d+)*)?\\.ttf".toPattern()
        val ttfFilter = FilenameFilter { _, name: String -> name.endsWith(".ttf") }
        val foundFontFiles = directory.listFiles(ttfFilter).orEmpty()
        Log.d(
            TAG,
            String.format(
                "loadExistingFontFiles: %d other font files found",
                foundFontFiles.size
            )
        )

        return foundFontFiles.map { file ->
            val matcher = fontRegex.matcher(file.name)
            val versionCode = if (matcher.matches()) {
                val version = matcher.group(1)
                getVersionCode(version)
            } else {
                listOf(0)
            }
            Pair(file, versionCode)
        }.sortedWith { a, b ->
            compareVersions(a.second, b.second)
        }.also {
            existingFontFileCache = it
        }
    }

    /**
     * Returns the current or latest version of this font file (if there is any)
     *
     * @param context The Context
     * @return The file for this font with the current or (if not existent) highest version code or null if there is no file for this font.
     */
    private fun getLatestFontFile(context: Context): File? {
        val current = getFontFile(context)
        if (current != null && current.exists()) return current
        val existingFontFiles = getExistingFontFiles(context)
        return existingFontFiles.firstOrNull()?.first
    }

    private fun getVersionCode(version: String?): List<Int> {
        if (version == null) return listOf(0)
        return version.split(".").map {
            it.toIntOrNull() ?: 0
        }
    }

    fun downloadFontFile(
        context: Context,
        okHttpClient: OkHttpClient
    ): Observable<Float> {
        return Observable.create { emitter: ObservableEmitter<Float> ->
            // It is possible (and very likely) that the file does not exist yet
            val downloadFile = getFontFile(context)!!
            if (!downloadFile.exists()) {
                downloadFile.parentFile?.mkdirs()
                downloadFile.createNewFile()
            }
            val request = Request.Builder().url(url)
                .build()

            val sink = downloadFile.sink().buffer()
            var source: Source? = null
            try {
                // Download!
                val response = okHttpClient.newCall(request).execute()

                val responseBody = response.body
                if (response.isSuccessful && responseBody != null) {
                    val size = response.length()
                    var progress = 0f
                    source = responseBody.source()
                    try {
                        while (!emitter.isDisposed) {
                            sink.write(source, CHUNK_SIZE)
                            progress += CHUNK_SIZE.toFloat()
                            if (size > 0) {
                                emitter.onNext(progress / size)
                            } else {
                                emitter.onNext(-1f)
                            }
                        }
                    } catch (ex: EOFException) {
                        /*
                         This means we've finished downloading the file since sink.write
                         will throw an EOFException when the file to be read is empty.
                        */
                    }
                } else {
                    Log.e(TAG, "Downloading $url failed. Status code: ${response.code}")
                    emitter.tryOnError(Exception())
                }
            } catch (ex: IOException) {
                Log.e(TAG, "Downloading $url failed.", ex)
                downloadFile.deleteIfExists()
                emitter.tryOnError(ex)
            } finally {
                source?.close()
                sink.close()
                if (emitter.isDisposed) {
                    downloadFile.deleteIfExists()
                } else {
                    deleteOldVersions(context)
                    emitter.onComplete()
                }
            }
        }
            .subscribeOn(Schedulers.io())
    }

    /**
     * Deletes the downloaded file, if it exists. Should be called when a download gets cancelled.
     */
    fun deleteDownloadedFile(context: Context) {
        getFontFile(context)?.deleteIfExists()
    }

    override fun toString(): String {
        return display
    }

    companion object {
        private const val TAG = "EmojiCompatFont"

        /**
         * This String represents the sub-directory the fonts are stored in.
         */
        private const val DIRECTORY = "emoji"

        private const val CHUNK_SIZE = 4096L

        // The system font gets some special behavior...
        val SYSTEM_DEFAULT = EmojiCompatFont(
            "system-default",
            "System Default",
            R.string.caption_systememoji,
            R.drawable.ic_emoji_34dp,
            "",
            "0"
        )
        val BLOBMOJI = EmojiCompatFont(
            "Blobmoji",
            "Blobmoji",
            R.string.caption_blobmoji,
            R.drawable.ic_blobmoji,
            "https://tusky.app/hosted/emoji/BlobmojiCompat.ttf",
            "14.0.1"
        )
        val TWEMOJI = EmojiCompatFont(
            "Twemoji",
            "Twemoji",
            R.string.caption_twemoji,
            R.drawable.ic_twemoji,
            "https://tusky.app/hosted/emoji/TwemojiCompat.ttf",
            "14.0.0"
        )
        val NOTOEMOJI = EmojiCompatFont(
            "NotoEmoji",
            "Noto Emoji",
            R.string.caption_notoemoji,
            R.drawable.ic_notoemoji,
            "https://tusky.app/hosted/emoji/NotoEmojiCompat.ttf",
            "14.0.0"
        )

        /**
         * This array stores all available EmojiCompat fonts.
         * References to them can simply be saved by saving their indices
         */
        val FONTS = listOf(SYSTEM_DEFAULT, BLOBMOJI, TWEMOJI, NOTOEMOJI)

        /**
         * Returns the Emoji font associated with this ID
         *
         * @param id the ID of this font
         * @return the corresponding font. Will default to SYSTEM_DEFAULT if not in range.
         */
        fun byId(id: Int): EmojiCompatFont = FONTS.getOrElse(id) { SYSTEM_DEFAULT }

        /**
         * Compares two version codes to each other
         *
         * @param versionA The first version
         * @param versionB The second version
         * @return -1 if versionA < versionB, 1 if versionA > versionB and 0 otherwise
         */
        @VisibleForTesting
        fun compareVersions(versionA: List<Int>, versionB: List<Int>): Int {
            val len = max(versionB.size, versionA.size)
            for (i in 0 until len) {

                val vA = versionA.getOrElse(i) { 0 }
                val vB = versionB.getOrElse(i) { 0 }

                // It needs to be decided on the next level
                if (vA == vB) continue
                // Okay, is version B newer or version A?
                return vA.compareTo(vB)
            }

            // The versions are equal
            return 0
        }

        /**
         * This method is needed because when transparent compression is used OkHttp reports
         * [ResponseBody.contentLength] as -1. We try to get the header which server sent
         * us manually here.
         *
         * @see [OkHttp issue 259](https://github.com/square/okhttp/issues/259)
         */
        private fun Response.length(): Long {
            networkResponse?.let {
                val header = it.header("Content-Length") ?: return -1
                return header.toLongOrDefault(-1)
            }

            // In case it's a fully cached response
            return body?.contentLength() ?: -1
        }

        private fun File.deleteIfExists() {
            if (exists() && !delete()) {
                Log.e(TAG, "Could not delete file $this")
            }
        }
    }
}
