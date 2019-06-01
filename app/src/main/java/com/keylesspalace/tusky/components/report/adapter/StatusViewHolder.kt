package com.keylesspalace.tusky.components.report.adapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.entity.Status
import kotlinx.android.synthetic.main.item_report_status.view.*

class StatusViewHolder(itemView: View, private val checkedChange: (String, Boolean) -> Unit) : RecyclerView.ViewHolder(itemView) {
    private var status: Status? = null

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
    }
}