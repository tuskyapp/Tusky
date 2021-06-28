/* Copyright 2021 Tusky Contributors
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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

class PickMediaFiles : ActivityResultContract<Boolean, List<Uri>>() {
    override fun createIntent(context: Context, allowMultiple: Boolean): Intent {
        return Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .apply {
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*", "audio/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
            }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode == Activity.RESULT_OK) {
            val intentData = intent?.data
            val clipData = intent?.clipData
            if (intentData != null) {
                // Single media, upload it and done.
                return listOf(intentData)
            } else if (clipData != null) {
                val result: MutableList<Uri> = mutableListOf()
                for (i in 0 until clipData.itemCount) {
                    result.add(clipData.getItemAt(i).uri)
                }
                return result
            }
        }
        return emptyList()
    }
}
