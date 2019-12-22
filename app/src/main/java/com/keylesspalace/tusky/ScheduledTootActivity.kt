package com.keylesspalace.tusky

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.keylesspalace.tusky.adapter.ScheduledTootAction
import com.keylesspalace.tusky.adapter.ScheduledTootAdapter
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.StatusScheduledEvent
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.ScheduledStatus
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.uber.autodispose.AutoDispose.autoDisposable
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_scheduled_toot.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject


class ScheduledTootActivity : BaseActivity(), ScheduledTootAction, Injectable {

    companion object {
        @JvmStatic
        fun newIntent(context: Context): Intent {
            return Intent(context, ScheduledTootActivity::class.java)
        }
    }

    lateinit var adapter: ScheduledTootAdapter

    @Inject
    lateinit var mastodonApi: MastodonApi
    @Inject
    lateinit var eventHub: EventHub

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scheduled_toot)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        setSupportActionBar(toolbar)
        val bar = supportActionBar
        if (bar != null) {
            bar.title = getString(R.string.title_scheduled_toot)
            bar.setDisplayHomeAsUpEnabled(true)
            bar.setDisplayShowHomeEnabled(true)
        }

        swipe_refresh_layout.setOnRefreshListener(this::refreshStatuses)

        scheduled_toot_list.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(this)
        scheduled_toot_list.layoutManager = layoutManager
        val divider = DividerItemDecoration(this, layoutManager.orientation)
        scheduled_toot_list.addItemDecoration(divider)
        adapter = ScheduledTootAdapter(this)
        scheduled_toot_list.adapter = adapter

        loadStatuses()

        eventHub.events
                .observeOn(AndroidSchedulers.mainThread())
                .`as`(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe { event ->
                    if (event is StatusScheduledEvent) {
                        refreshStatuses()
                    }
                }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun loadStatuses() {
        progress_bar.visibility = View.VISIBLE
        mastodonApi.scheduledStatuses()
                .enqueue(object : Callback<List<ScheduledStatus>> {
                    override fun onResponse(call: Call<List<ScheduledStatus>>, response: Response<List<ScheduledStatus>>) {
                        progress_bar.visibility = View.GONE
                        if (response.body().isNullOrEmpty()) {
                            errorMessageView.show()
                            errorMessageView.setup(R.drawable.elephant_friend_empty, R.string.message_empty,
                                    null)
                        } else {
                            show(response.body()!!)
                        }
                    }

                    override fun onFailure(call: Call<List<ScheduledStatus>>, t: Throwable) {
                        progress_bar.visibility = View.GONE
                        errorMessageView.show()
                        errorMessageView.setup(R.drawable.elephant_error, R.string.error_generic) {
                            errorMessageView.hide()
                            loadStatuses()
                        }
                    }
                })
    }

    private fun refreshStatuses() {
        swipe_refresh_layout.isRefreshing = true
        mastodonApi.scheduledStatuses()
                .enqueue(object : Callback<List<ScheduledStatus>> {
                    override fun onResponse(call: Call<List<ScheduledStatus>>, response: Response<List<ScheduledStatus>>) {
                        swipe_refresh_layout.isRefreshing = false
                        if (response.body().isNullOrEmpty()) {
                            errorMessageView.show()
                            errorMessageView.setup(R.drawable.elephant_friend_empty, R.string.message_empty,
                                    null)
                        } else {
                            show(response.body()!!)
                        }
                    }

                    override fun onFailure(call: Call<List<ScheduledStatus>>, t: Throwable) {
                        swipe_refresh_layout.isRefreshing = false
                    }
                })
    }

    fun show(statuses: List<ScheduledStatus>) {
        adapter.setItems(statuses)
        adapter.notifyDataSetChanged()
    }

    override fun edit(position: Int, item: ScheduledStatus?) {
        if (item == null) {
            return
        }
        val intent = ComposeActivity.startIntent(this, ComposeActivity.ComposeOptions(
                tootText = item.params.text,
                contentWarning = item.params.spoilerText,
                mediaAttachments = item.mediaAttachments,
                inReplyToId = item.params.inReplyToId,
                visibility = item.params.visibility,
                scheduledAt = item.scheduledAt,
                sensitive = item.params.sensitive
        ))
        startActivity(intent)
        delete(position, item)
    }

    override fun delete(position: Int, item: ScheduledStatus?) {
        if (item == null) {
            return
        }
        mastodonApi.deleteScheduledStatus(item.id)
                .enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        adapter.removeItem(position)
                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {

                    }
                })
    }
}
