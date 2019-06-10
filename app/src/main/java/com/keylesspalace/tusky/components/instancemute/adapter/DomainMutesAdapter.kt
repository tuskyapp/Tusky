package com.keylesspalace.tusky.components.instancemute.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.instancemute.interfaces.InstanceActionListener
import kotlinx.android.synthetic.main.item_muted_domain.view.*

class DomainMutesAdapter(private val actionListener: InstanceActionListener): RecyclerView.Adapter<DomainMutesAdapter.ViewHolder>() {
    var instances: MutableList<String> = mutableListOf()
    var bottomLoading: Boolean = false
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_muted_domain, parent, false), actionListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setupWithInstance(instances[position])
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

    fun removeItem(position: Int)
    {
        if (position >= 0 && position < instances.size) {
            instances.removeAt(position)
            notifyItemRemoved(position)
        }
    }


    class ViewHolder(rootView: View, private val actionListener: InstanceActionListener): RecyclerView.ViewHolder(rootView) {
        fun setupWithInstance(instance: String) {
            itemView.muted_domain.text = instance
            itemView.muted_domain_unmute.setOnClickListener {
                actionListener.mute(false, instance, adapterPosition)
            }
        }
    }
}