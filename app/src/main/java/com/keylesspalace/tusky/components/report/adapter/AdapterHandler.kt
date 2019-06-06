package com.keylesspalace.tusky.components.report.adapter

import android.view.View
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.LinkListener
import java.util.ArrayList

interface AdapterHandler: LinkListener {
    fun showMedia(v: View?, status: Status?, idx: Int)
    fun setStatusChecked(status: Status, isChecked: Boolean)
    fun isStatusChecked(id: String): Boolean
}