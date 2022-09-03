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
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import at.connyduck.sparkbutton.helpers.Utils
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.util.Util
import com.github.chrisbanes.photoview.OnPhotoTapListener
import com.github.chrisbanes.photoview.PhotoView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Attachment.Focus
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.locks.Lock

// Private, but necessary to implement BitmapTransformation, function extracted from Glide
private fun getAlphaSafeBitmap(
    pool: BitmapPool, maybeAlphaSafe: Bitmap
): Bitmap {
    val safeConfig: Bitmap.Config = getAlphaSafeConfig(maybeAlphaSafe)
    if (safeConfig == maybeAlphaSafe.config) {
        return maybeAlphaSafe
    }
    val argbBitmap = pool[maybeAlphaSafe.width, maybeAlphaSafe.height, safeConfig]
    Canvas(argbBitmap).drawBitmap(maybeAlphaSafe, 0.0f /*left*/, 0.0f /*top*/, null /*paint*/)

    // From Glide: "We now own this Bitmap. It's our responsibility to replace it in the pool outside this method
    // when we're finished with it."
    return argbBitmap
}

// Private, but necessary to implement BitmapTransformation, function extracted from Glide
private fun getAlphaSafeConfig(inBitmap: Bitmap): Bitmap.Config {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Avoid short circuiting the sdk check.
        if (Bitmap.Config.RGBA_F16 == inBitmap.config) { // NOPMD
            return Bitmap.Config.RGBA_F16
        }
    }
    return Bitmap.Config.ARGB_8888
}

/** Glide BitmapTransformation which overlays a highlight on a focus point. */
class HighlightFocus(val focus: Focus) : BitmapTransformation() {
    override fun transform(
        pool: BitmapPool, inBitmap: Bitmap, outWidth: Int, outHeight: Int
    ): Bitmap {
        // Draw overlaid target
        val bitmapDrawableLock: Lock = TransformationUtils.getBitmapDrawableLock()
        val safeConfig: Bitmap.Config = getAlphaSafeConfig(inBitmap)
        val toTransform: Bitmap = getAlphaSafeBitmap(pool, inBitmap)
        val result = pool[toTransform.width, toTransform.height, safeConfig]

        val plainPaint = Paint()
        val strokePaint = Paint()
        strokePaint.setAntiAlias(true)
        strokePaint.setStyle(Paint.Style.STROKE)
        val strokeWidth = 8.0f;
        strokePaint.setStrokeWidth(strokeWidth)
        strokePaint.setColor(Color.RED)

        bitmapDrawableLock.lock()
        try {
            val canvas: Canvas = Canvas(result)

            canvas.drawBitmap(toTransform, Matrix.IDENTITY_MATRIX, plainPaint)

            // Canvas range is 0..size Y-down but Mastodon API range is -1..1 Y-up
            val x = (focus.x+1.0f)/2.0f*result.width.toFloat();
            val y = (1.0f-focus.y)/2.0f*result.height.toFloat();
            canvas.drawCircle(x, y, Math.min(result.width, result.height).toFloat()/4.0f, strokePaint)
            canvas.drawCircle(x, y, strokeWidth/2.0f, strokePaint)

            canvas.setBitmap(null)
        } finally {
            bitmapDrawableLock.unlock()
        }

        if (!toTransform.equals(inBitmap)) {
            pool.put(toTransform)
        }

        return result
    }

    // Remaining methods are boilerplate for BitmapTransformations to work with image caching.
    override fun equals(other: Any?): Boolean {
        if (other is HighlightFocus) {
            return focus == other.focus
        }
        return false
    }

    override fun hashCode(): Int {
        return Util.hashCode(ID.hashCode(), Util.hashCode(Util.hashCode(focus.x), Util.hashCode(focus.y)))
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
        val radiusData: ByteArray = ByteBuffer.allocate(8).putFloat(focus.x).putFloat(focus.y).array()
        messageDigest.update(radiusData)
    }

    companion object {
        private const val ID = "com.keylesspalace.tusky.components.compose.dialog.HighlightFocus"
        private val ID_BYTES = ID.toByteArray(CHARSET)
    }
}

fun <T> T.makeFocusDialog(
    existingFocus: Focus?,
    previewUri: Uri,
    onUpdateFocus: suspend (Focus) -> Boolean
) where T : Activity, T : LifecycleOwner {
    var focus = existingFocus ?: Focus(0.0f, 0.0f) // Default to center

    val dialogLayout = LinearLayout(this)
    val padding = Utils.dpToPx(this, 8)
    dialogLayout.setPadding(padding, padding, padding, padding)

    dialogLayout.orientation = LinearLayout.VERTICAL
    val imageView = PhotoView(this).apply {
        maximumScale = 6f
        setOnPhotoTapListener(object : OnPhotoTapListener {
            override fun onPhotoTap(view: ImageView, x: Float, y: Float) {
                focus = Focus(x * 2 - 1, 1 - y * 2) // PhotoView range is 0..1 Y-down but Mastodon API range is -1..1 Y-up
            }
        })
    }

    val margin = Utils.dpToPx(this, 4)
    dialogLayout.addView(imageView)
    (imageView.layoutParams as LinearLayout.LayoutParams).weight = 1f
    imageView.layoutParams.height = 0
    (imageView.layoutParams as LinearLayout.LayoutParams).setMargins(0, margin, 0, 0)

    val okListener = { dialog: DialogInterface, _: Int ->
        lifecycleScope.launch {
            if (!onUpdateFocus(focus)) {
                showFailedFocusMessage()
            }
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
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    )

    dialog.show()

    // Load the image and manually set it into the ImageView because it doesn't have a fixed  size.
    Glide.with(this)
        .load(previewUri)
        .downsample(DownsampleStrategy.CENTER_INSIDE)
        .transform(HighlightFocus(focus))
        .into(object : CustomTarget<Drawable>(4096, 4096) {
            override fun onLoadCleared(placeholder: Drawable?) {
                imageView.setImageDrawable(placeholder)
            }

            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                imageView.setImageDrawable(resource)
            }
        })
}

private fun Activity.showFailedFocusMessage() {
    Toast.makeText(this, R.string.error_failed_set_focus, Toast.LENGTH_SHORT).show()
}
