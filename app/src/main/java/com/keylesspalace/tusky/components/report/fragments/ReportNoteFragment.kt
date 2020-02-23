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
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.report.ReportViewModel
import com.keylesspalace.tusky.components.report.Screen
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.util.*
import kotlinx.android.synthetic.main.fragment_report_note.*
import java.io.IOException
import javax.inject.Inject

class ReportNoteFragment : Fragment(), Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: ReportViewModel by viewModels { viewModelFactory }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_report_note, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fillViews()
        handleChanges()
        handleClicks()
        subscribeObservables()
    }

    private fun handleChanges() {
        editNote.doAfterTextChanged {
            viewModel.reportNote = it?.toString() ?: ""
        }
        checkIsNotifyRemote.setOnCheckedChangeListener { _, isChecked ->
            viewModel.isRemoteNotify = isChecked
        }
    }

    private fun fillViews() {
        editNote.setText(viewModel.reportNote)

        if (viewModel.isRemoteAccount){
            checkIsNotifyRemote.show()
            reportDescriptionRemoteInstance.show()
        }
        else{
            checkIsNotifyRemote.hide()
            reportDescriptionRemoteInstance.hide()
        }

        if (viewModel.isRemoteAccount)
            checkIsNotifyRemote.text = getString(R.string.report_remote_instance, viewModel.remoteServer)
        checkIsNotifyRemote.isChecked = viewModel.isRemoteNotify
    }

    private fun subscribeObservables() {
        viewModel.reportingState.observe(viewLifecycleOwner, Observer {
            when (it) {
                is Success -> viewModel.navigateTo(Screen.Done)
                is Loading -> showLoading()
                is Error -> showError(it.cause)

            }
        })
    }

    private fun showError(error: Throwable?) {
        editNote.isEnabled = true
        checkIsNotifyRemote.isEnabled = true
        buttonReport.isEnabled = true
        buttonBack.isEnabled = true
        progressBar.hide()

        Snackbar.make(buttonBack, if (error is IOException) R.string.error_network else R.string.error_generic, Snackbar.LENGTH_LONG)
                .apply {
                    setAction(R.string.action_retry) {
                        sendReport()
                    }
                }
                .show()
    }

    private fun sendReport() {
        viewModel.doReport()
    }

    private fun showLoading() {
        buttonReport.isEnabled = false
        buttonBack.isEnabled = false
        editNote.isEnabled = false
        checkIsNotifyRemote.isEnabled = false
        progressBar.show()
    }

    private fun handleClicks() {
        buttonBack.setOnClickListener {
            viewModel.navigateTo(Screen.Back)
        }

        buttonReport.setOnClickListener {
            sendReport()
        }
    }

    companion object {
        fun newInstance() = ReportNoteFragment()
    }

}
