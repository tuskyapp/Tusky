package com.keylesspalace.tusky.components.filters

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.size
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.databinding.ActivityEditFilterBinding
import com.keylesspalace.tusky.databinding.DialogFilterBinding
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterKeyword
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.viewBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.Date
import javax.inject.Inject

class EditFilterActivity : BaseActivity() {
    @Inject
    lateinit var api: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    private val binding by viewBinding(ActivityEditFilterBinding::inflate)

    private lateinit var filter: Filter
    private var originalFilter: Filter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        originalFilter = intent?.getParcelableExtra(FILTER_TO_EDIT)
        filter = originalFilter ?: Filter("", "", listOf(), null, Filter.Action.WARN.action, listOf())

        setContentView(binding.root)
        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            // Back button
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        // This has to be done in code for some reason 🤷
        binding.filterContexts.adapter = ArrayAdapter(this,
            android.R.layout.simple_list_item_multiple_choice,
            resources.getStringArray(R.array.filter_contexts)
        )

        setTitle(R.string.filter_edit_title)
        binding.actionChip.setOnClickListener { showAddKeywordDialog() }
        binding.filterSaveButton.setOnClickListener { saveChanges() }
        binding.filterContexts.setOnItemClickListener { _, _, _, _ -> validateSaveButton() }
        binding.filterTitle.doAfterTextChanged { validateSaveButton() }
        validateSaveButton()

        if (originalFilter != null) {
            loadFilter()
        }
    }

    // Populate the UI from the filter's members
    private fun loadFilter() {
        binding.filterTitle.setText(filter.title)

        val contexts = Filter.Kind.values()
        for (context in filter.context) {
            contexts.firstOrNull { it.kind == context }?.ordinal?.let { index ->
                binding.filterContexts.setItemChecked(index, true)
            }
        }
        binding.filterActionSpinner.setSelection(filter.action.ordinal - 1) // Server-side enum doesn't include None
        if (filter.expiresAt != null) {
            val durationNames = listOf(getString(R.string.duration_no_change)) + resources.getStringArray(R.array.filter_duration_names)
            binding.filterDurationSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, durationNames)
        }
        updateKeywords(filter.keywords)
    }

    private fun addKeyword(keyword: FilterKeyword) {
        updateKeywords(filter.keywords + keyword)
    }

    private fun modifyKeyword(original: FilterKeyword, updated: FilterKeyword) {
        val index = filter.keywords.indexOf(original)
        if (index >= 0) {
            val newKeywords = filter.keywords.toMutableList().apply {
                set(index, updated)
            }
            updateKeywords(newKeywords)
        }
    }

    private fun deleteKeyword(keyword: FilterKeyword) {
        updateKeywords(filter.keywords.filterNot { it == keyword })
    }

    private fun updateKeywords(newKeywords: List<FilterKeyword>) {
        newKeywords.forEachIndexed { index, filterKeyword ->
            val chip = binding.keywordChips.getChildAt(index).takeUnless {
                it.id == R.id.actionChip
            } as Chip? ?: Chip(this).apply {
                setCloseIconResource(R.drawable.ic_cancel_24dp)
                isCheckable = false
                binding.keywordChips.addView(this, binding.keywordChips.size - 1)
            }

            chip.text = if (filterKeyword.wholeWord) {
                binding.root.context.getString(
                    R.string.filter_keyword_display_format,
                    filterKeyword.keyword
                )
            } else {
                filterKeyword.keyword
            }
            chip.isCloseIconVisible = true
            chip.setOnClickListener {
                showEditKeywordDialog(newKeywords[index])
            }
            chip.setOnCloseIconClickListener {
                deleteKeyword(newKeywords[index])
            }
        }

        while (binding.keywordChips.size - 1 > newKeywords.size) {
            binding.keywordChips.removeViewAt(newKeywords.size)
        }

        filter = filter.copy(keywords = newKeywords)
        validateSaveButton()
    }

    private fun showAddKeywordDialog() {
        val binding = DialogFilterBinding.inflate(layoutInflater)
        binding.phraseWholeWord.isChecked = true
        AlertDialog.Builder(this)
            .setTitle(R.string.filter_keyword_addition_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                addKeyword(FilterKeyword("",
                    binding.phraseEditText.text.toString(),
                    binding.phraseWholeWord.isChecked,
                ))
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditKeywordDialog(keyword: FilterKeyword) {
        val binding = DialogFilterBinding.inflate(layoutInflater)
        binding.phraseEditText.setText(keyword.keyword)
        binding.phraseWholeWord.isChecked = keyword.wholeWord

        AlertDialog.Builder(this)
            .setTitle(R.string.filter_edit_keyword_title)
            .setView(binding.root)
            .setPositiveButton(R.string.filter_dialog_update_button) { _, _ ->
                modifyKeyword(keyword, keyword.copy(
                    keyword = binding.phraseEditText.text.toString(),
                    wholeWord = binding.phraseWholeWord.isChecked,
                ))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun validateSaveButton() {
        binding.filterSaveButton.isEnabled = binding.filterTitle.text.isNotBlank() &&
            filter.keywords.isNotEmpty() &&
            binding.filterContexts.checkedItemPositions.indexOfValue(true) >= 0
    }

    private fun saveChanges() {
        val checks = binding.filterContexts.checkedItemPositions
        val contexts = Filter.Kind.values().filterIndexed { index, _ ->
            checks[index]
        }.map { it.kind }
        val title = binding.filterTitle.text.trim().toString()
        val action = Filter.Action.values()[binding.filterActionSpinner.selectedItemPosition + 1].action
        val durationIndex = binding.filterDurationSpinner.selectedItemPosition

        lifecycleScope.launch {
            originalFilter?.let { originalFilter ->
                updateFilter(originalFilter, title, contexts, action, durationIndex)
            } ?: createFilter(title, contexts, action, durationIndex)
        }
    }

    private suspend fun createFilter(title: String, contexts: List<String>, action: String, durationIndex: Int) {
        val expiresInSeconds = getSecondsForDurationIndex(durationIndex, this@EditFilterActivity)
        api.createFilter(
            title = title,
            context = contexts,
            filterAction = action,
            expiresInSeconds = expiresInSeconds,
        ).fold(
            { newFilter ->
                // This is _terrible_, but the all-in-one update filter api Just Doesn't Work
                if (showErrorIfAnyFailure(filter.keywords.map { keyword ->
                        api.addFilterKeyword(filterId = newFilter.id, keyword = keyword.keyword, wholeWord = keyword.wholeWord)
                    }, "Error creating filter '${filter.title}'"
                )) {
                    finish()
                }
            },
            { throwable ->
                if (throwable is HttpException && throwable.code() == 404) {
                    // Endpoint not found, fall back to v1 api
                    if (createFilterV1(contexts, expiresInSeconds)) {
                        finish()
                    }
                } else {
                    Snackbar.make(
                        binding.root,
                        "Error creating filter '${filter.title}'",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private suspend fun updateFilter(originalFilter: Filter, title: String, contexts: List<String>, action: String, durationIndex: Int) {
        // durationIndex - 1 here because we've prepended an extra entry for "don't modify"
        val expiresInSeconds = getSecondsForDurationIndex(durationIndex - 1, this@EditFilterActivity)
        api.updateFilter(
            id = filter.id,
            title = title,
            context = contexts,
            filterAction = action,
            expiresInSeconds = expiresInSeconds,
        ).fold(
            {
                // This is _terrible_, but the all-in-one update filter api Just Doesn't Work
                val results = filter.keywords.map { keyword ->
                        if (keyword.id.isEmpty()) {
                            api.addFilterKeyword(filterId = filter.id, keyword = keyword.keyword, wholeWord = keyword.wholeWord)
                        } else {
                            api.updateFilterKeyword(keywordId = keyword.id, keyword = keyword.keyword, wholeWord = keyword.wholeWord)
                        }
                    } + originalFilter.keywords.filter { keyword ->
                        // Deleted keywords
                        filter.keywords.none { it.id == keyword.id }
                    }.map { api.deleteFilterKeyword(it.id) }

                if (showErrorIfAnyFailure(results, "Error updating filter '${filter.title}'")) {
                    finish()
                }
            },
            { throwable ->
                if (throwable is HttpException && throwable.code() == 404) {
                    // Endpoint not found, fall back to v1 api
                    if (updateFilterV1(contexts, expiresInSeconds)) {
                        finish()
                    }
                } else {
                    Snackbar.make(binding.root, "Error updating filter '${filter.title}'", Snackbar.LENGTH_SHORT).show()
                }
            }
        )
    }

    private suspend fun createFilterV1(context: List<String>, expiresInSeconds: Int?): Boolean {
        return showErrorIfAnyFailure(filter.keywords.map { keyword ->
                api.createFilterV1(keyword.keyword, context, false, keyword.wholeWord, expiresInSeconds)
            }, "Error creating filter '${filter.title}'",
        )
    }

    private suspend fun updateFilterV1(context: List<String>, expiresInSeconds: Int?): Boolean {
        val results = filter.keywords.map { keyword ->
            if (filter.id.isEmpty()) {
                api.createFilterV1(phrase = keyword.keyword,
                    context = context,
                    irreversible = false,
                    wholeWord = keyword.wholeWord,
                    expiresInSeconds = expiresInSeconds)
            } else {
                api.updateFilterV1(id = filter.id,
                    phrase = keyword.keyword,
                    context = context,
                    irreversible = false,
                    wholeWord = keyword.wholeWord,
                    expiresInSeconds = expiresInSeconds,
                )
            }
        }
        // Don't handle deleted keywords here because there's only one keyword per v1 filter anyway

        return showErrorIfAnyFailure(results, "Error updating filter '${filter.title}'")
    }

    private fun showErrorIfAnyFailure(results: List<NetworkResult<Any>>, message: String): Boolean {
        return if (results.any { it.isFailure }) {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            false
        } else {
            true
        }
    }


    companion object {
        const val FILTER_TO_EDIT = "FilterToEdit"
        const val TITLE= "EditFilterActivityTitle"

        // Mastodon *stores* the absolute date in the filter,
        // but create/edit take a number of seconds (relative to the time the operation is posted)
        fun getSecondsForDurationIndex(index: Int, context: Context?, default: Date? = null): Int? {
            return when (index) {
                -1 -> if (default == null) { default } else { ((default.time - System.currentTimeMillis()) / 1000).toInt() }
                0 -> null
                else -> context?.resources?.getIntArray(R.array.filter_duration_values)?.get(index)
            }
        }
    }

}