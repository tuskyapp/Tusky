package com.keylesspalace.tusky

import android.os.Bundle
import android.text.format.DateUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.databinding.ActivityFiltersBinding
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.view.getSecondsForDurationIndex
import com.keylesspalace.tusky.view.setupEditDialogForFilter
import com.keylesspalace.tusky.view.showAddFilterDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.Date
import javax.inject.Inject

class FiltersActivity : BaseActivity() {
    @Inject
    lateinit var api: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    private val binding by viewBinding(ActivityFiltersBinding::inflate)

    private lateinit var context: String
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
            showAddFilterDialog(this)
        }

        title = intent?.getStringExtra(FILTERS_TITLE)
        context = intent?.getStringExtra(FILTERS_CONTEXT)!!
        loadFilters()
    }

    fun updateFilter(id: String, phrase: String, filterContext: List<String>, irreversible: Boolean, wholeWord: Boolean, expiresInSeconds: Int?, itemIndex: Int) {
        api.updateFilter(id, phrase, filterContext, irreversible, wholeWord, expiresInSeconds)
            .enqueue(object : Callback<Filter> {
                override fun onFailure(call: Call<Filter>, t: Throwable) {
                    Toast.makeText(this@FiltersActivity, "Error updating filter '$phrase'", Toast.LENGTH_SHORT).show()
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

    fun deleteFilter(itemIndex: Int) {
        val filter = filters[itemIndex]
        if (filter.context.size == 1) {
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
            val newFilter = Filter(
                oldFilter.id, oldFilter.phrase, oldFilter.context.filter { c -> c != context },
                oldFilter.expiresAt, oldFilter.irreversible, oldFilter.wholeWord
            )
            updateFilter(
                newFilter.id, newFilter.phrase, newFilter.context, newFilter.irreversible, newFilter.wholeWord,
                getSecondsForDurationIndex(-1, this, oldFilter.expiresAt), itemIndex
            )
        }
    }

    fun createFilter(phrase: String, wholeWord: Boolean, expiresInSeconds: Int? = null) {
        api.createFilter(phrase, listOf(context), false, wholeWord, expiresInSeconds).enqueue(object : Callback<Filter> {
            override fun onResponse(call: Call<Filter>, response: Response<Filter>) {
                val filterResponse = response.body()
                if (response.isSuccessful && filterResponse != null) {
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

    private fun refreshFilterDisplay() {
        binding.filtersView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            filters.map { filter ->
                if (filter.expiresAt == null) {
                    filter.phrase
                } else {
                    getString(
                        R.string.filter_expiration_format,
                        filter.phrase,
                        DateUtils.getRelativeTimeSpanString(
                            filter.expiresAt.time,
                            Date().time,
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE
                        )
                    )
                }
            }
        )
        binding.filtersView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ -> setupEditDialogForFilter(this, filters[position], position) }
    }

    private fun loadFilters() {

        binding.filterMessageView.hide()
        binding.filtersView.hide()
        binding.addFilterButton.hide()
        binding.filterProgressBar.show()

        lifecycleScope.launch {
            val newFilters = try {
                api.getFilters().await()
            } catch (t: Exception) {
                binding.filterProgressBar.hide()
                binding.filterMessageView.show()
                if (t is IOException) {
                    binding.filterMessageView.setup(
                        R.drawable.elephant_offline,
                        R.string.error_network
                    ) { loadFilters() }
                } else {
                    binding.filterMessageView.setup(
                        R.drawable.elephant_error,
                        R.string.error_generic
                    ) { loadFilters() }
                }
                return@launch
            }

            filters = newFilters.filter { it.context.contains(context) }.toMutableList()
            refreshFilterDisplay()

            binding.filtersView.show()
            binding.addFilterButton.show()
            binding.filterProgressBar.hide()
        }
    }

    companion object {
        const val FILTERS_CONTEXT = "filters_context"
        const val FILTERS_TITLE = "filters_title"
    }
}
