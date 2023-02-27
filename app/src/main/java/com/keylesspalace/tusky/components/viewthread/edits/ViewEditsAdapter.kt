package com.keylesspalace.tusky.components.viewthread.edits

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.PollAdapter
import com.keylesspalace.tusky.adapter.PollAdapter.Companion.MULTIPLE
import com.keylesspalace.tusky.adapter.PollAdapter.Companion.SINGLE
import com.keylesspalace.tusky.databinding.ItemStatusEditBinding
import com.keylesspalace.tusky.entity.Attachment.Focus
import com.keylesspalace.tusky.entity.StatusEdit
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.util.AbsoluteTimeFormatter
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.aspectRatios
import com.keylesspalace.tusky.util.decodeBlurHash
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import com.keylesspalace.tusky.util.setClickableText
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.unicodeWrap
import com.keylesspalace.tusky.util.visible
import com.keylesspalace.tusky.viewdata.toViewData

class ViewEditsAdapter(
    private val edits: List<StatusEdit>,
    private val animateAvatars: Boolean,
    private val animateEmojis: Boolean,
    private val useBlurhash: Boolean,
    private val listener: LinkListener
) : RecyclerView.Adapter<BindingHolder<ItemStatusEditBinding>>() {

    private val absoluteTimeFormatter = AbsoluteTimeFormatter()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BindingHolder<ItemStatusEditBinding> {
        val binding = ItemStatusEditBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.statusEditMediaPreview.clipToOutline = true
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemStatusEditBinding>, position: Int) {

        val edit = edits[position]

        val binding = holder.binding

        val context = binding.root.context

        val avatarRadius: Int = context.resources
            .getDimensionPixelSize(R.dimen.avatar_radius_48dp)

        loadAvatar(edit.account.avatar, binding.statusEditAvatar, avatarRadius, animateAvatars)

        val infoStringRes = if (position == edits.size - 1) {
            R.string.status_created_info
        } else {
            R.string.status_edit_info
        }

        val timestamp = absoluteTimeFormatter.format(edit.createdAt, false)

        binding.statusEditInfo.text = context.getString(
            infoStringRes,
            edit.account.name.unicodeWrap(),
            timestamp
        ).emojify(edit.account.emojis, binding.statusEditInfo, animateEmojis)

        if (edit.spoilerText.isEmpty()) {
            binding.statusEditContentWarningDescription.hide()
            binding.statusEditContentWarningSeparator.hide()
        } else {
            binding.statusEditContentWarningDescription.show()
            binding.statusEditContentWarningSeparator.show()
            binding.statusEditContentWarningDescription.text = edit.spoilerText.emojify(
                edit.emojis,
                binding.statusEditContentWarningDescription,
                animateEmojis
            )
        }

        val emojifiedText = edit.content.parseAsMastodonHtml().emojify(edit.emojis, binding.statusEditContent, animateEmojis)
        setClickableText(binding.statusEditContent, emojifiedText, emptyList(), emptyList(), listener)

        if (edit.poll == null) {
            binding.statusEditPollOptions.hide()
            binding.statusEditPollDescription.hide()
        } else {
            binding.statusEditPollOptions.show()

            // not used for now since not reported by the api
            // https://github.com/mastodon/mastodon/issues/22571
            // binding.statusEditPollDescription.show()

            val pollAdapter = PollAdapter()
            binding.statusEditPollOptions.adapter = pollAdapter
            binding.statusEditPollOptions.layoutManager = LinearLayoutManager(context)

            pollAdapter.setup(
                options = edit.poll.options.map { it.toViewData(false) },
                voteCount = 0,
                votersCount = null,
                emojis = edit.emojis,
                mode = if (edit.poll.multiple) { // not reported by the api
                    MULTIPLE
                } else {
                    SINGLE
                },
                resultClickListener = null,
                animateEmojis = animateEmojis,
                enabled = false
            )
        }

        if (edit.mediaAttachments.isEmpty()) {
            binding.statusEditMediaPreview.hide()
            binding.statusEditMediaSensitivity.hide()
        } else {
            binding.statusEditMediaPreview.show()
            binding.statusEditMediaPreview.aspectRatios = edit.mediaAttachments.aspectRatios()

            binding.statusEditMediaPreview.forEachIndexed { index, imageView, descriptionIndicator ->

                val attachment = edit.mediaAttachments[index]
                val hasDescription = !attachment.description.isNullOrBlank()

                if (hasDescription) {
                    imageView.contentDescription = attachment.description
                } else {
                    imageView.contentDescription =
                        imageView.context.getString(R.string.action_view_media)
                }
                descriptionIndicator.visibility = if (hasDescription) View.VISIBLE else View.GONE

                val blurhash = attachment.blurhash

                val placeholder: Drawable = if (blurhash != null && useBlurhash) {
                    decodeBlurHash(context, blurhash)
                } else {
                    ColorDrawable(MaterialColors.getColor(imageView, R.attr.colorBackgroundAccent))
                }

                if (attachment.previewUrl.isNullOrEmpty()) {
                    imageView.removeFocalPoint()
                    Glide.with(imageView)
                        .load(placeholder)
                        .centerInside()
                        .into(imageView)
                } else {
                    val focus: Focus? = attachment.meta?.focus

                    if (focus != null) {
                        imageView.setFocalPoint(focus)
                        Glide.with(imageView.context)
                            .load(attachment.previewUrl)
                            .placeholder(placeholder)
                            .centerInside()
                            .addListener(imageView)
                            .into(imageView)
                    } else {
                        imageView.removeFocalPoint()
                        Glide.with(imageView)
                            .load(attachment.previewUrl)
                            .placeholder(placeholder)
                            .centerInside()
                            .into(imageView)
                    }
                }
            }
            binding.statusEditMediaSensitivity.visible(edit.sensitive)
        }
    }

    override fun getItemCount() = edits.size
}
