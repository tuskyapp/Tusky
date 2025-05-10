package com.keylesspalace.tusky.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.R as materialR
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.BottomsheetConfirmationBinding
import com.keylesspalace.tusky.databinding.ItemReblogOptionBinding
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.getNonNullString
import com.keylesspalace.tusky.util.getSerializableCompat
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ConfirmationBottomSheet : BottomSheetDialogFragment(R.layout.bottomsheet_confirmation) {

    @Inject
    lateinit var prefs: SharedPreferences

    private val binding by viewBinding(BottomsheetConfirmationBinding::bind)

    private var selectedOption = Status.Visibility.PUBLIC

    @SuppressLint("UseCompatTextViewDrawableApis")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val mode: Mode = requireArguments().getSerializableCompat(ARG_MODE)!!
        if (mode == Mode.REBLOG) {
            selectedOption = Status.Visibility.valueOf(prefs.getNonNullString(PrefKeys.REBLOG_PRIVACY, Status.Visibility.PUBLIC.name))

            binding.confirmTextView.setText(R.string.reblog_confirm)
            binding.confirmTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_repeat_24dp, 0, 0, 0)
            binding.confirmTextView.compoundDrawableTintList = ColorStateList.valueOf(
                MaterialColors.getColor(binding.confirmTextView, materialR.attr.colorPrimary)
            )

            binding.confirmButton.setText(R.string.action_reblog)

            binding.confirmButton.setOnClickListener {
                prefs.edit {
                    putString(PrefKeys.REBLOG_PRIVACY, selectedOption.name)
                }
                setFragmentResult(KEY_CONFIRM, bundleOf(RESULT_VISIBILITY to selectedOption.name))
                dismiss()
            }

            binding.reblogPrivacyDropdown.setAdapter(OptionsAdapter(view.context))

            binding.reblogPrivacyLayout.setStartIconDrawable(selectedOption.getIcon())
            binding.reblogPrivacyDropdown.setText(selectedOption.getName())

            binding.reblogPrivacyDropdown.setOnItemClickListener { _, _, position, _ ->
                selectedOption = reblogOptions.getOrElse(position) { Status.Visibility.PUBLIC }
                binding.reblogPrivacyLayout.setStartIconDrawable(selectedOption.getIcon())
                binding.reblogPrivacyDropdown.setText(selectedOption.getName())
            }
        } else {
            binding.confirmTextView.setText(R.string.favourite_confirm)
            binding.confirmTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_star_24dp, 0, 0, 0)
            binding.confirmTextView.compoundDrawableTintList = ColorStateList.valueOf(
                requireContext().getColor(R.color.favoriteButtonActiveColor)
            )

            binding.reblogPrivacyLayout.hide()

            binding.confirmButton.setText(R.string.action_favourite)

            binding.confirmButton.setOnClickListener {
                setFragmentResult(KEY_CONFIRM, bundleOf())
                dismiss()
            }
        }
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    inner class OptionsAdapter(context: Context) : ArrayAdapter<Status.Visibility>(context, R.layout.item_reblog_option, reblogOptions) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = getItem(position)
            val view: View = convertView ?: run {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = ItemReblogOptionBinding.inflate(layoutInflater)

                binding.reblogOptionName.setText(item.getName())
                binding.reblogOptionDescription.setText(item.getDescription())
                binding.reblogOptionIcon.setImageResource(item.getIcon())
                binding.root
            }
            if (item == selectedOption) {
                // using the same color as MaterialAutoCompleteTextView.MaterialArrayAdapter which is not public unfortunately
                val overlayColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(view, materialR.attr.colorOnSurface),
                    30
                )
                view.background = RippleDrawable(
                    ColorStateList.valueOf(overlayColor),
                    MaterialColors.getColor(view, materialR.attr.colorSecondaryContainer).toDrawable(),
                    null
                )
            } else {
                view.background = null
            }
            return view
        }

        override fun getFilter() = object : Filter() {
            override fun performFiltering(constraint: CharSequence) = FilterResults().apply { count = 3 }

            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                // noop
            }
        }
    }

    enum class Mode {
        REBLOG,
        FAVOURITE
    }

    companion object {
        private const val TAG = "ConfirmationBottomSheet"

        private const val KEY_CONFIRM = "confirm"
        private const val ARG_MODE = "mode"
        private const val RESULT_VISIBILITY = "visibility"

        private val reblogOptions = listOf(Status.Visibility.PUBLIC, Status.Visibility.UNLISTED, Status.Visibility.PRIVATE)

        fun Fragment.confirmReblog(preferences: SharedPreferences, onConfirmed: (Status.Visibility) -> Unit) {
            if (preferences.getBoolean(PrefKeys.CONFIRM_REBLOGS, true)) {
                val bottomSheet = ConfirmationBottomSheet()
                bottomSheet.arguments = bundleOf(
                    ARG_MODE to Mode.REBLOG
                )
                bottomSheet.show(childFragmentManager, TAG)
                childFragmentManager.setFragmentResultListener(KEY_CONFIRM, this) { requestKey, result ->
                    onConfirmed(Status.Visibility.valueOf(result.getString(RESULT_VISIBILITY)!!))
                }
            } else {
                onConfirmed(Status.Visibility.PUBLIC)
            }
        }

        fun Fragment.confirmFavourite(preferences: SharedPreferences, onConfirmed: () -> Unit) {
            if (preferences.getBoolean(PrefKeys.CONFIRM_FAVOURITES, false)) {
                val bottomSheet = ConfirmationBottomSheet()
                bottomSheet.arguments = bundleOf(
                    ARG_MODE to Mode.FAVOURITE
                )
                bottomSheet.show(childFragmentManager, TAG)
                childFragmentManager.setFragmentResultListener(KEY_CONFIRM, this) { _, _ ->
                    onConfirmed()
                }
            } else {
                onConfirmed()
            }
        }

        @StringRes
        private fun Status.Visibility?.getName(): Int {
            return when (this) {
                Status.Visibility.PUBLIC -> R.string.post_privacy_public
                Status.Visibility.UNLISTED -> R.string.post_privacy_unlisted
                else -> R.string.post_privacy_followers_only
            }
        }

        @StringRes
        private fun Status.Visibility?.getDescription(): Int {
            return when (this) {
                Status.Visibility.PUBLIC -> R.string.reblog_privacy_public_description
                Status.Visibility.UNLISTED -> R.string.reblog_privacy_unlisted_description
                else -> R.string.reblog_privacy_followers_only_description
            }
        }

        @DrawableRes
        private fun Status.Visibility?.getIcon(): Int {
            return when (this) {
                Status.Visibility.PUBLIC -> R.drawable.ic_public_24dp
                Status.Visibility.UNLISTED -> R.drawable.ic_lock_open_24dp
                else -> R.drawable.ic_lock_24dp
            }
        }
    }
}
