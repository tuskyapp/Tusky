package com.keylesspalace.tusky.components.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.compose.view.ProgressImageView

class MediaPreviewAdapter(
        context: Context,
        private val onAddCaption: (ComposeActivity.QueuedMedia) -> Unit,
        private val onRemove: (ComposeActivity.QueuedMedia) -> Unit
) : RecyclerView.Adapter<MediaPreviewAdapter.PreviewViewHolder>() {

    fun submitList(list: List<ComposeActivity.QueuedMedia>) {
        this.differ.submitList(list)
    }

    private fun onMediaClick(position: Int, view: View) {
        val item = differ.currentList[position]
        val popup = PopupMenu(view.context, view)
        val addCaptionId = 1
        val removeId = 2
        popup.menu.add(0, addCaptionId, 0, R.string.action_set_caption)
        popup.menu.add(0, removeId, 0, R.string.action_remove)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                addCaptionId -> onAddCaption(item)
                removeId -> onRemove(item)
            }
            true
        }
        popup.show()
    }

    private val thumbnailViewSize =
            context.resources.getDimensionPixelSize(R.dimen.compose_media_preview_size)

    override fun getItemCount(): Int = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        return PreviewViewHolder(ProgressImageView(parent.context))
    }

    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        val item = differ.currentList[position]
        holder.progressImageView.setChecked(!item.description.isNullOrEmpty())
        holder.progressImageView.setProgress(item.uploadPercent)
        Glide.with(holder.itemView.context)
                .load(item.uri)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .dontAnimate()
                .into(holder.progressImageView)
    }

    val differ = AsyncListDiffer(this, object : DiffUtil.ItemCallback<ComposeActivity.QueuedMedia>() {
        override fun areItemsTheSame(oldItem: ComposeActivity.QueuedMedia, newItem: ComposeActivity.QueuedMedia): Boolean {
            return oldItem.localId == newItem.localId
        }

        override fun areContentsTheSame(oldItem: ComposeActivity.QueuedMedia, newItem: ComposeActivity.QueuedMedia): Boolean {
            return oldItem == newItem
        }
    })

    inner class PreviewViewHolder(val progressImageView: ProgressImageView)
        : RecyclerView.ViewHolder(progressImageView) {
        init {
            val layoutParams = ConstraintLayout.LayoutParams(thumbnailViewSize, thumbnailViewSize)
            val margin = itemView.context.resources
                    .getDimensionPixelSize(R.dimen.compose_media_preview_margin)
            val marginBottom = itemView.context.resources
                    .getDimensionPixelSize(R.dimen.compose_media_preview_margin_bottom)
            layoutParams.setMargins(margin, 0, margin, marginBottom)
            progressImageView.layoutParams = layoutParams
            progressImageView.scaleType = ImageView.ScaleType.CENTER_CROP
            progressImageView.setOnClickListener {
                onMediaClick(adapterPosition, progressImageView)
            }
        }
    }
}