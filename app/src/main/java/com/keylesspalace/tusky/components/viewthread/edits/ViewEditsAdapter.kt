package com.keylesspalace.tusky.components.viewthread.edits

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.util.aspectRatios
import com.keylesspalace.tusky.util.decodeBlurHash
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import com.keylesspalace.tusky.util.setClickableText
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.viewdata.toViewData

class ViewEditsAdapter(
    private val edits: List<StatusEdit>,
    private val animateAvatar: Boolean,
    private val animateEmoji: Boolean,
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

        loadAvatar(edit.account.avatar, binding.statusEditAvatar, avatarRadius, animateAvatar)

        val infoStringRes = if (position == 0) {
            R.string.status_created_info
        } else {
            R.string.status_edit_info
        }

        val timestamp = absoluteTimeFormatter.format(edit.createdAt, false)

        binding.statusEditInfo.text = context.getString(infoStringRes, edit.account.name, timestamp)

        if (edit.spoilerText.isEmpty()) {
            binding.statusEditContentWarningDescription.hide()
            binding.statusEditContentWarningSeparator.hide()
        } else {
            binding.statusEditContentWarningDescription.show()
            binding.statusEditContentWarningSeparator.show()
            binding.statusEditContentWarningDescription.text = edit.spoilerText.emojify(
                edit.emojis,
                binding.statusEditContentWarningDescription,
                animateEmoji
            )
        }

        val emojifiedText = edit.content.parseAsMastodonHtml().emojify(edit.emojis, binding.statusEditContent, animateEmoji)
        setClickableText(binding.statusEditContent, emojifiedText, emptyList(), emptyList(), listener)

        if (edit.poll == null) {
            binding.statusEditPollOptions.hide()
            binding.statusEditPollDescription.hide()
        } else {
            binding.statusEditPollOptions.show()
            binding.statusEditPollDescription.show()

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
                animateEmojis = animateEmoji,
                enabled = false
            )

            // not reported by the api
           /*binding.statusEditContentWarningDescription.text = context.getString(
                R.string.poll_info_time_absolute,
                absoluteTimeFormatter.format(edit.poll.expiresAt, false)
            ) */
        }

        if (edit.mediaAttachments.isEmpty()) {
            binding.statusEditMediaPreview.hide()
        } else {
            binding.statusEditMediaPreview.show()
            binding.statusEditMediaPreview.aspectRatios = edit.mediaAttachments.aspectRatios()

            binding.statusEditMediaPreview.forEachIndexed { index, wrapper, imageView, descriptionIndicator ->

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
                    ColorDrawable(ThemeUtils.getColor(context, R.attr.colorBackgroundAccent))
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
        }
    }

    override fun getItemCount() = edits.size
}
