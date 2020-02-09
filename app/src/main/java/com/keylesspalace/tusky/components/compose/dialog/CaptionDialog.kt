/* Copyright 2019 Tusky Contributors
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

package com.keylesspalace.tusky.components.compose.dialog

import android.app.Activity
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.InputFilter
import android.text.InputType
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import at.connyduck.sparkbutton.helpers.Utils
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.chrisbanes.photoview.PhotoView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.util.withLifecycleContext

// https://github.com/tootsuite/mastodon/blob/1656663/app/models/media_attachment.rb#L94
private const val MEDIA_DESCRIPTION_CHARACTER_LIMIT = 420


fun <T> T.makeCaptionDialog(existingDescription: String?,
                            previewUri: Uri,
                            onUpdateDescription: (String) -> LiveData<Boolean>
) where T : Activity, T : LifecycleOwner {
    val dialogLayout = LinearLayout(this)
    val padding = Utils.dpToPx(this, 8)
    dialogLayout.setPadding(padding, padding, padding, padding)

    dialogLayout.orientation = LinearLayout.VERTICAL
    val imageView = PhotoView(this).apply {
        // If it seems a lot, try opening an image of A4 format or similar
        maximumScale = 6.0f
    }

    val displayMetrics = DisplayMetrics()
    windowManager.defaultDisplay.getMetrics(displayMetrics)

    val margin = Utils.dpToPx(this, 4)
    dialogLayout.addView(imageView)
    (imageView.layoutParams as LinearLayout.LayoutParams).weight = 1f
    imageView.layoutParams.height = 0
    (imageView.layoutParams as LinearLayout.LayoutParams).setMargins(0, margin, 0, 0)

    val input = EditText(this)
    input.hint = getString(R.string.hint_describe_for_visually_impaired,
            MEDIA_DESCRIPTION_CHARACTER_LIMIT)
    dialogLayout.addView(input)
    (input.layoutParams as LinearLayout.LayoutParams).setMargins(margin, margin, margin, margin)
    input.setLines(2)
    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    input.setText(existingDescription)
    input.filters = arrayOf(InputFilter.LengthFilter(MEDIA_DESCRIPTION_CHARACTER_LIMIT))

    val okListener = { dialog: DialogInterface, _: Int ->
        onUpdateDescription(input.text.toString())
        withLifecycleContext {
            onUpdateDescription(input.text.toString())
                    .observe { success -> if (!success) showFailedCaptionMessage() }

        }

        dialog.dismiss()
    }

    val dialog = AlertDialog.Builder(this)
            .setView(dialogLayout)
            .setPositiveButton(android.R.string.ok, okListener)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

    val window = dialog.window
    window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

    dialog.show()

    // Load the image and manually set it into the ImageView because it doesn't have a fixed
    // size. Maybe we should limit the size of CustomTarget
    Glide.with(this)
            .load(previewUri)
            .into(object : CustomTarget<Drawable>() {
                override fun onLoadCleared(placeholder: Drawable?) {}

                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    imageView.setImageDrawable(resource)
                }
            })
}


private fun Activity.showFailedCaptionMessage() {
    Toast.makeText(this, R.string.error_failed_set_caption, Toast.LENGTH_SHORT).show()
}
