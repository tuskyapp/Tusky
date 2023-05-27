/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.util.Linkify
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import com.google.gson.GsonBuilder
import com.keylesspalace.tusky.BuildConfig.APPLICATION_ID
import com.keylesspalace.tusky.databinding.ActivityNotificationsBlackboxBinding
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BlackBoxReport(
    /** The version of this data structure */
    // Increment this number if you add/remove fields or change the semantics of a field
    val report_version: Int = 1,

    /** Tusky version name */
    val tusky_version_name: String = BuildConfig.VERSION_NAME,

    /** Most recent requests, oldest first */
    val entries: List<String>
)

class BlackBoxActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityNotificationsBlackboxBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.text.setTextIsSelectable(true)

        val report = BlackBoxReport(
            entries = BlackBox.blackbox.toList()
        )

        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(report).toString()
        val file = saveCrashData(json)

        binding.text.text = json
        Linkify.addLinks(binding.instructions, Linkify.WEB_URLS)

        // Share the JSON as a file (if it was successfully saved)
        binding.shareAsFile.isVisible = file != null
        file?.let {
            binding.shareAsFile.setOnClickListener {
                ShareCompat.IntentBuilder(this)
                    .setType("application/json")
                    .addStream(FileProvider.getUriForFile(this, "$APPLICATION_ID.fileprovider", file))
                    .setChooserTitle("Send notification debug data")
                    .startChooser()
            }
        }

        // Copy the JSON content to the clipboard
        binding.copyToClipboard.setOnClickListener {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.setPrimaryClip(ClipData.newPlainText("tusky_notifications_data", json))
            if (VERSION.SDK_INT <= VERSION_CODES.S_V2) {
                Toast.makeText(this, "Copied notifications report", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Save the crash data to a file in the cache directory so it can be shared.
     */
    private fun saveCrashData(json: String): File? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_hh-mm-ss", Locale.getDefault())
        val fileName = "tusky_notification_blackbox_${dateFormat.format(Date())}.json"
        val file = File(cacheDir, fileName)

        try {
            val writer = FileWriter(file)
            writer.append(json)
            writer.flush()
            writer.close()
        } catch (e: Exception) {
            Log.e(TAG, "writing $fileName failed: $e")
            return null
        }

        // Copy the file to the downloads directory
        if (VERSION.SDK_INT < VERSION_CODES.Q) {
            val target = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            file.inputStream().copyTo(target.outputStream(), DEFAULT_BUFFER_SIZE)
        } else {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )
            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    file.inputStream().copyTo(outputStream, DEFAULT_BUFFER_SIZE)
                }
            }
        }

        return file
    }

    companion object {
        private const val TAG = "BlackBoxActivity"

        fun getIntent(context: Context) = Intent(context, BlackBoxActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
