package com.keylesspalace.tusky.components.report.adapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.report.model.StatusViewState
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.util.DateUtils
import com.keylesspalace.tusky.util.LinkHelper
import com.keylesspalace.tusky.util.MediaPreviewListener
import com.keylesspalace.tusky.util.setMediaPreviews
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
        LinkHelper.setClickableText(itemView.statusContent, status, clickHandler)
        itemView.statusSelection.isChecked = isChecked
        val sensitive = status.sensitive
        setMediaPreviews(itemView, mediaPreviewEnabled, status.attachments, sensitive, previewListener,
                viewState.isMediaShow(status.id, status.sensitive),
                mediaViewHeight)

        setCreatedAt(status.createdAt)
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