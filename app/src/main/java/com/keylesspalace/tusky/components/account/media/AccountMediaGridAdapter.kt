package com.keylesspalace.tusky.components.account.media

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.setPadding
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemAccountMediaBinding
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.decodeBlurHash
import com.keylesspalace.tusky.util.getFormattedDescription
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import java.util.Random

class AccountMediaGridAdapter(
    private val alwaysShowSensitiveMedia: Boolean,
    private val useBlurhash: Boolean,
    context: Context,
    private val onAttachmentClickListener: (AttachmentViewData, View) -> Unit
) : PagingDataAdapter<AttachmentViewData, BindingHolder<ItemAccountMediaBinding>>(
    object : DiffUtil.ItemCallback<AttachmentViewData>() {
        override fun areItemsTheSame(oldItem: AttachmentViewData, newItem: AttachmentViewData): Boolean {
            return oldItem.attachment.id == newItem.attachment.id
        }

        override fun areContentsTheSame(oldItem: AttachmentViewData, newItem: AttachmentViewData): Boolean {
            return oldItem == newItem
        }
    }
) {

    private val baseItemBackgroundColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.BLACK)
    private val videoIndicator = AppCompatResources.getDrawable(context, R.drawable.ic_play_indicator)
    private val mediaHiddenDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_hide_media_24dp)

    private val itemBgBaseHSV = FloatArray(3)
    private val random = Random()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemAccountMediaBinding> {
        val binding = ItemAccountMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        Color.colorToHSV(baseItemBackgroundColor, itemBgBaseHSV)
        itemBgBaseHSV[2] = itemBgBaseHSV[2] + random.nextFloat() / 3f - 1f / 6f
        binding.root.setBackgroundColor(Color.HSVToColor(itemBgBaseHSV))
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemAccountMediaBinding>, position: Int) {
        val context = holder.binding.root.context
        getItem(position)?.let { item ->

            val imageView = holder.binding.accountMediaImageView
            val overlay = holder.binding.accountMediaImageViewOverlay

            val blurhash = item.attachment.blurhash
            val placeholder = if (useBlurhash && blurhash != null) {
                decodeBlurHash(context, blurhash)
            } else {
                null
            }

            if (item.attachment.type == Attachment.Type.AUDIO) {
                overlay.hide()

                imageView.setPadding(context.resources.getDimensionPixelSize(R.dimen.profile_media_audio_icon_padding))

                Glide.with(imageView)
                    .load(R.drawable.ic_music_box_preview_24dp)
                    .centerInside()
                    .into(imageView)

                imageView.contentDescription = item.attachment.getFormattedDescription(context)
            } else if (item.sensitive && !item.isRevealed && !alwaysShowSensitiveMedia) {
                overlay.show()
                overlay.setImageDrawable(mediaHiddenDrawable)

                imageView.setPadding(0)

                Glide.with(imageView)
                    .load(placeholder)
                    .centerInside()
                    .into(imageView)

                imageView.contentDescription = imageView.context.getString(R.string.post_media_hidden_title)
            } else {
                if (item.attachment.type == Attachment.Type.VIDEO || item.attachment.type == Attachment.Type.GIFV) {
                    overlay.show()
                    overlay.setImageDrawable(videoIndicator)
                } else {
                    overlay.hide()
                }

                imageView.setPadding(0)

                Glide.with(imageView)
                    .asBitmap()
                    .load(item.attachment.previewUrl)
                    .placeholder(placeholder)
                    .centerInside()
                    .into(imageView)

                imageView.contentDescription = item.attachment.getFormattedDescription(context)
            }

            holder.binding.root.setOnClickListener {
                onAttachmentClickListener(item, imageView)
            }

            holder.binding.root.setOnLongClickListener { view ->
                val description = item.attachment.getFormattedDescription(view.context)
                Toast.makeText(view.context, description, Toast.LENGTH_LONG).show()
                true
            }
        }
    }
}
