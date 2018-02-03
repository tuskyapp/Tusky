package com.keylesspalace.tusky

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.widget.TextViewCompat
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.keylesspalace.tusky.entity.MastoList
import com.keylesspalace.tusky.fragment.TimelineFragment
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.ThemeUtils
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.varunest.sparkbutton.helpers.Utils
import retrofit2.Call
import retrofit2.Response
import java.lang.ref.WeakReference

/**
 * Created by charlag on 1/4/18.
 */

interface ListsView {
    fun update(state: State)
    fun openTimeline(listId: String)
}


data class State(val lists: List<MastoList>, val isLoading: Boolean)

class ListsViewModel(private val api: MastodonApi) {

    private var _view: WeakReference<ListsView>? = null
    private val view: ListsView? get() = _view?.get()
    private var state = State(listOf(), false)

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

    private fun loadIfNeeded() {
        if (state.isLoading || !state.lists.isEmpty()) return
        updateState(state.copy(isLoading = false))

        api.getLists().enqueue(object : retrofit2.Callback<List<MastoList>> {
            override fun onResponse(call: Call<List<MastoList>>, response: Response<List<MastoList>>) {
                updateState(state.copy(lists = response.body() ?: listOf(), isLoading = false))
            }

            override fun onFailure(call: Call<List<MastoList>>, t: Throwable?) {
                updateState(state.copy(isLoading = false))
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

class ListsActivity : BaseActivity(), ListsView {

    companion object {
        @JvmStatic
        fun newIntent(context: Context): Intent {
            return Intent(context, ListsActivity::class.java)
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    private lateinit var viewModel: ListsViewModel
    private val adapter = ListsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lists)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        recyclerView = findViewById(R.id.lists_recycler)
        progressBar = findViewById(R.id.progress_bar)

        setSupportActionBar(toolbar)
        val bar = supportActionBar
        if (bar != null) {
            bar.title = getString(R.string.title_lists)
            bar.setDisplayHomeAsUpEnabled(true)
            bar.setDisplayShowHomeEnabled(true)
        }

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(
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
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

    }

    override fun openTimeline(listId: String) {
        startActivity(
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
                        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(nameTextView, icon, null, null, null)
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