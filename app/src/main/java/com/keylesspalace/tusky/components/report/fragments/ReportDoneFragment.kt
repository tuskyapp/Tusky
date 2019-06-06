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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.report.ReportViewModel
import com.keylesspalace.tusky.components.report.Screen
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.util.Loading
import kotlinx.android.synthetic.main.fragment_report_done.*
import javax.inject.Inject


class ReportDoneFragment : Fragment(), Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private lateinit var viewModel: ReportViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory)[ReportViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_report_done, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textReported.text = getString(R.string.report_sent_success, viewModel.accountUserName)
        handleClicks()
        subscribeObservables()
    }

    private fun subscribeObservables() {
        viewModel.muteState.observe(viewLifecycleOwner, Observer {
            buttonMute.visibility = if (it !is Loading) View.VISIBLE else View.INVISIBLE
            progressMute.visibility = if (it is Loading) View.VISIBLE else View.INVISIBLE
            buttonMute.setText(when {
                it.data == true -> R.string.action_unmute
                else -> R.string.action_mute
            })
        })

        viewModel.blockState.observe(viewLifecycleOwner, Observer {
            buttonBlock.visibility = if (it !is Loading) View.VISIBLE else View.INVISIBLE
            progressBlock.visibility = if (it is Loading) View.VISIBLE else View.INVISIBLE
            buttonBlock.setText(when {
                it.data == true -> R.string.action_unblock
                else -> R.string.action_block
            })
        })

    }

    private fun handleClicks() {
        buttonDone.setOnClickListener {
            viewModel.navigateTo(Screen.Finish)
        }
        buttonBlock.setOnClickListener {
            viewModel.toggleBlock()
        }
        buttonMute.setOnClickListener {
            viewModel.toggleMute()
        }
    }

    companion object {
        fun newInstance() = ReportDoneFragment()
    }

}
