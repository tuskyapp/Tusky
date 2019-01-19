package com.keylesspalace.tusky

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.LoadingState.*
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.MastoList
import com.keylesspalace.tusky.fragment.TimelineFragment
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.ThemeUtils
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import kotlinx.android.synthetic.main.activity_lists.*
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * Created by charlag on 1/4/18.
 */

interface ListsView {
    fun update(state: State)
    fun openTimeline(listId: String)
}


enum class LoadingState {
    INITIAL, LOADING, LOADED, ERROR_NETWORK, ERROR_OTHER
}

data class State(val lists: List<MastoList>, val loadingState: LoadingState)

class ListsViewModel(private val api: MastodonApi) {

    private var _view: WeakReference<ListsView>? = null
    private val view: ListsView? get() = _view?.get()
    private var state = State(listOf(), INITIAL)

    fun attach(view: ListsView) {
        this._view = WeakReference(view)
        updateView()
        loadIfNeeded()
    }

    fun detach() {
        this._view = null
    }

    fun didSelectItem(id: String) {
        view?.openTimeline(id)
    }

    fun retryLoading() {
        loadIfNeeded()
    }

    private fun loadIfNeeded() {
        if (state.loadingState == LOADING || !state.lists.isEmpty()) return
        updateState(state.copy(loadingState = LOADING))

        api.getLists().enqueue(object : retrofit2.Callback<List<MastoList>> {
            override fun onResponse(call: Call<List<MastoList>>, response: Response<List<MastoList>>) {
                updateState(state.copy(lists = response.body() ?: listOf(), loadingState = LOADED))
            }

            override fun onFailure(call: Call<List<MastoList>>, err: Throwable?) {
                updateState(state.copy(
                        loadingState = if (err is IOException) ERROR_NETWORK else ERROR_OTHER
                ))
            }
        })
    }

    private fun updateState(state: State) {
        this.state = state
        view?.update(state)
    }

    private fun updateView() {
        view?.update(state)
    }
}

class ListsActivity : BaseActivity(), ListsView, Injectable {

    companion object {
        @JvmStatic
        fun newIntent(context: Context): Intent {
            return Intent(context, ListsActivity::class.java)
        }
    }

    @Inject
    lateinit var mastodonApi: MastodonApi

    private lateinit var viewModel: ListsViewModel
    private val adapter = ListsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lists)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        setSupportActionBar(toolbar)
        val bar = supportActionBar
        if (bar != null) {
            bar.title = getString(R.string.title_lists)
            bar.setDisplayHomeAsUpEnabled(true)
            bar.setDisplayShowHomeEnabled(true)
        }

        listsRecycler.adapter = adapter
        listsRecycler.layoutManager = LinearLayoutManager(this)
        listsRecycler.addItemDecoration(
                DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        viewModel = lastNonConfigurationInstance as? ListsViewModel ?: ListsViewModel(mastodonApi)
        viewModel.attach(this)
    }

    override fun onDestroy() {
        viewModel.detach()
        super.onDestroy()
    }

    override fun onRetainCustomNonConfigurationInstance(): Any {
        return viewModel
    }


    override fun update(state: State) {
        adapter.update(state.lists)
        progressBar.visibility = if (state.loadingState == LOADING) View.VISIBLE else View.GONE
        statusImage.setImageResource(when (state.loadingState) {
            INITIAL, LOADING -> android.R.color.transparent
            ERROR_NETWORK -> R.drawable.elephant_offline
            ERROR_OTHER -> R.drawable.elephant_error
            LOADED ->
                if (state.lists.isEmpty()) {
                    R.drawable.elephant_friend_empty
                } else {
                    android.R.color.transparent
                }
        })
        when (state.loadingState) {
            ERROR_OTHER -> showMessage(R.string.error_generic) { viewModel.retryLoading() }
            ERROR_NETWORK -> {
                showMessage(R.string.error_network) { viewModel.retryLoading() }
            }
            LOADED -> {
                if (state.lists.isEmpty()) {
                    showMessage(R.string.message_empty, null)
                }
            }
            else -> {
            }
        }
    }

    fun showMessage(@StringRes message: Int, retry: ((v: View) -> Unit)?) {
        Snackbar.make(window.decorView.rootView, message, Snackbar.LENGTH_INDEFINITE)
                .apply {
                    retry?.let { setAction(R.string.action_retry, retry) }
                }
                .show()
    }

    override fun openTimeline(listId: String) {
        startActivityWithSlideInAnimation(
                ModalTimelineActivity.newIntent(this, TimelineFragment.Kind.LIST, listId))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return false
    }

    private inner class ListsAdapter : RecyclerView.Adapter<ListsAdapter.ListViewHolder>() {

        private val items = mutableListOf<MastoList>()

        fun update(list: List<MastoList>) {
            this.items.clear()
            this.items.addAll(list)
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
            return LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false)
                    .let(this::ListViewHolder)
                    .apply {
                        val context = nameTextView.context
                        val icon = IconicsDrawable(context, GoogleMaterial.Icon.gmd_list).sizeDp(20)

                        ThemeUtils.setDrawableTint(context, icon, android.R.attr.textColorTertiary)
                        nameTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                    }
        }

        override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
            holder.nameTextView.text = items[position].title
        }

        private inner class ListViewHolder(view: View) : RecyclerView.ViewHolder(view),
                View.OnClickListener {
            val nameTextView: TextView = view.findViewById(R.id.list_name_textview)

            init {
                view.setOnClickListener(this)
            }

            override fun onClick(v: View?) {
                viewModel.didSelectItem(items[adapterPosition].id)
            }
        }
    }
}