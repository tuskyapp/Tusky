package com.keylesspalace.tusky.components.filters

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.getOrElse
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.databinding.ActivityFiltersBinding
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterV1
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class FiltersActivity : BaseActivity(), FiltersListener {
    @Inject
    lateinit var api: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    private val binding by viewBinding(ActivityFiltersBinding::inflate)

    private lateinit var filtersV1: List<FilterV1>
    private lateinit var filters: List<Filter>

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
            launchEditFilterActivity()
        }

        setTitle(R.string.pref_title_timeline_filters)
    }

    override fun onResume() {
        super.onResume()
        loadFilters()
    }

    private fun refreshFilterDisplay(filters: List<Filter>) {
        binding.filtersView.adapter = FiltersAdapter(this, this, filters)
    }

    private fun loadFilters() {

        binding.filterMessageView.hide()
        binding.filtersView.hide()
        binding.addFilterButton.hide()
        binding.filterProgressBar.show()

        lifecycleScope.launch {
            api.getFilters().fold(
                { filters ->
                    this@FiltersActivity.filters = filters
                    refreshFilterDisplay(filters)
                    binding.filtersView.show()
                    binding.addFilterButton.show()
                    binding.filterProgressBar.hide()
                },
                { throwable ->
                    if (throwable is HttpException && throwable.code() == 404) {
                        filters = listOf()
                        filtersV1 = api.getFiltersV1().getOrElse {
                            binding.filterProgressBar.hide()
                            binding.filterMessageView.show()
                            if (it is IOException) {
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

                        refreshFilterDisplay(filtersV1.map { it.toFilter() })

                        binding.filtersView.show()
                        binding.addFilterButton.show()
                        binding.filterProgressBar.hide()
                    } else {
                        binding.filterMessageView.setup(
                            R.drawable.elephant_error,
                            R.string.error_generic
                        ) { loadFilters() }
                    }
                }
            )
        }
    }

    private fun launchEditFilterActivity(filter: Filter? = null) {
        val intent = Intent(this, EditFilterActivity::class.java).apply {
            if (filter == null) {
                putExtra(EditFilterActivity.TITLE, getString(R.string.filter_addition_title))
            } else {
                putExtra(EditFilterActivity.TITLE, getString(R.string.filter_edit_title))
                putExtra(EditFilterActivity.FILTER_TO_EDIT, filter)
            }
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
    }

    override fun deleteFilter(filter: Filter) {
        if (filters.isNotEmpty()) {
            doDeleteFilter(filter)
        } else {
            deleteFilterV1(filter)
        }
    }

    private fun doDeleteFilter(filter: Filter) {
        lifecycleScope.launch {
            api.deleteFilter(filter.id).fold(
                {
                    filters = filters.filter { it.id != filter.id }
                    refreshFilterDisplay(filters)
                    for (context in filter.context) {
                        eventHub.dispatch(PreferenceChangedEvent(context))
                    }
                },
                {
                    Snackbar.make(binding.root, "Error deleting filter '${filter.title}'", Snackbar.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun deleteFilterV1(filter: Filter) {
        lifecycleScope.launch {
            api.deleteFilterV1(filter.id).fold(
                {
                    filtersV1 = filtersV1.filter { it.id != filter.id }
                    refreshFilterDisplay(filtersV1.map { it.toFilter() })
                    for (context in filter.context) {
                        eventHub.dispatch(PreferenceChangedEvent(context))
                    }
                },
                {
                    Snackbar.make(binding.root, "Error deleting filter '${filter.title}'", Snackbar.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun updateFilter(updatedFilter: Filter) {
        launchEditFilterActivity(updatedFilter)
    }
}
