package com.keylesspalace.tusky.components.report.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.AsyncPagedListDiffer
import androidx.paging.PagedList
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.report.model.StatusViewState
import com.keylesspalace.tusky.entity.Status

class StatusesAdapter(private val useAbsoluteTime: Boolean,
                      private val mediaPreviewEnabled: Boolean,
                      private val statusViewState: StatusViewState,
                      private val adapterHandler: AdapterHandler)
    : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val statusForPosition: (Int) -> Status? = { position: Int ->
        if (position != RecyclerView.NO_POSITION) differ.getItem(position) else null
    }

    private val differ: AsyncPagedListDiffer<Status> = AsyncPagedListDiffer(object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            notifyItemRangeInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position, count, payload)
        }
    }, AsyncDifferConfig.Builder<Status>(STATUS_COMPARATOR).build())

    fun submitList(list: PagedList<Status>) {
        differ.submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return StatusViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_report_status, parent, false),
                useAbsoluteTime, mediaPreviewEnabled, statusViewState, adapterHandler, statusForPosition)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        differ.getItem(position)?.let { status ->
            (holder as? StatusViewHolder)?.bind(status)
        }

    }

    override fun getItemCount(): Int = differ.itemCount

    companion object {

        val STATUS_COMPARATOR = object : DiffUtil.ItemCallback<Status>() {
            override fun areContentsTheSame(oldItem: Status, newItem: Status): Boolean =
                    oldItem == newItem

            override fun areItemsTheSame(oldItem: Status, newItem: Status): Boolean =
                    oldItem.id == newItem.id
        }

    }

}