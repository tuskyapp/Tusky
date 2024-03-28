package com.keylesspalace.tusky.adapter

import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.view.View
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.text.buildSpannedString
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.NoUnderlineURLSpan
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.createClickableText
import com.keylesspalace.tusky.viewdata.StatusViewData
import java.text.DateFormat
import java.util.Locale

class StatusDetailedViewHolder(view: View) : StatusBaseViewHolder(view) {
    private val reblogs: TextView = view.findViewById(R.id.status_reblogs)
    private val favourites: TextView = view.findViewById(R.id.status_favourites)
    private val infoDivider: View = view.findViewById(R.id.status_info_divider)

    override fun setMetaData(
        statusViewData: StatusViewData.Concrete,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener
    ) {
        val status = statusViewData.actionable

        val visibility = status.visibility
        val context = metaInfo.context

        val visibilityIcon = getVisibilityIcon(visibility)
        val visibilityString = getVisibilityDescription(context, visibility)

        metaInfo.movementMethod = LinkMovementMethod.getInstance()
        metaInfo.text = buildSpannedString {
            if (visibilityIcon != null) {
                val visibilityIconSpan = ImageSpan(
                    visibilityIcon,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) DynamicDrawableSpan.ALIGN_CENTER else DynamicDrawableSpan.ALIGN_BASELINE
                )
                setSpan(
                    visibilityIconSpan,
                    0,
                    visibilityString.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            val metadataJoiner = context.getString(R.string.metadata_joiner)

            val createdAt = status.createdAt
            append(" ")
            append(dateFormat.format(createdAt))

            val editedAt = status.editedAt

            if (editedAt != null) {
                val editedAtString = context.getString(R.string.post_edited, dateFormat.format(editedAt))

                append(metadataJoiner)
                val spanStart = length
                val spanEnd = spanStart + editedAtString.length

                append(editedAtString)

                if (statusViewData.status.editedAt != null) {
                    val editedClickSpan: NoUnderlineURLSpan = object : NoUnderlineURLSpan("") {
                        override fun onClick(view: View) {
                            listener.onShowEdits(bindingAdapterPosition)
                        }
                    }

                    setSpan(editedClickSpan, spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            val language = status.language

            if (language != null) {
                append(metadataJoiner)
                append(language.uppercase(Locale.getDefault()))
            }

            val app = status.application

            if (app != null) {
                append(metadataJoiner)

                if (app.website != null) {
                    val text = createClickableText(app.name, app.website)
                    append(text)
                } else {
                    append(app.name)
                }
            }
        }
    }

    private fun setReblogAndFavCount(
        reblogCount: Int,
        favCount: Int,
        listener: StatusActionListener
    ) {
        reblogs.text = getReblogsText(reblogs.context, reblogCount)
        favourites.text = getFavsText(favourites.context, favCount)

        reblogs.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onShowReblogs(position)
            }
        }
        favourites.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onShowFavs(position)
            }
        }
    }

    override fun setupWithStatus(
        status: StatusViewData.Concrete,
        listener: StatusActionListener,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: Any?
    ) {
        // We never collapse statuses in the detail view
        val uncollapsedStatus = if (status.isCollapsible && status.isCollapsed) status.copyWithCollapsed(false) else status

        super.setupWithStatus(uncollapsedStatus, listener, statusDisplayOptions, payloads)
        setupCard(
            uncollapsedStatus,
            status.isExpanded,
            CardViewMode.FULL_WIDTH,
            statusDisplayOptions,
            listener
        ) // Always show card for detailed status
        if (payloads == null) {
            val actionable = uncollapsedStatus.actionable

            if (!statusDisplayOptions.hideStats) {
                setReblogAndFavCount(
                    actionable.reblogsCount,
                    actionable.favouritesCount,
                    listener
                )
            } else {
                hideQuantitativeStats()
            }
        }
    }

    private fun getVisibilityIcon(visibility: Status.Visibility): Drawable? {
        val visibilityIcon = when (visibility) {
            Status.Visibility.PUBLIC -> R.drawable.ic_public_24dp
            Status.Visibility.UNLISTED -> R.drawable.ic_lock_open_24dp
            Status.Visibility.PRIVATE -> R.drawable.ic_lock_outline_24dp
            Status.Visibility.DIRECT -> R.drawable.ic_email_24dp
            else -> return null
        }
        val visibilityDrawable = AppCompatResources.getDrawable(metaInfo.context, visibilityIcon) ?: return null
        val size = metaInfo.textSize.toInt()
        visibilityDrawable.setBounds(0, 0, size, size)
        visibilityDrawable.setTint(metaInfo.currentTextColor)
        return visibilityDrawable
    }

    private fun hideQuantitativeStats() {
        reblogs.isVisible = false
        favourites.isVisible = false
        infoDivider.isVisible = false
    }

    companion object {
        private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT)
    }
}
