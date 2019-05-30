package com.keylesspalace.tusky.fragment.report


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders

import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.viewmodel.ReportViewModel
import kotlinx.android.synthetic.main.fragment_report_done.*
import kotlinx.android.synthetic.main.fragment_report_statuses.*
import javax.inject.Inject


class ReportDoneFragment : Fragment(), Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private lateinit var viewModel: ReportViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity(),viewModelFactory)[ReportViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_report_done, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        handleClicks()
    }

    private fun handleClicks() {
        buttonDone.setOnClickListener {
            viewModel.navigateTo(Screen.Finish)
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() = ReportDoneFragment()
    }

}
