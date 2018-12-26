

package com.keylesspalace.tusky.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.TabData
import kotlinx.android.synthetic.main.item_tab_preference.view.*

class TabAdapter(val items: List<TabData>) : RecyclerView.Adapter<TabAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab_preference, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.itemView.textView.setText(items[position].text)
        holder.itemView.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(items[position].icon, 0, 0, 0)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
