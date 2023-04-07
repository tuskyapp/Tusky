/* Copyright Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.occurrence

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ListAdapter
import com.google.android.material.color.MaterialColors
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ActivityOccurrencesBinding
import com.keylesspalace.tusky.databinding.ItemOccurrenceBinding
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.getDurationStringAllowMillis
import com.keylesspalace.tusky.util.getRelativeTimeSpanString
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.runBlocking
import java.text.DateFormat
import javax.inject.Inject

class OccurrenceActivity : BaseActivity(), Injectable, HasAndroidInjector {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    // TODO what's this?
    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    @Inject
    lateinit var occurrenceRepository: OccurrenceRepository

    @Inject
    lateinit var db: AppDatabase

//    private val viewModel: ListsViewModel by viewModels { viewModelFactory }

    private val binding by viewBinding(ActivityOccurrencesBinding::inflate)

    private val adapter = OccurrenceAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.title_occurrences)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.occurrenceList.adapter = adapter
        binding.occurrenceList.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        binding.swipeRefreshLayout.setOnRefreshListener { load() }
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)

        // It's only function here so far: show "there is nothing"
        binding.messageView.setup(
            R.drawable.elephant_friend_empty,
            R.string.message_empty,
            null
        )

        load()
    }

    private fun load() {
//        if (binding.swipeRefreshLayout.isRefreshing) {
//            return
//        }

        // TODO well...
        runBlocking {
            binding.swipeRefreshLayout.isRefreshing = true

            val occurrences = occurrenceRepository.loadAll()
            Log.i("OCA", "Found occurrences "+occurrences.size)

            adapter.submitList(occurrences)

            binding.messageView.visible(occurrences.isEmpty())
            binding.occurrenceList.visible(occurrences.isNotEmpty())

            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    override fun androidInjector() = dispatchingAndroidInjector

    companion object {
        fun newIntent(context: Context) = Intent(context, OccurrenceActivity::class.java)
    }

    private object OccurrenceDiffer : DiffUtil.ItemCallback<OccurrenceEntity>() {
        override fun areItemsTheSame(oldItem: OccurrenceEntity, newItem: OccurrenceEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: OccurrenceEntity, newItem: OccurrenceEntity): Boolean {
            return oldItem == newItem
        }
    }

    private inner class OccurrenceAdapter :
        ListAdapter<OccurrenceEntity, BindingHolder<ItemOccurrenceBinding>>(OccurrenceDiffer) {

        private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT)
        private var lastAccount: AccountEntity? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemOccurrenceBinding> {
            return BindingHolder(ItemOccurrenceBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: BindingHolder<ItemOccurrenceBinding>, position: Int) {
            val occurrence = getItem(position)

            val defaultTextColor = MaterialColors.getColor(binding.root, android.R.attr.textColorPrimary)

            holder.binding.what.text = occurrence.what
            holder.binding.what.setTextColor(
                if (occurrence.type == OccurrenceEntity.Type.CRASH) {
                    Color.RED
                } else {
                    defaultTextColor
                }
            )

            holder.binding.code.text = occurrence.code?.toString() ?: ""
            holder.binding.code.setTextColor(
                if (occurrence.code != null && occurrence.code > 0) {
                    if (occurrence.code >= 400) {
                        Color.RED
                    } else if (occurrence.code >= 300) {
                        Color.YELLOW
                    } else {
                        Color.GREEN
                    }
                } else {
                    defaultTextColor
                }
            )

            holder.binding.whenDate.text =
                getRelativeTimeSpanString(this@OccurrenceActivity.applicationContext, occurrence.startedAt.time, System.currentTimeMillis())
                //dateFormat.format(occurrence.startedAt)
            // TODO or AbsoluteTimeFormatter?

            // TODO how does one get the current locale /and/or format numbers here?
            val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                resources.configuration.locales[0]
            } else {
                resources.configuration.locale
            }

            var duration = ""
            var durationMs = 0L
            if (occurrence.finishedAt != null) {
                durationMs = occurrence.finishedAt.time - occurrence.startedAt.time
                duration = getDurationStringAllowMillis(currentLocale, durationMs)
            }
            holder.binding.duration.text = duration
            holder.binding.duration.setTextColor(
                if (durationMs >= 1000) {
                    Color.RED
                } else if (durationMs >= 400) {
                    Color.YELLOW
                } else {
                    Color.GREEN
                }
            )

            holder.binding.who.text = if (occurrence.accountId != null) {
                val account = getAccount(occurrence.accountId)
                account?.displayName ?: ""
            } else {
                ""
            }

            holder.binding.trace.visible(occurrence.callTrace.isNotEmpty())
            holder.binding.trace.text = occurrence.callTrace // TODO this could/should be normal multi-line

            // TODO cache some objects here? For example different helper objects (locale, number format, ...)
        }

        private fun getAccount(accountId: Long): AccountEntity? {
            if (lastAccount?.id == accountId) {
                return lastAccount
            }

            lastAccount = db.accountDao().get(accountId)

            return lastAccount
        }
    }
}
