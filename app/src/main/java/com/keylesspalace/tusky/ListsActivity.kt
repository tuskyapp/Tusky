package com.keylesspalace.tusky

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.*
import androidx.recyclerview.widget.ListAdapter
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.MastoList
import com.keylesspalace.tusky.fragment.TimelineFragment
import com.keylesspalace.tusky.util.*
import com.keylesspalace.tusky.viewmodel.ListsViewModel
import com.keylesspalace.tusky.viewmodel.ListsViewModel.Event.*
import com.keylesspalace.tusky.viewmodel.ListsViewModel.LoadingState.*
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import com.uber.autodispose.autoDisposable
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_lists.*
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
        supportActionBar?.apply {
            title = getString(R.string.title_lists)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
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
            showlistNameDialog(null)
        }

        viewModel.events.observeOn(AndroidSchedulers.mainThread())
                .autoDisposable(from(this))
                .subscribe { event ->
                    @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
                    when (event) {
                        CREATE_ERROR -> showMessage(R.string.error_create_list)
                        RENAME_ERROR -> showMessage(R.string.error_rename_list)
                        DELETE_ERROR -> showMessage(R.string.error_delete_list)
                    }
                }
    }

    private fun showlistNameDialog(list: MastoList?) {
        val layout = FrameLayout(this)
        val editText = EditText(this)
        editText.setHint(R.string.hint_list_name)
        layout.addView(editText)
        val margin = Utils.dpToPx(this, 8)
        (editText.layoutParams as ViewGroup.MarginLayoutParams)
                .setMargins(margin, margin, margin, 0)

        val dialog = AlertDialog.Builder(this)
                .setView(layout)
                .setPositiveButton(
                        if (list == null) R.string.action_create_list
                        else R.string.action_rename_list) { _, _ ->
                    onPickedDialogName(editText.text, list?.id)
                }
                .setNegativeButton(android.R.string.cancel) { d, _ ->
                    d.dismiss()
                }
                .show()

        val positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE)
        editText.onTextChanged { s, _, _, _ ->
            positiveButton.isEnabled = !s.isNullOrBlank()
        }
        editText.setText(list?.title)
        editText.text?.let { editText.setSelection(it.length) }
    }


    private fun update(state: ListsViewModel.State) {
        adapter.submitList(state.lists)
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

    private fun showMessage(@StringRes messageId: Int) {
        Snackbar.make(
                listsRecycler, messageId, Snackbar.LENGTH_SHORT
        ).show()

    }

    private fun onListSelected(listId: String) {
        startActivityWithSlideInAnimation(
                ModalTimelineActivity.newIntent(this, TimelineFragment.Kind.LIST, listId))
    }

    private fun openListSettings(list: MastoList) {
        AccountsInListFragment.newInstance(list.id, list.title).show(supportFragmentManager, null)
    }

    private fun renameListDialog(list: MastoList) {
        showlistNameDialog(list)
    }

    private fun onMore(list: MastoList, view: View) {
        PopupMenu(view.context, view).apply {
            inflate(R.menu.list_actions)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.list_edit -> openListSettings(list)
                    R.id.list_rename -> renameListDialog(list)
                    R.id.list_delete -> viewModel.deleteList(list.id)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    override fun supportFragmentInjector() = dispatchingAndroidInjector

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return false
    }

    private object ListsDiffer : DiffUtil.ItemCallback<MastoList>() {
        override fun areItemsTheSame(oldItem: MastoList, newItem: MastoList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MastoList, newItem: MastoList): Boolean {
            return oldItem == newItem
        }
    }

    private inner class ListsAdapter
        : ListAdapter<MastoList, ListsAdapter.ListViewHolder>(ListsDiffer) {

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
            holder.nameTextView.text = getItem(position).title
        }

        private inner class ListViewHolder(view: View) : RecyclerView.ViewHolder(view),
                View.OnClickListener {
            val nameTextView: TextView = view.findViewById(R.id.list_name_textview)
            val moreButton: ImageButton = view.findViewById(R.id.editListButton)

            init {
                view.setOnClickListener(this)
                moreButton.setOnClickListener(this)
            }

            override fun onClick(v: View) {
                if (v == itemView) {
                    onListSelected(getItem(adapterPosition).id)
                } else {
                    onMore(getItem(adapterPosition), v)
                }
            }
        }
    }

    private fun onPickedDialogName(name: CharSequence, listId: String?) {
        if (listId == null) {
            viewModel.createNewList(name.toString())
        } else {
            viewModel.renameList(listId, name.toString())
        }
    }
}