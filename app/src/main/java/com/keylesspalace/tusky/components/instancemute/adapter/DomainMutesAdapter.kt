package com.keylesspalace.tusky.components.instancemute.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.components.instancemute.interfaces.InstanceActionListener
import com.keylesspalace.tusky.databinding.ItemMutedDomainBinding
import com.keylesspalace.tusky.util.BindingHolder

class DomainMutesAdapter(
    private val actionListener: InstanceActionListener
) : RecyclerView.Adapter<BindingHolder<ItemMutedDomainBinding>>() {

    var instances: MutableList<String> = mutableListOf()
    var bottomLoading: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemMutedDomainBinding> {
        val binding = ItemMutedDomainBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemMutedDomainBinding>, position: Int) {
        val instance = instances[position]

        holder.binding.mutedDomain.text = instance
        holder.binding.mutedDomainUnmute.setOnClickListener {
            actionListener.mute(false, instance, holder.bindingAdapterPosition)
        }
    }

    override fun getItemCount(): Int {
        var count = instances.size
        if (bottomLoading)
            ++count
        return count
    }

    fun addItems(newInstances: List<String>) {
        val end = instances.size
        instances.addAll(newInstances)
        notifyItemRangeInserted(end, instances.size)
    }

    fun addItem(instance: String) {
        instances.add(instance)
        notifyItemInserted(instances.size)
    }

    fun removeItem(position: Int) {
        if (position >= 0 && position < instances.size) {
            instances.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}
