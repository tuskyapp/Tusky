/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.fragment

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.view.SquareImageView
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_timeline.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.*
import javax.inject.Inject

/**
 * Created by charlag on 26/10/2017.
 *
 * Fragment with multiple columns of media previews for the specified account.
 */

class AccountMediaFragment : BaseFragment(), Injectable {

    companion object {
        @JvmStatic
        fun newInstance(accountId: String): AccountMediaFragment {
            val fragment = AccountMediaFragment()
            val args = Bundle()
            args.putString(ACCOUNT_ID_ARG, accountId)
            fragment.arguments = args
            return fragment
        }

        private const val ACCOUNT_ID_ARG = "account_id"
        private const val TAG = "AccountMediaFragment"
    }

    @Inject
    lateinit var api: MastodonApi

    private val adapter = MediaGridAdapter()
    private var currentCall: Call<List<Status>>? = null
    private val statuses = mutableListOf<Status>()
    private var fetchingStatus = FetchingStatus.NOT_FETCHING
    private var isVisibleToUser: Boolean = false

    private val callback = object : Callback<List<Status>> {
        override fun onFailure(call: Call<List<Status>>?, t: Throwable?) {
            fetchingStatus = FetchingStatus.NOT_FETCHING

            if(isAdded) {
                swipeRefreshLayout.isRefreshing = false
                progressBar.visibility = View.GONE
                statusView.show()
                if (t is IOException) {
                    statusView.setup(R.drawable.elephant_offline, R.string.error_network) {
                        doInitialLoadingIfNeeded()
                    }
                } else {
                    statusView.setup(R.drawable.elephant_error, R.string.error_generic) {
                        doInitialLoadingIfNeeded()
                    }
                }
            }

            Log.d(TAG, "Failed to fetch account media", t)
        }

        override fun onResponse(call: Call<List<Status>>, response: Response<List<Status>>) {
            fetchingStatus = FetchingStatus.NOT_FETCHING
            if(isAdded) {
                swipeRefreshLayout.isRefreshing = false
                progressBar.visibility = View.GONE

                val body = response.body()
                body?.let { fetched ->
                    statuses.addAll(0, fetched)
                    // flatMap requires iterable but I don't want to box each array into list
                    val result = mutableListOf<AttachmentViewData>()
                    for (status in fetched) {
                        result.addAll(AttachmentViewData.list(status))
                    }
                    adapter.addTop(result)

                    if (statuses.isEmpty()) {
                        statusView.show()
                        statusView.setup(R.drawable.elephant_friend_empty, R.string.message_empty,
                                null)
                    }
                }
            }
        }
    }

    private val bottomCallback = object : Callback<List<Status>> {
        override fun onFailure(call: Call<List<Status>>?, t: Throwable?) {
            fetchingStatus = FetchingStatus.NOT_FETCHING

            Log.d(TAG, "Failed to fetch account media", t)
        }

        override fun onResponse(call: Call<List<Status>>, response: Response<List<Status>>) {
            fetchingStatus = FetchingStatus.NOT_FETCHING
            val body = response.body()
            body?.let { fetched ->
                Log.d(TAG, "fetched ${fetched.size} statuses")
                if (fetched.isNotEmpty()) Log.d(TAG, "first: ${fetched.first().id}, last: ${fetched.last().id}")
                statuses.addAll(fetched)
                Log.d(TAG, "now there are ${statuses.size} statuses")
                // flatMap requires iterable but I don't want to box each array into list
                val result = mutableListOf<AttachmentViewData>()
                for (status in fetched) {
                    result.addAll(AttachmentViewData.list(status))
                }
                adapter.addBottom(result)
            }
        }

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        val columnCount = view.context.resources.getInteger(R.integer.profile_media_column_count)
        val layoutManager = GridLayoutManager(view.context, columnCount)

        val bgRes = ThemeUtils.getColorId(view.context, R.attr.window_background)

        adapter.baseItemColor = ContextCompat.getColor(recyclerView.context, bgRes)

        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        val accountId = arguments?.getString(ACCOUNT_ID_ARG)

        swipeRefreshLayout.setOnRefreshListener {
            statusView.hide()
            if (fetchingStatus != FetchingStatus.NOT_FETCHING) return@setOnRefreshListener
            currentCall = if (statuses.isEmpty()) {
                fetchingStatus = FetchingStatus.INITIAL_FETCHING
                api.accountStatuses(accountId, null, null, null, null, true, null)
            } else {
                fetchingStatus = FetchingStatus.REFRESHING
                api.accountStatuses(accountId, null, statuses[0].id, null, null, true, null)
            }
            currentCall?.enqueue(callback)

        }
        swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ThemeUtils.getColor(view.context, android.R.attr.colorBackground))

        statusView.visibility = View.GONE

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recycler_view: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    val itemCount = layoutManager.itemCount
                    val lastItem = layoutManager.findLastCompletelyVisibleItemPosition()
                    if (itemCount <= lastItem + 3 && fetchingStatus == FetchingStatus.NOT_FETCHING) {
                        statuses.lastOrNull()?.let { last ->
                            Log.d(TAG, "Requesting statuses with max_id: ${last.id}, (bottom)")
                            fetchingStatus = FetchingStatus.FETCHING_BOTTOM
                            currentCall = api.accountStatuses(accountId, last.id, null, null, null, true, null)
                            currentCall?.enqueue(bottomCallback)
                        }
                    }
                }
            }
        })

        if (isVisibleToUser) doInitialLoadingIfNeeded()
    }

    // That's sort of an optimization to only load media once user has opened the tab
    // Attention: can be called before *any* lifecycle method!
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        this.isVisibleToUser = isVisibleToUser
        if (isVisibleToUser && isAdded) doInitialLoadingIfNeeded()
    }

    private fun doInitialLoadingIfNeeded() {
        if (isAdded) {
            statusView.hide()
        }
        val accountId = arguments?.getString(ACCOUNT_ID_ARG)
        if (fetchingStatus == FetchingStatus.NOT_FETCHING && statuses.isEmpty()) {
            fetchingStatus = FetchingStatus.INITIAL_FETCHING
            currentCall = api.accountStatuses(accountId, null, null, null, null, true, null)
            currentCall?.enqueue(callback)
        }
    }

    private fun viewMedia(items: List<AttachmentViewData>, currentIndex: Int, view: View?) {
        val type = items[currentIndex].attachment.type

        when (type) {
            Attachment.Type.IMAGE,
            Attachment.Type.GIFV,
            Attachment.Type.VIDEO -> {
                val intent = ViewMediaActivity.newIntent(context, items, currentIndex)
                if (view != null && activity != null) {
                    val url = items[currentIndex].attachment.url
                    ViewCompat.setTransitionName(view, url)
                    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(activity!!, view, url)
                    startActivity(intent, options.toBundle())
                } else {
                    startActivity(intent)
                }
            }
            Attachment.Type.UNKNOWN -> {
            }/* Intentionally do nothing. This case is here is to handle when new attachment
                 * types are added to the API before code is added here to handle them. So, the
                 * best fallback is to just show the preview and ignore requests to view them. */

        }
    }

    private enum class FetchingStatus {
        NOT_FETCHING, INITIAL_FETCHING, FETCHING_BOTTOM, REFRESHING
    }

    inner class MediaGridAdapter :
            RecyclerView.Adapter<MediaGridAdapter.MediaViewHolder>() {

        var baseItemColor = Color.BLACK

        private val items = mutableListOf<AttachmentViewData>()
        private val itemBgBaseHSV = FloatArray(3)
        private val random = Random()

        fun addTop(newItems: List<AttachmentViewData>) {
            items.addAll(0, newItems)
            notifyItemRangeInserted(0, newItems.size)
        }

        fun addBottom(newItems: List<AttachmentViewData>) {
            if (newItems.isEmpty()) return

            val oldLen = items.size
            items.addAll(newItems)
            notifyItemRangeInserted(oldLen, newItems.size)
        }

        override fun onAttachedToRecyclerView(recycler_view: RecyclerView) {
            val hsv = FloatArray(3)
            Color.colorToHSV(baseItemColor, hsv)
            super.onAttachedToRecyclerView(recycler_view)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
            val view = SquareImageView(parent.context)
            view.scaleType = ImageView.ScaleType.CENTER_CROP
            return MediaViewHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
            itemBgBaseHSV[2] = random.nextFloat() * (1f - 0.3f) + 0.3f
            holder.imageView.setBackgroundColor(Color.HSVToColor(itemBgBaseHSV))
            val item = items[position]

            val maxW = holder.imageView.context.resources.getInteger(R.integer.media_max_width)
            val maxH = holder.imageView.context.resources.getInteger(R.integer.media_max_height)

            Picasso.with(holder.imageView.context)
                    .load(item.attachment.previewUrl)
                    .resize(maxW, maxH)
                    .onlyScaleDown()
                    .centerInside()
                    .into(holder.imageView)
        }


        inner class MediaViewHolder(val imageView: ImageView)
            : RecyclerView.ViewHolder(imageView),
                View.OnClickListener {
            init {
                itemView.setOnClickListener(this)
            }

            // saving some allocations
            override fun onClick(v: View?) {
                viewMedia(items, adapterPosition, imageView)
            }
        }
    }
}