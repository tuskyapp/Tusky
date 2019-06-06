package com.keylesspalace.tusky.components.report.adapter

import android.text.Spanned
import android.text.TextUtils
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.report.model.StatusViewState
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.util.*
import kotlinx.android.synthetic.main.item_report_status.view.*
import java.text.SimpleDateFormat
import java.util.*

class StatusViewHolder(itemView: View,
                       private val checkedChange: (String, Boolean) -> Unit,
                       private val useAbsoluteTime: Boolean,
                       private val mediaPreviewEnabled: Boolean,
                       private val viewState: StatusViewState,
                       private val clickHandler: AdapterClickHandler) : RecyclerView.ViewHolder(itemView) {
    private var status: Status? = null
    private val shortSdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val longSdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
    private val mediaViewHeight = itemView.context.resources.getDimensionPixelSize(R.dimen.status_media_preview_height)

    private val previewListener = object : MediaPreviewListener {
        override fun onViewMedia(v: View?, idx: Int) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION)
                clickHandler.showMedia(v, status, idx)
        }

        override fun onContentHiddenChange(isShowing: Boolean) {
            status?.id?.let { id ->
                viewState.setMediaShow(id, isShowing)
            }
        }
    }

    init {
        itemView.statusSelection.setOnCheckedChangeListener { _, isChecked ->
            status?.id?.let { statusId ->
                checkedChange(statusId, isChecked)
            }
        }
    }

    fun bind(status: Status, isChecked: Boolean) {
        this.status = status

        itemView.statusSelection.isChecked = isChecked

        if (status.spoilerText.isBlank()){
            setTextVisible(true, status.content, status.mentions, status.emojis, clickHandler)
            itemView.statusContentWarningButton.visibility = View.GONE
            itemView.statusContentWarningDescription.visibility = View.GONE
        }
        else{
            val emojiSpoiler = CustomEmojiHelper.emojifyString(status.spoilerText, status.emojis, itemView.statusContentWarningDescription)
            itemView.statusContentWarningDescription.text = emojiSpoiler
            itemView.statusContentWarningDescription.visibility = View.VISIBLE
            itemView.statusContentWarningButton.visibility = View.VISIBLE
            itemView.statusContentWarningButton.isChecked = viewState.isContentShow(status.id,true)
            itemView.statusContentWarningButton.setOnCheckedChangeListener { _, isViewChecked ->
                itemView.statusContentWarningDescription.invalidate()
                viewState.setContentShow(status.id,isViewChecked)
                setTextVisible(isViewChecked, status.content, status.mentions, status.emojis, clickHandler)
            }
            setTextVisible(viewState.isContentShow(status.id,true), status.content, status.mentions, status.emojis, clickHandler)
        }

        val sensitive = status.sensitive
        setMediaPreviews(itemView, mediaPreviewEnabled, status.attachments, sensitive, previewListener,
                viewState.isMediaShow(status.id, status.sensitive),
                mediaViewHeight)

        setCreatedAt(status.createdAt)
    }


    private fun setTextVisible(expanded: Boolean,
                               content: Spanned,
                               mentions: Array<Status.Mention>?,
                               emojis: List<Emoji>,
                               listener: LinkListener) {
        if (expanded) {
            val emojifiedText = CustomEmojiHelper.emojifyText(content, emojis, itemView.statusContent)
            LinkHelper.setClickableText(itemView.statusContent, emojifiedText, mentions, listener)
        } else {
            LinkHelper.setClickableMentions(itemView.statusContent, mentions, listener)
        }
        if (TextUtils.isEmpty(itemView.statusContent.text)) {
            itemView.statusContent.visibility = View.GONE
        } else {
            itemView.statusContent.visibility = View.VISIBLE
        }
    }

    private fun setCreatedAt(createdAt: Date?) {
        if (useAbsoluteTime) {
            itemView.timestampInfo.text = getAbsoluteTime(createdAt)
        } else {
            itemView.timestampInfo.text = if (createdAt != null) {
                val then = createdAt.time
                val now = Date().time
                DateUtils.getRelativeTimeSpanString(itemView.timestampInfo.context, then, now)
            } else {
                // unknown minutes~
                "?m"
            }
        }
    }

    private fun getAbsoluteTime(createdAt: Date?): String {
        return if (createdAt != null) {
            if (android.text.format.DateUtils.isToday(createdAt.time)) {
                shortSdf.format(createdAt)
            } else {
                longSdf.format(createdAt)
            }
        } else {
            "??:??:??"
        }
    }
}