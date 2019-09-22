package com.keylesspalace.tusky.components.instancemute.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.instancemute.adapter.DomainMutesAdapter
import com.keylesspalace.tusky.components.instancemute.interfaces.InstanceActionListener
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.fragment.BaseFragment
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.view.EndlessOnScrollListener
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import com.uber.autodispose.autoDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.fragment_instance_list.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject

class InstanceListFragment: BaseFragment(), Injectable, InstanceActionListener {
    @Inject
    lateinit var api: MastodonApi

    private var fetching = false
    private var bottomId: String? = null
    private var adapter = DomainMutesAdapter(this)
    private lateinit var scrollListener: EndlessOnScrollListener

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_instance_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.setHasFixedSize(true)
        recyclerView.addItemDecoration(DividerItemDecoration(view.context, DividerItemDecoration.VERTICAL))
        recyclerView.adapter = adapter

        val layoutManager = LinearLayoutManager(view.context)
        recyclerView.layoutManager = layoutManager

        scrollListener = object : EndlessOnScrollListener(layoutManager) {
            override fun onLoadMore(totalItemsCount: Int, view: RecyclerView) {
                if (bottomId != null) {
                    fetchInstances(bottomId)
                }
            }
        }

        recyclerView.addOnScrollListener(scrollListener)
        fetchInstances()
    }

    override fun mute(mute: Boolean, instance: String, position: Int) {
        if (mute) {
            api.blockDomain(instance).enqueue(object: Callback<Any> {
                override fun onFailure(call: Call<Any>, t: Throwable) {
                    Log.e(TAG, "Error muting domain $instance")
                }

                override fun onResponse(call: Call<Any>, response: Response<Any>) {
                    if (response.isSuccessful) {
                        adapter.addItem(instance)
                    } else {
                        Log.e(TAG, "Error muting domain $instance")
                    }
                }
            })
        } else {
            api.unblockDomain(instance).enqueue(object: Callback<Any> {
                override fun onFailure(call: Call<Any>, t: Throwable) {
                    Log.e(TAG, "Error unmuting domain $instance")
                }

                override fun onResponse(call: Call<Any>, response: Response<Any>) {
                    if (response.isSuccessful) {
                        adapter.removeItem(position)
                        Snackbar.make(recyclerView, getString(R.string.confirmation_domain_unmuted, instance), Snackbar.LENGTH_LONG)
                                .setAction(R.string.action_undo) {
                                    mute(true, instance, position)
                                }
                                .show()
                    } else {
                        Log.e(TAG, "Error unmuting domain $instance")
                    }
                }
            })
        }
    }

    private fun fetchInstances(id: String? = null) {
        if (fetching) {
            return
        }
        fetching = true
        instanceProgressBar.show()

        if (id != null) {
            recyclerView.post { adapter.bottomLoading = true }
        }

        api.domainBlocks(id, bottomId)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDisposable(from(this, Lifecycle.Event.ON_DESTROY))
                .subscribe({ response ->
                    val instances = response.body()

                    if (response.isSuccessful && instances != null) {
                        onFetchInstancesSuccess(instances, response.headers().get("Link"))
                    } else {
                        onFetchInstancesFailure(Exception(response.message()))
                    }
                }, {throwable ->
                    onFetchInstancesFailure(throwable)
                })
    }

    private fun onFetchInstancesSuccess(instances: List<String>, linkHeader: String?) {
        adapter.bottomLoading = false
        instanceProgressBar.hide()

        val links = HttpHeaderLink.parse(linkHeader)
        val next = HttpHeaderLink.findByRelationType(links, "next")
        val fromId = next?.uri?.getQueryParameter("max_id")
        adapter.addItems(instances)
        bottomId = fromId
        fetching = false

        if (adapter.itemCount == 0) {
            messageView.show()
            messageView.setup(
                    R.drawable.elephant_friend_empty,
                    R.string.message_empty,
                    null
            )
        } else {
            messageView.hide()
        }
    }

    private fun onFetchInstancesFailure(throwable: Throwable) {
        fetching = false
        instanceProgressBar.hide()
        Log.e(TAG, "Fetch failure", throwable)

        if (adapter.itemCount == 0) {
            messageView.show()
            if (throwable is IOException) {
                messageView.setup(R.drawable.elephant_offline, R.string.error_network) {
                    messageView.hide()
                    this.fetchInstances(null)
                }
            } else {
                messageView.setup(R.drawable.elephant_error, R.string.error_generic) {
                    messageView.hide()
                    this.fetchInstances(null)
                }
            }
        }
    }

    companion object {
        private const val TAG = "InstanceList" // logging tag
    }
}