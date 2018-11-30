package com.keylesspalace.tusky

import android.app.AlertDialog
import android.os.Bundle
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.hide
import kotlinx.android.synthetic.main.activity_filters.*
import kotlinx.android.synthetic.main.dialog_filter.*
import kotlinx.android.synthetic.main.toolbar_basic.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject

class FiltersActivity: BaseActivity() {
    @Inject
    lateinit var api: MastodonApi

    private lateinit var context : String
    private lateinit var filters: MutableList<Filter>

    companion object {
        const val FILTERS_CONTEXT = "filters_context"
        const val FILTERS_TITLE = "filters_title"
    }

    private fun updateFilter(filter: Filter, itemIndex: Int) {
        api.updateFilter(filter.id, filter.phrase, filter.context, filter.irreversible, filter.wholeWord, filter.expiresAt)
            .enqueue(object: Callback<Filter>{
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
                }
            })
    }

    private fun deleteFilter(itemIndex: Int) {
        val filter = filters[itemIndex]
        if (filter.context.count() == 1) {
            // This is the only context for this filter; delete it
            api.deleteFilter(filters[itemIndex].id).enqueue(object: Callback<ResponseBody> {
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Toast.makeText(this@FiltersActivity, "Error updating filter '${filters[itemIndex].phrase}'", Toast.LENGTH_SHORT).show()
                }

                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    filters.removeAt(itemIndex)
                    refreshFilterDisplay()
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
        api.createFilter(phrase, listOf(context), false, true, "").enqueue(object: Callback<Filter> {
            override fun onResponse(call: Call<Filter>, response: Response<Filter>) {
                filters.add(response.body()!!)
                refreshFilterDisplay()
            }

            override fun onFailure(call: Call<Filter>, t: Throwable) {
                Toast.makeText(this@FiltersActivity, "Error creating filter '${phrase}'", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showAddFilterDialog() {
        val dialog = AlertDialog.Builder(this@FiltersActivity)
                .setTitle(R.string.filter_addition_dialog_title)
                .setView(R.layout.dialog_filter)
                .create()
        dialog.show()

        // Need to show the dialog before referencing any elements from its view
        dialog.filterRemove.hide()
        dialog.filterOk.setText(android.R.string.ok)
        dialog.filterOk.setOnClickListener {
            createFilter(dialog.phraseEditText.text.toString())
            dialog.dismiss()
        }
        dialog.filterCancel.setOnClickListener { dialog.cancel() }
    }

    private fun setupEditDialogForItem(itemIndex: Int) {
        val dialog = AlertDialog.Builder(this@FiltersActivity)
                .setTitle(R.string.filter_edit_dialog_title)
                .setView(R.layout.dialog_filter)
                .create()
        dialog.show()

        // Need to show the dialog before referencing any elements from its view
        dialog.phraseEditText.setText(filters[itemIndex].phrase)
        dialog.filterOk.setOnClickListener {
            val oldFilter = filters[itemIndex]
            val newFilter = Filter(oldFilter.id, dialog.phraseEditText.text.toString(), oldFilter.context,
                    oldFilter.expiresAt, oldFilter.irreversible, oldFilter.wholeWord)
            updateFilter(newFilter, itemIndex)
            dialog.dismiss()
        }
        dialog.filterRemove.setOnClickListener {
            deleteFilter(itemIndex)
            dialog.dismiss()
        }
        dialog.filterCancel.setOnClickListener { dialog.cancel() }
    }

    private fun refreshFilterDisplay() {
        filtersView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filters.map { filter -> filter.phrase })
        filtersView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ -> setupEditDialogForItem(position) }
    }

    private fun loadFilters() {
        api.filters.enqueue(object : Callback<List<Filter>> {
            override fun onResponse(call: Call<List<Filter>>, response: Response<List<Filter>>) {
                filters = response.body()!!.filter { filter -> filter.context.contains(context) }.toMutableList()
                refreshFilterDisplay()
            }

            override fun onFailure(call: Call<List<Filter>>, t: Throwable) {
                // Anything?
            }
        })
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