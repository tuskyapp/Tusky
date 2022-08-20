package com.keylesspalace.tusky.components.account.media

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemAccountMediaBinding
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.decodeBlurHash
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import java.util.Random

class AccountMediaGridAdapter(
    private val alwaysShowSensitiveMedia: Boolean,
    private val useBlurhash: Boolean,
    @ColorInt private val baseItemBackgroundColor: Int
) : PagingDataAdapter<AttachmentViewData, BindingHolder<ItemAccountMediaBinding>>(
    object : DiffUtil.ItemCallback<AttachmentViewData>() {
        override fun areItemsTheSame(oldItem: AttachmentViewData, newItem: AttachmentViewData): Boolean {
            return oldItem.statusId == newItem.statusId && oldItem.attachment.id == newItem.attachment.id
        }

        override fun areContentsTheSame(oldItem: AttachmentViewData, newItem: AttachmentViewData): Boolean {
            return oldItem == newItem
        }
    }
) {
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
        getItem(position)?.let { item ->

            val imageView = holder.binding.accountMediaImageView
            val overlay = holder.binding.accountMediaImageViewOverlay

            val blurhash = item.attachment.blurhash
            val placeholder = if (useBlurhash && blurhash != null) {
                decodeBlurHash(imageView.context, blurhash)
            } else {
                null
            }

            if (item.sensitive && !item.isRevealed && !alwaysShowSensitiveMedia) {
                overlay.show()
                overlay.setImageDrawable(AppCompatResources.getDrawable(overlay.context, R.drawable.ic_hide_media_24dp))

                Glide.with(imageView)
                    .load(placeholder)
                    .centerInside()
                    .into(imageView)
            } else {
                if (item.attachment.type == Attachment.Type.VIDEO || item.attachment.type == Attachment.Type.GIFV) {
                    overlay.show()
                    overlay.setImageDrawable(AppCompatResources.getDrawable(overlay.context, R.drawable.ic_play_indicator))
                } else {
                    overlay.hide()
                }

                Glide.with(holder.binding.accountMediaImageView)
                    .asBitmap()
                    .load(item.attachment.previewUrl)
                    .placeholder(placeholder)
                    .centerInside()
                    .into(holder.binding.accountMediaImageView)
            }
        }
    }
}
