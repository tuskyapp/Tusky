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

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.DialogImageDescriptionBinding
import com.keylesspalace.tusky.util.getParcelableCompat
import com.keylesspalace.tusky.util.hide

// https://github.com/tootsuite/mastodon/blob/c6904c0d3766a2ea8a81ab025c127169ecb51373/app/models/media_attachment.rb#L32
private const val MEDIA_DESCRIPTION_CHARACTER_LIMIT = 1500

class CaptionDialog : DialogFragment() {
    private lateinit var listener: Listener

    private lateinit var binding: DialogImageDescriptionBinding

    private var animatable: Animatable? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val localId = arguments?.getInt(LOCAL_ID_ARG) ?: error("Missing localId")
        val inset = requireContext().resources.getDimensionPixelSize(R.dimen.dialog_inset)
        return MaterialAlertDialogBuilder(requireContext())
            .setView(createView(savedInstanceState))
            .setBackgroundInsetTop(inset)
            .setBackgroundInsetEnd(inset)
            .setBackgroundInsetBottom(inset)
            .setBackgroundInsetStart(inset)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                listener.onUpdateDescription(localId, binding.imageDescriptionText.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun createView(savedInstanceState: Bundle?): View {
        binding = DialogImageDescriptionBinding.inflate(layoutInflater)
        val imageView = binding.imageDescriptionView
        imageView.maxZoom = 6f
        val imageDescriptionText = binding.imageDescriptionText
        imageDescriptionText.post {
            imageDescriptionText.requestFocus()
            imageDescriptionText.setSelection(imageDescriptionText.length())
        }

        binding.imageDescriptionText.hint = resources.getQuantityString(
            R.plurals.hint_describe_for_visually_impaired,
            MEDIA_DESCRIPTION_CHARACTER_LIMIT,
            MEDIA_DESCRIPTION_CHARACTER_LIMIT
        )
        binding.imageDescriptionText.filters = arrayOf(InputFilter.LengthFilter(MEDIA_DESCRIPTION_CHARACTER_LIMIT))
        binding.imageDescriptionText.setText(arguments?.getString(EXISTING_DESCRIPTION_ARG))
        savedInstanceState?.getCharSequence(DESCRIPTION_KEY)?.let {
            binding.imageDescriptionText.setText(it)
        }

        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false) // Dialog is full screen anyway. But without this, taps in navbar while keyboard is up can dismiss the dialog.

        val previewUri = arguments?.getParcelableCompat<Uri>(PREVIEW_URI_ARG) ?: error("Preview Uri is null")

        // Load the image and manually set it into the ImageView because it doesn't have a fixed size.
        Glide.with(this)
            .load(previewUri)
            .downsample(DownsampleStrategy.CENTER_INSIDE)
            .into(object : CustomTarget<Drawable>(4096, 4096) {
                override fun onLoadCleared(placeholder: Drawable?) {
                    imageView.setImageDrawable(placeholder)
                }

                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    if (resource is Animatable) {
                        resource.callback = object : Drawable.Callback {
                            override fun invalidateDrawable(who: Drawable) {
                                imageView.invalidate()
                            }

                            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                                imageView.postDelayed(what, `when`)
                            }

                            override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                                imageView.removeCallbacks(what)
                            }
                        }
                        resource.start()
                        animatable = resource
                    }
                    imageView.setImageDrawable(resource)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    imageView.hide()
                }
            })
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.apply {
            window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putCharSequence(DESCRIPTION_KEY, binding.imageDescriptionText.text)
        super.onSaveInstanceState(outState)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener ?: error("Activity is not ComposeCaptionDialog.Listener")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        animatable?.stop()
        (animatable as? Drawable?)?.callback = null
    }

    interface Listener {
        fun onUpdateDescription(localId: Int, description: String)
    }

    companion object {
        fun newInstance(localId: Int, existingDescription: String?, previewUri: Uri) =
            CaptionDialog().apply {
                arguments = bundleOf(
                    LOCAL_ID_ARG to localId,
                    EXISTING_DESCRIPTION_ARG to existingDescription,
                    PREVIEW_URI_ARG to previewUri
                )
            }

        private const val DESCRIPTION_KEY = "description"
        private const val EXISTING_DESCRIPTION_ARG = "existing_description"
        private const val PREVIEW_URI_ARG = "preview_uri"
        private const val LOCAL_ID_ARG = "local_id"
    }
}
