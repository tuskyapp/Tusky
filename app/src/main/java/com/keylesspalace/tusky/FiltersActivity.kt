package com.keylesspalace.tusky

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.network.MastodonApi
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import com.uber.autodispose.autoDisposable
import kotlinx.android.synthetic.main.activity_filters.*
import kotlinx.android.synthetic.main.dialog_filter.*
import kotlinx.android.synthetic.main.toolbar_basic.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject

class FiltersActivity : BaseActivity() {
    @Inject
    lateinit var api: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    private lateinit var context: String
    private lateinit var filters: MutableList<Filter>
    private lateinit var dialog: AlertDialog

    companion object {
        const val FILTERS_CONTEXT = "filters_context"
        const val FILTERS_TITLE = "filters_title"
    }

    private fun updateFilter(filter: Filter, itemIndex: Int) {
        api.updateFilter(filter.id, filter.phrase, filter.context, filter.irreversible, filter.wholeWord, filter.expiresAt)
                .enqueue(object : Callback<Filter> {
                    override fun onFailure(call: Call<Filter>, t: Throwable) {
                        Toast.makeText(this@FiltersActivity, "Error updating filter '${filter.phrase}'", Toast.LENGTH_SHORT).show()
                    }

                    override fun onResponse(call: Call<Filter>, response: Response<Filter>) {
                        val updatedFilter = response.body()!!
                        if (updatedFilter.context.contains(context)) {
                            filters[itemIndex] = updatedFilter
                        } else {
                            filters.removeAt(itemIndex)
                        }
                        refreshFilterDisplay()
                        eventHub.dispatch(PreferenceChangedEvent(context))
                    }
                })
    }

    private fun deleteFilter(itemIndex: Int) {
        val filter = filters[itemIndex]
        if (filter.context.count() == 1) {
            // This is the only context for this filter; delete it
            api.deleteFilter(filters[itemIndex].id).enqueue(object : Callback<ResponseBody> {
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Toast.makeText(this@FiltersActivity, "Error updating filter '${filters[itemIndex].phrase}'", Toast.LENGTH_SHORT).show()
                }

                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    filters.removeAt(itemIndex)
                    refreshFilterDisplay()
                    eventHub.dispatch(PreferenceChangedEvent(context))
                }
            })
        } else {
            // Keep the filter, but remove it from this context
            val oldFilter = filters[itemIndex]
            val newFilter = Filter(oldFilter.id, oldFilter.phrase, oldFilter.context.filter { c -> c != context },
                    oldFilter.expiresAt, oldFilter.irreversible, oldFilter.wholeWord)
            updateFilter(newFilter, itemIndex)
        }
    }

    private fun createFilter(phrase: String) {
        api.createFilter(phrase, listOf(context), false, true, "").enqueue(object : Callback<Filter> {
            override fun onResponse(call: Call<Filter>, response: Response<Filter>) {
                filters.add(response.body()!!)
                refreshFilterDisplay()
                eventHub.dispatch(PreferenceChangedEvent(context))
            }

            override fun onFailure(call: Call<Filter>, t: Throwable) {
                Toast.makeText(this@FiltersActivity, "Error creating filter '${phrase}'", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showAddFilterDialog() {
        dialog = AlertDialog.Builder(this@FiltersActivity)
                .setTitle(R.string.filter_addition_dialog_title)
                .setView(R.layout.dialog_filter)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    createFilter(dialog.phraseEditText.text.toString())
                }
                .setNeutralButton(android.R.string.cancel, null)
                .create()
        dialog.show()
    }

    private fun setupEditDialogForItem(itemIndex: Int) {
        dialog = AlertDialog.Builder(this@FiltersActivity)
                .setTitle(R.string.filter_edit_dialog_title)
                .setView(R.layout.dialog_filter)
                .setPositiveButton(R.string.filter_dialog_update_button) { _, _ ->
                    val oldFilter = filters[itemIndex]
                    val newFilter = Filter(oldFilter.id, dialog.phraseEditText.text.toString(), oldFilter.context,
                            oldFilter.expiresAt, oldFilter.irreversible, oldFilter.wholeWord)
                    updateFilter(newFilter, itemIndex)
                }
                .setNegativeButton(R.string.filter_dialog_remove_button) { _, _ ->
                    deleteFilter(itemIndex)
                }
                .setNeutralButton(android.R.string.cancel, null)
                .create()
        dialog.show()

        // Need to show the dialog before referencing any elements from its view
        dialog.phraseEditText.setText(filters[itemIndex].phrase)
    }

    private fun refreshFilterDisplay() {
        filtersView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filters.map { filter -> filter.phrase })
        filtersView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ -> setupEditDialogForItem(position) }
    }

    private fun loadFilters() {
        api.filters.autoDisposable(from(this))
                .subscribe({ response ->
                    filters = response
                            .filter { filter -> filter.context.contains(context) }
                            .toMutableList()
                }, { t -> Log.w(javaClass.simpleName, "Failed to get filters", t) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_filters)
        setupToolbarBackArrow()
        filter_floating_add.setOnClickListener {
            showAddFilterDialog()
        }

        title = intent?.getStringExtra(FILTERS_TITLE)
        context = intent?.getStringExtra(FILTERS_CONTEXT)!!
        loadFilters()
    }

    private fun setupToolbarBackArrow() {
        setSupportActionBar(toolbar)
        supportActionBar?.run {
            // Back button
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    // Activate back arrow in toolbar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}