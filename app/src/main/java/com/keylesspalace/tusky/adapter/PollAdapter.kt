package com.keylesspalace.tusky.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.TextView
import androidx.emoji.text.EmojiCompat
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.util.CustomEmojiHelper
import com.keylesspalace.tusky.util.HtmlUtils
import com.keylesspalace.tusky.util.visible
import com.keylesspalace.tusky.viewdata.PollOptionViewData
import com.keylesspalace.tusky.viewdata.calculatePercent

class PollAdapter: RecyclerView.Adapter<PollViewHolder>() {

    private var pollOptions: List<PollOptionViewData> = emptyList()
    private var voteCount: Int = 0
    private var mode = RESULT
    private var emojis: List<Emoji> = emptyList()

    fun setup(options: List<PollOptionViewData>, voteCount: Int, emojis: List<Emoji>, mode: Int) {
        this.pollOptions = options
        this.voteCount = voteCount
        this.emojis = emojis
        this.mode = mode
        notifyDataSetChanged()
    }

    fun getSelected() : List<Int> {
        return pollOptions.filter { it.selected }
                .map { pollOptions.indexOf(it) }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollViewHolder {
        return PollViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_poll, parent, false))
    }

    override fun getItemCount(): Int {
        return pollOptions.size
    }

    override fun onBindViewHolder(holder: PollViewHolder, position: Int) {

        val option = pollOptions[position]

        holder.resultTextView.visible(mode == RESULT)
        holder.radioButton.visible(mode == SINGLE)
        holder.checkBox.visible(mode == MULTIPLE)

        when(mode) {
            RESULT -> {
                val percent = calculatePercent(option.votesCount, voteCount)

                val pollOptionText = holder.resultTextView.context.getString(R.string.poll_option_format, percent, option.title)

                val emojifiedPollOptionText = CustomEmojiHelper.emojifyText(HtmlUtils.fromHtml(pollOptionText), emojis, holder.resultTextView)
                holder.resultTextView.text =  EmojiCompat.get().process(emojifiedPollOptionText)

                val level = percent * 100

                holder.resultTextView.background.level = level

            }
            SINGLE -> {
                val emojifiedPollOptionText = CustomEmojiHelper.emojifyString(option.title, emojis, holder.radioButton)
                holder.radioButton.text = EmojiCompat.get().process(emojifiedPollOptionText)
                holder.radioButton.isChecked = option.selected
                holder.radioButton.setOnClickListener {
                    pollOptions.forEachIndexed { index, pollOption ->
                        pollOption.selected = index == holder.adapterPosition
                        notifyItemChanged(index)
                    }
                }
            }
            MULTIPLE -> {
                val emojifiedPollOptionText = CustomEmojiHelper.emojifyString(option.title, emojis, holder.checkBox)
                holder.checkBox.text = EmojiCompat.get().process(emojifiedPollOptionText)
                holder.checkBox.isChecked = option.selected
                holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                    pollOptions[holder.adapterPosition].selected = isChecked
                }
            }
        }

    }

    companion object {
        const val RESULT = 0
        const val SINGLE = 1
        const val MULTIPLE = 2
    }
}



class PollViewHolder(view: View): RecyclerView.ViewHolder(view) {

    val resultTextView: TextView = view.findViewById(R.id.status_poll_option_result)
    val radioButton: RadioButton = view.findViewById(R.id.status_poll_radio_button)
    val checkBox: CheckBox = view.findViewById(R.id.status_poll_checkbox)

}