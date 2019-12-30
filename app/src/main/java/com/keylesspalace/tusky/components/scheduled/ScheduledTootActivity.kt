package com.keylesspalace.tusky.components.scheduled

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.StatusScheduledEvent
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.ScheduledStatus
import com.keylesspalace.tusky.util.Status
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.uber.autodispose.AutoDispose.autoDisposable
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_scheduled_toot.*
import kotlinx.android.synthetic.main.toolbar_basic.*
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
    lateinit var eventHub: EventHub
    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    lateinit var viewModel: ScheduledTootViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scheduled_toot)

        setSupportActionBar(toolbar)
        supportActionBar?.run {
            title = getString(R.string.title_scheduled_toot)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        swipeRefreshLayout.setOnRefreshListener(this::refreshStatuses)

        scheduledTootList.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(this)
        scheduledTootList.layoutManager = layoutManager
        val divider = DividerItemDecoration(this, layoutManager.orientation)
        scheduledTootList.addItemDecoration(divider)
        adapter = ScheduledTootAdapter(this)
        scheduledTootList.adapter = adapter

        viewModel = ViewModelProvider(this, viewModelFactory)[ScheduledTootViewModel::class.java]

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
        viewModel.data.observe(this, Observer {
            adapter.submitList(it)
        })

        viewModel.networkState.observe(this, Observer { (status) ->
            when(status) {
                Status.SUCCESS -> {
                    progressBar.hide()
                    swipeRefreshLayout.isRefreshing = false
                    errorMessageView.hide()
                }
                Status.RUNNING -> {
                    errorMessageView.hide()
                    if(viewModel.data.value?.loadedCount ?: 0 > 0) {
                        swipeRefreshLayout.isRefreshing = true
                    } else {
                        progressBar.show()
                    }
                }
                Status.FAILED -> {
                    if(viewModel.data.value?.loadedCount ?: 0 >= 0) {
                        progressBar.hide()
                        swipeRefreshLayout.isRefreshing = false
                        errorMessageView.setup(R.drawable.elephant_error, R.string.error_generic) {
                            refreshStatuses()
                        }
                        errorMessageView.show()
                    }
                }
            }

        })
    }

    private fun refreshStatuses() {
        viewModel.reload()
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

        viewModel.deleteScheduledStatus(item)
    }
}
