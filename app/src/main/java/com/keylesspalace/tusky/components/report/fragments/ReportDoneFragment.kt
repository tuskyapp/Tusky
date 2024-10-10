/* Copyright 2019 Joel Pyska
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
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.components.report.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.report.ReportViewModel
import com.keylesspalace.tusky.components.report.Screen
import com.keylesspalace.tusky.databinding.FragmentReportDoneBinding
import com.keylesspalace.tusky.util.Loading
import com.keylesspalace.tusky.util.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReportDoneFragment : Fragment(R.layout.fragment_report_done) {

    private val viewModel: ReportViewModel by activityViewModels()

    private val binding by viewBinding(FragmentReportDoneBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.textReported.text = getString(R.string.report_sent_success, viewModel.accountUserName)
        handleClicks()
        subscribeObservables()
    }

    private fun subscribeObservables() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.muteState.collect {
                if (it == null) return@collect
                if (it !is Loading) {
                    binding.buttonMute.visibility = View.VISIBLE
                    binding.progressMute.visibility = View.GONE
                } else {
                    binding.buttonMute.visibility = View.INVISIBLE
                    binding.progressMute.visibility = View.VISIBLE
                }

                binding.buttonMute.setText(
                    when (it.data) {
                        true -> R.string.action_unmute
                        else -> R.string.action_mute
                    }
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.blockState.collect {
                if (it == null) return@collect
                if (it !is Loading) {
                    binding.buttonBlock.visibility = View.VISIBLE
                    binding.progressBlock.visibility = View.GONE
                } else {
                    binding.buttonBlock.visibility = View.INVISIBLE
                    binding.progressBlock.visibility = View.VISIBLE
                }
                binding.buttonBlock.setText(
                    when (it.data) {
                        true -> R.string.action_unblock
                        else -> R.string.action_block
                    }
                )
            }
        }
    }

    private fun handleClicks() {
        binding.buttonDone.setOnClickListener {
            viewModel.navigateTo(Screen.Finish)
        }
        binding.buttonBlock.setOnClickListener {
            viewModel.toggleBlock()
        }
        binding.buttonMute.setOnClickListener {
            viewModel.toggleMute()
        }
    }

    companion object {
        fun newInstance() = ReportDoneFragment()
    }
}
