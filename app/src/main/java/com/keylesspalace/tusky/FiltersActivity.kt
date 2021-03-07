package com.keylesspalace.tusky

import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.databinding.ActivityFiltersBinding
import com.keylesspalace.tusky.databinding.DialogFilterBinding
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject

class FiltersActivity: BaseActivity() {
    @Inject
    lateinit var api: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    private val binding by viewBinding(ActivityFiltersBinding::inflate)

    private lateinit var context : String
    private lateinit var filters: MutableList<Filter>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            // Back button
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        binding.addFilterButton.setOnClickListener {
            showAddFilterDialog()
        }

        title = intent?.getStringExtra(FILTERS_TITLE)
        context = intent?.getStringExtra(FILTERS_CONTEXT)!!
        loadFilters()
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
                    eventHub.dispatch(PreferenceChangedEvent(context))
                }
            })
    }

    private fun deleteFilter(itemIndex: Int) {
        val filter = filters[itemIndex]
        if (filter.context.size == 1) {
            // This is the only context for this filter; delete it
            api.deleteFilter(filters[itemIndex].id).enqueue(object: Callback<ResponseBody> {
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

    private fun createFilter(phrase: String, wholeWord: Boolean) {
        api.createFilter(phrase, listOf(context), false, wholeWord, "").enqueue(object: Callback<Filter> {
            override fun onResponse(call: Call<Filter>, response: Response<Filter>) {
                val filterResponse = response.body()
                if(response.isSuccessful && filterResponse != null) {
                    filters.add(filterResponse)
                    refreshFilterDisplay()
                    eventHub.dispatch(PreferenceChangedEvent(context))
                } else {
                    Toast.makeText(this@FiltersActivity, "Error creating filter '$phrase'", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Filter>, t: Throwable) {
                Toast.makeText(this@FiltersActivity, "Error creating filter '$phrase'", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showAddFilterDialog() {
        val binding = DialogFilterBinding.inflate(layoutInflater)
        binding.phraseWholeWord.isChecked = true
        AlertDialog.Builder(this@FiltersActivity)
                .setTitle(R.string.filter_addition_dialog_title)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok){ _, _ ->
                    createFilter(binding.phraseEditText.text.toString(), binding.phraseWholeWord.isChecked)
                }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
    }

    private fun setupEditDialogForItem(itemIndex: Int) {
        val binding = DialogFilterBinding.inflate(layoutInflater)
        val filter = filters[itemIndex]
        binding.phraseEditText.setText(filter.phrase)
        binding.phraseWholeWord.isChecked = filter.wholeWord

        AlertDialog.Builder(this@FiltersActivity)
                .setTitle(R.string.filter_edit_dialog_title)
                .setView(binding.root)
                .setPositiveButton(R.string.filter_dialog_update_button) { _, _ ->
                    val oldFilter = filters[itemIndex]
                    val newFilter = Filter(oldFilter.id, binding.phraseEditText.text.toString(), oldFilter.context,
                            oldFilter.expiresAt, oldFilter.irreversible, binding.phraseWholeWord.isChecked)
                    updateFilter(newFilter, itemIndex)
                }
                .setNegativeButton(R.string.filter_dialog_remove_button) { _, _ ->
                    deleteFilter(itemIndex)
                }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
    }

    private fun refreshFilterDisplay() {
        binding.filtersView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filters.map { filter -> filter.phrase })
        binding.filtersView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ -> setupEditDialogForItem(position) }
    }

    private fun loadFilters() {

        binding.filterMessageView.hide()
        binding.filtersView.hide()
        binding.addFilterButton.hide()
        binding.filterProgressBar.show()

        api.getFilters().enqueue(object : Callback<List<Filter>> {
            override fun onResponse(call: Call<List<Filter>>, response: Response<List<Filter>>) {
                val filterResponse = response.body()
                if(response.isSuccessful && filterResponse != null) {

                    filters = filterResponse.filter { filter -> filter.context.contains(context) }.toMutableList()
                    refreshFilterDisplay()

                    binding.filtersView.show()
                    binding.addFilterButton.show()
                    binding.filterProgressBar.hide()
                } else {
                    binding.filterProgressBar.hide()
                    binding.filterMessageView.show()
                    binding.filterMessageView.setup(R.drawable.elephant_error,
                            R.string.error_generic) { loadFilters() }
                }
            }

            override fun onFailure(call: Call<List<Filter>>, t: Throwable) {
                binding.filterProgressBar.hide()
                binding.filterMessageView.show()
                if (t is IOException) {
                    binding.filterMessageView.setup(R.drawable.elephant_offline,
                            R.string.error_network) { loadFilters() }
                } else {
                    binding.filterMessageView.setup(R.drawable.elephant_error,
                            R.string.error_generic) { loadFilters() }
                }
            }
        })
    }

    companion object {
        const val FILTERS_CONTEXT = "filters_context"
        const val FILTERS_TITLE = "filters_title"
    }
}