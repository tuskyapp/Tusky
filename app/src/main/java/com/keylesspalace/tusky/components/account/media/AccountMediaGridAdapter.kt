package com.keylesspalace.tusky.components.account.media

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.view.SquareImageView
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import java.util.Random

class AccountMediaGridAdapter : PagingDataAdapter<AttachmentViewData, AccountMediaGridAdapter.MediaViewHolder>(
    object : DiffUtil.ItemCallback<AttachmentViewData>() {
        override fun areItemsTheSame(oldItem: AttachmentViewData, newItem: AttachmentViewData): Boolean {
            return oldItem.statusId == newItem.statusId && oldItem.attachment.id == newItem.attachment.id
        }

        override fun areContentsTheSame(oldItem: AttachmentViewData, newItem: AttachmentViewData): Boolean {
            return oldItem == newItem
        }
    }
) {

    var baseItemColor = Color.BLACK

    private val itemBgBaseHSV = FloatArray(3)
    private val random = Random()

    override fun onAttachedToRecyclerView(recycler_view: RecyclerView) {
        val hsv = FloatArray(3)
        Color.colorToHSV(baseItemColor, hsv)
        super.onAttachedToRecyclerView(recycler_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountMediaGridAdapter.MediaViewHolder {
        val view = SquareImageView(parent.context)
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        itemBgBaseHSV[2] = random.nextFloat() * (1f - 0.3f) + 0.3f
        holder.imageView.setBackgroundColor(Color.HSVToColor(itemBgBaseHSV))
        getItem(position)?.let { item ->
            Glide.with(holder.imageView)
                .load(item.attachment.previewUrl)
                .centerInside()
                .into(holder.imageView)
        }
    }


    inner class MediaViewHolder(val imageView: ImageView) :
        RecyclerView.ViewHolder(imageView),
        View.OnClickListener {
        init {
            itemView.setOnClickListener(this)
        }

        // saving some allocations
        override fun onClick(v: View?) {
            //viewMedia(items, bindingAdapterPosition, imageView)
        }
    }
}