package com.keylesspalace.tusky.components.report.adapter

import android.view.View
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.LinkListener
import java.util.ArrayList

interface AdapterClickHandler: LinkListener {
    fun showMedia(v: View?, status: Status?, idx: Int)
    fun checkedChanged(status: Status, isChecked: Boolean)
}