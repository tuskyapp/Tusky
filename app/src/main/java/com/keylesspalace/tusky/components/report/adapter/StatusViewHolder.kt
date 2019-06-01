package com.keylesspalace.tusky.components.report.adapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.util.DateUtils
import kotlinx.android.synthetic.main.item_report_status2.view.*
import java.text.SimpleDateFormat
import java.util.*

class StatusViewHolder(itemView: View, private val checkedChange: (String, Boolean) -> Unit,
                       private val useAbsoluteTime: Boolean) : RecyclerView.ViewHolder(itemView) {
    private var status: Status? = null
    private val shortSdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val longSdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())


    init {
        itemView.statusSelection.setOnCheckedChangeListener { _, isChecked ->
            status?.id?.let { statusId ->
                checkedChange(statusId, isChecked)
            }
        }
    }

    fun bind(status: Status, isChecked: Boolean) {
        this.status = status
        itemView.statusContent.text = status.content
        itemView.statusSelection.isChecked = isChecked
        setCreatedAt(status.createdAt)
    }
    private fun setCreatedAt(createdAt: Date?) {
        if (useAbsoluteTime) {
            itemView.timestampInfo.text = getAbsoluteTime(createdAt)
        } else {
            val readout: String
            if (createdAt != null) {
                val then = createdAt.time
                val now = Date().time
                readout = DateUtils.getRelativeTimeSpanString(itemView.timestampInfo.context, then, now)
            } else {
                // unknown minutes~
                readout = "?m"
            }
            itemView.timestampInfo.text = readout
        }
    }

    private fun getAbsoluteTime(createdAt: Date?): String {
        val time: String
        if (createdAt != null) {
            if (android.text.format.DateUtils.isToday(createdAt.time)) {
                time = shortSdf.format(createdAt)
            } else {
                time = longSdf.format(createdAt)
            }
        } else {
            time = "??:??:??"
        }
        return time
    }
}