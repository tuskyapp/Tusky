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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import at.connyduck.sparkbutton.helpers.Utils
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.util.Util
import com.github.chrisbanes.photoview.OnPhotoTapListener
import com.github.chrisbanes.photoview.PhotoView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.DialogFocusBinding
import com.keylesspalace.tusky.entity.Attachment.Focus
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.locks.Lock

fun <T> T.makeFocusDialog(
    existingFocus: Focus?,
    previewUri: Uri,
    onUpdateFocus: suspend (Focus) -> Boolean
) where T : Activity, T : LifecycleOwner {
    val focus = existingFocus ?: Focus(0.0f, 0.0f) // Default to center

    val dialogBinding = DialogFocusBinding.inflate(layoutInflater)

    dialogBinding.focusIndicator.setFocus(focus)

    Glide.with(this)
        .load(previewUri)
        .downsample(DownsampleStrategy.CENTER_INSIDE)
        .listener(object: RequestListener<Drawable> {
            override fun onLoadFailed(p0: GlideException?, p1:Any?, p2:Target<Drawable?>?, p3: Boolean): Boolean {
                return false
            }

            override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable?>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                dialogBinding.focusIndicator.setImageSize(resource!!.getIntrinsicWidth(), resource.getIntrinsicHeight())
                return false
            }
        })
        .into(dialogBinding.imageView)

    val okListener = { dialog: DialogInterface, _: Int ->
        lifecycleScope.launch {
            if (!onUpdateFocus(dialogBinding.focusIndicator.getFocus())) {
                showFailedFocusMessage()
            }
        }
        dialog.dismiss()
    }

    val dialog = AlertDialog.Builder(this)
        .setView(dialogBinding.root)
        .setPositiveButton(android.R.string.ok, okListener)
        .setNegativeButton(android.R.string.cancel, null)
        .create()

    val window = dialog.window
    window?.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    )

    dialog.show()
}

private fun Activity.showFailedFocusMessage() {
    Toast.makeText(this, R.string.error_failed_set_focus, Toast.LENGTH_SHORT).show()
}
