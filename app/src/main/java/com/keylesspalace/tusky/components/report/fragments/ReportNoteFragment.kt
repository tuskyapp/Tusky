package com.keylesspalace.tusky.components.report.fragments


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.report.ReportViewModel
import com.keylesspalace.tusky.components.report.Screen
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.util.Error
import com.keylesspalace.tusky.util.Loading
import com.keylesspalace.tusky.util.Success
import kotlinx.android.synthetic.main.fragment_report_note.*
import java.io.IOException
import javax.inject.Inject


class ReportNoteFragment : Fragment(), Injectable {

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
            viewModel.reportNote = it?.toString()
        }
        checkIsNotifyRemote.setOnCheckedChangeListener { _, isChecked ->
            viewModel.isRemoteNotify = isChecked
        }
    }

    private fun fillViews() {
        editNote.setText(viewModel.reportNote)
        checkIsNotifyRemote.visibility = if (viewModel.isRemoteAccount) View.VISIBLE else View.GONE
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
        progressBar.visibility = View.GONE

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
        progressBar.visibility = View.VISIBLE
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
