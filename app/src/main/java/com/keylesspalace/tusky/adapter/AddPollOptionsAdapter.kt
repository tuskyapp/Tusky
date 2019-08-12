package com.keylesspalace.tusky.adapter

import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.util.onTextChanged
import com.keylesspalace.tusky.util.visible

class AddPollOptionsAdapter(
        private var options: MutableList<String>,
        private val maxOptionLength: Int
): RecyclerView.Adapter<ViewHolder>() {

    val pollOptions: List<String>
        get() = options.toList()

    fun addChoice() {
        options.add("")
        notifyItemInserted(options.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val holder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_add_poll_option, parent, false))
        holder.editText.filters = arrayOf(InputFilter.LengthFilter(maxOptionLength))

        holder.editText.onTextChanged { s, _, _, _ ->
            val pos = holder.adapterPosition
            if(pos != RecyclerView.NO_POSITION) {
                options[pos] = s.toString()
            }
        }

        return holder
    }

    override fun getItemCount() = options.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.editText.setText(options[position])

        holder.textInputLayout.hint = holder.textInputLayout.context.getString(R.string.poll_new_choice_hint, position + 1)

        holder.deleteButton.visible(position > 1, View.INVISIBLE)

        holder.deleteButton.setOnClickListener {
            options.removeAt(holder.adapterPosition)
            notifyItemRemoved(holder.adapterPosition)
        }
    }


}


class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    val textInputLayout: TextInputLayout = itemView.findViewById(R.id.optionTextInputLayout)
    val editText: TextInputEditText = itemView.findViewById(R.id.optionEditText)
    val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
}