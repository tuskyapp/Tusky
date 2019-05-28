package com.keylesspalace.tusky.fragment.report


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.keylesspalace.tusky.R


class ReportFirstFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_report_first, container, false)
    }

    companion object {
        @JvmStatic
        fun newInstance() = ReportFirstFragment()
    }

}
