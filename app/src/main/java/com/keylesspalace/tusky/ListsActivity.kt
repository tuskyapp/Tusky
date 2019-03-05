package com.keylesspalace.tusky

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.MastoList
import com.keylesspalace.tusky.fragment.TimelineFragment
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.visible
import com.keylesspalace.tusky.viewmodel.ListsViewModel
import com.keylesspalace.tusky.viewmodel.ListsViewModel.LoadingState.*
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import com.uber.autodispose.autoDisposable
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_lists.*
import kotlinx.android.synthetic.main.dialog_new_list.*
import kotlinx.android.synthetic.main.toolbar_basic.*
import javax.inject.Inject

/**
 * Created by charlag on 1/4/18.
 */

class ListsActivity : BaseActivity(), Injectable, HasSupportFragmentInjector {

    companion object {
        @JvmStatic
        fun newIntent(context: Context): Intent {
            return Intent(context, ListsActivity::class.java)
        }
    }

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Fragment>

    private lateinit var viewModel: ListsViewModel
    private val adapter = ListsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lists)


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

        viewModel = viewModelFactory.create(ListsViewModel::class.java)
        viewModel.state
                .observeOn(AndroidSchedulers.mainThread())
                .autoDisposable(from(this))
                .subscribe(this::update)
        viewModel.retryLoading()

        addListButton.setOnClickListener {
            NewListDialogFragment().show(supportFragmentManager, null)
        }
    }


    private fun update(state: ListsViewModel.State) {
        adapter.update(state.lists)
        progressBar.visible(state.loadingState == LOADING)
        when (state.loadingState) {
            INITIAL, LOADING -> messageView.hide()
            ERROR_NETWORK -> {
                messageView.show()
                messageView.setup(R.drawable.elephant_offline, R.string.error_network) {
                    viewModel.retryLoading()
                }
            }
            ERROR_OTHER -> {
                messageView.show()
                messageView.setup(R.drawable.elephant_error, R.string.error_generic) {
                    viewModel.retryLoading()
                }
            }
            LOADED ->
                if (state.lists.isEmpty()) {
                    messageView.show()
                    messageView.setup(R.drawable.elephant_friend_empty, R.string.message_empty,
                            null)
                } else {
                    messageView.hide()
                }
        }
    }

    private fun openTimeline(listId: String) {
        startActivityWithSlideInAnimation(
                ModalTimelineActivity.newIntent(this, TimelineFragment.Kind.LIST, listId))
    }

    private fun openListSettings(list: MastoList) {
        AccountsInListFragment.newInstance(list.id, list.title).show(supportFragmentManager, null)
    }

    override fun supportFragmentInjector() = dispatchingAndroidInjector

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
            val editButton: ImageButton = view.findViewById(R.id.editListButton)

            init {
                view.setOnClickListener(this)
                editButton.setOnClickListener(this)
            }

            override fun onClick(v: View?) {
                if (v == nameTextView) {
                    openTimeline(items[adapterPosition].id)
                } else {
                    openListSettings(items[adapterPosition])
                }
            }
        }
    }

    fun onPickedDialogName(name: CharSequence) {
        viewModel.createNewList(name.toString())
    }
}

class NewListDialogFragment : DialogFragment() {
    companion object {
        fun newInstance(name: String?): NewListDialogFragment {
            val args = Bundle()
            args.putString("name", name)
            return NewListDialogFragment().also { it.arguments = args }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_new_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listNameEditText.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                createButton.isEnabled = !s.isNullOrBlank()
            }

            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
        })
        listNameEditText.setText(arguments?.getString("name"))

        createButton.setOnClickListener {
            (activity as ListsActivity).onPickedDialogName(listNameEditText.text)
            dismiss()
        }
        cancelbutton.setOnClickListener { dismiss() }
    }
}