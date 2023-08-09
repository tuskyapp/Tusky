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

import android.app.ApplicationErrorReport
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import com.google.gson.GsonBuilder
import com.keylesspalace.tusky.BuildConfig.APPLICATION_ID
import com.keylesspalace.tusky.databinding.ActivityCrashReportBinding
import com.keylesspalace.tusky.network.RecordResponseInterceptor
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

data class CrashReport(
    /** The version of this data structure */
    // Increment this number if you add/remove fields or change the semantics of a field
    val report_version: Int = 1,

    /** Tusky version name */
    val tusky_version_name: String = BuildConfig.VERSION_NAME,

    /** Stack trace from the exception */
    val stackTrace: String,

    /** Most recent requests, oldest first */
    val requests: List<String>,

    /** Most recent responses, oldest first */
    val responses: List<String>
)

class CrashReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityCrashReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val exception = intent.getSerializableExtra("e") as Throwable

        val crashInfo = ApplicationErrorReport.CrashInfo(exception)

        binding.text.setTextIsSelectable(true)

        val queries = RecordResponseInterceptor.queries.toList()
        val report = CrashReport(
            stackTrace = crashInfo.stackTrace,
            requests = queries.map { "${it.req.method} ${it.req.url}" },
            responses = queries.map { it.body ?: it.e.toString() }
        )

        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(report).toString()
        val file = saveCrashData(json)

        binding.text.text = json

        // Share the JSON as a file (if it was successfully saved)
        binding.shareAsFile.isVisible = file != null
        file?.let {
            binding.shareAsFile.setOnClickListener {
                ShareCompat.IntentBuilder(this)
                    .setType("application/json")
                    .addStream(FileProvider.getUriForFile(this, "$APPLICATION_ID.fileprovider", file))
                    .setChooserTitle("Send crash details")
                    .startChooser()
            }
        }

        // Copy the JSON content to the clipboard
        binding.copyToClipboard.setOnClickListener {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.setPrimaryClip(ClipData.newPlainText("tusky_crash", json))
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                Toast.makeText(this, "Copied crash data", Toast.LENGTH_SHORT).show()
            }
        }

        // Restart Tusky
        binding.restart.setOnClickListener {
            val packageManager = this.packageManager
            val intent = packageManager.getLaunchIntentForPackage(this.packageName)!!
            val componentName = intent.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            startActivity(mainIntent)
            exitProcess(0)
        }

        // Going back is probably a bad idea as the app is in an unknown state. Explain to the user.
        // Restarting on back-press is a bad idea
        onBackPressedDispatcher.addCallback {
            Toast.makeText(this@CrashReportActivity, "Please tap 'Restart' to restart Tusky", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Save the crash data to a file in the cache directory so it can be shared.
     */
    private fun saveCrashData(json: String): File? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_hh-mm-ss", Locale.getDefault())
        val fileName = "tusky_crash_${dateFormat.format(Date())}.json"
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

        return file
    }

    companion object {
        private const val TAG = "CrashReportActivity"

        fun getIntent(context: Context, e: Throwable) = Intent(context, CrashReportActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("e", e)
        }
    }
}
