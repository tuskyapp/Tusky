package com.keylesspalace.tusky.components.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemFilteredNotificationsInfoBinding
import com.keylesspalace.tusky.usecase.NotificationPolicyState
import com.keylesspalace.tusky.util.BindingHolder
import java.text.NumberFormat

class NotificationPolicySummaryAdapter(
    private val onOpenDetails: () -> Unit
) : RecyclerView.Adapter<BindingHolder<ItemFilteredNotificationsInfoBinding>>() {

    private var state: NotificationPolicyState = NotificationPolicyState.Loading

    fun updateState(newState: NotificationPolicyState) {
        val oldShowInfo = state.shouldShowInfo()
        val newShowInfo = newState.shouldShowInfo()
        state = newState
        if (oldShowInfo && !newShowInfo) {
            notifyItemRemoved(0)
        } else if (!oldShowInfo && newShowInfo) {
            notifyItemInserted(0)
        } else if (oldShowInfo && newShowInfo) {
            notifyItemChanged(0)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemFilteredNotificationsInfoBinding> {
        val binding = ItemFilteredNotificationsInfoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        binding.root.setOnClickListener {
            onOpenDetails()
        }
        return BindingHolder(binding)
    }

    override fun getItemCount() = if (state.shouldShowInfo()) 0 else 0

    override fun onBindViewHolder(holder: BindingHolder<ItemFilteredNotificationsInfoBinding>, position: Int) {
        val policySummary = (state as? NotificationPolicyState.Loaded)?.policy?.summary
        if (policySummary != null) {
            val binding = holder.binding
            val context = holder.binding.root.context
            binding.notificationPolicySummaryDescription.text = context.getString(R.string.notifications_from_people_you_may_know, policySummary.pendingRequestsCount)
            binding.notificationPolicySummaryBadge.text = NumberFormat.getInstance().format(policySummary.pendingNotificationsCount)
        }
    }

    private fun NotificationPolicyState.shouldShowInfo(): Boolean {
        return this is NotificationPolicyState.Loaded && this.policy.summary.pendingNotificationsCount > 0
    }
}
