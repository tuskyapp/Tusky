package com.keylesspalace.tusky.view

import android.util.Size
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import androidx.fragment.app.DialogFragment
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.util.dpToPx

open class FullScreenDialogFragment : DialogFragment() {
    /**
     * Make sure the dialog window is full screen (minus normal insets like a device status bar).
     *
     * However the size is bounded to a maximum of `max_dialog_width_dp` and `max_dialog_height_dp` from the resources.
     * So for example on tablet it does not fill the whole space.
     */
    override fun onStart() {
        super.onStart()
        dialog?.apply {
            window?.apply {
                // On smaller devices this is -1 which is MATCH_PARENT
                val maxWidthDp = resources.getInteger(R.integer.max_dialog_width_dp)
                val maxHeightDp = resources.getInteger(R.integer.max_dialog_height_dp)

                if (maxWidthDp == ViewGroup.LayoutParams.MATCH_PARENT && maxHeightDp == ViewGroup.LayoutParams.MATCH_PARENT) {
                    this.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                } else {
                    val maxAvailablePixels = getAvailableScreenPixels(this)

                    var maxWidthSpec = if (maxWidthDp == -1) maxWidthDp else maxWidthDp.dpToPx(this.context.resources)
                    if (maxWidthSpec > maxAvailablePixels.width) {
                        maxWidthSpec = maxAvailablePixels.width
                    }
                    var maxHeightSpec = if (maxHeightDp == -1) maxHeightDp else maxHeightDp.dpToPx(this.context.resources)
                    if (maxHeightSpec > maxAvailablePixels.height) {
                        maxHeightSpec = maxAvailablePixels.height
                    }

                    this.setLayout(maxWidthSpec, maxHeightSpec)
                }
            }
        }
    }

    private fun getAvailableScreenPixels(window: Window): Size {
        val windowInsets = window.windowManager.maximumWindowMetrics.windowInsets
        val insets = windowInsets.getInsets(WindowInsets.Type.captionBar() or WindowInsets.Type.systemBars() or
            WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars())
        val maxAvailableDeviceWidth = window.context.resources.displayMetrics.widthPixels - insets.left - insets.right
        val maxAvailableDeviceHeight = window.context.resources.displayMetrics.heightPixels - insets.top - insets.bottom

        return Size(maxAvailableDeviceWidth, maxAvailableDeviceHeight)
    }
}
