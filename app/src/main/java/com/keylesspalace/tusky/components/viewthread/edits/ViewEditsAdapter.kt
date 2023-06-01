package com.keylesspalace.tusky.components.viewthread.edits

import android.content.Context
import android.graphics.Typeface.DEFAULT_BOLD
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.PollAdapter
import com.keylesspalace.tusky.adapter.PollAdapter.Companion.MULTIPLE
import com.keylesspalace.tusky.adapter.PollAdapter.Companion.SINGLE
import com.keylesspalace.tusky.databinding.ItemStatusEditBinding
import com.keylesspalace.tusky.entity.Attachment.Focus
import com.keylesspalace.tusky.entity.StatusEdit
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.util.AbsoluteTimeFormatter
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.aspectRatios
import com.keylesspalace.tusky.util.decodeBlurHash
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import com.keylesspalace.tusky.util.setClickableText
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.visible
import com.keylesspalace.tusky.viewdata.toViewData
import org.xml.sax.XMLReader

class ViewEditsAdapter(
    private val edits: List<StatusEdit>,
    private val animateAvatars: Boolean,
    private val animateEmojis: Boolean,
    private val useBlurhash: Boolean,
    private val listener: LinkListener
) : RecyclerView.Adapter<BindingHolder<ItemStatusEditBinding>>() {

    private val absoluteTimeFormatter = AbsoluteTimeFormatter()

    /** Size of large text in this theme, in px */
    var largeTextSizePx: Float = 0f

    /** Size of medium text in this theme, in px */
    var mediumTextSizePx: Float = 0f

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BindingHolder<ItemStatusEditBinding> {
        val binding = ItemStatusEditBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        binding.statusEditMediaPreview.clipToOutline = true

        val typedValue = TypedValue()
        val context = binding.root.context
        val displayMetrics = context.resources.displayMetrics
        context.theme.resolveAttribute(R.attr.status_text_large, typedValue, true)
        largeTextSizePx = typedValue.getDimension(displayMetrics)
        context.theme.resolveAttribute(R.attr.status_text_medium, typedValue, true)
        mediumTextSizePx = typedValue.getDimension(displayMetrics)

        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemStatusEditBinding>, position: Int) {
        val edit = edits[position]

        val binding = holder.binding

        val context = binding.root.context

        val infoStringRes = if (position == edits.lastIndex) {
            R.string.status_created_info
        } else {
            R.string.status_edit_info
        }

        // Show the most recent version of the status using large text to make it clearer for
        // the user, and for similarity with thread view.
        val variableTextSize = if (position == edits.lastIndex) {
            mediumTextSizePx
        } else {
            largeTextSizePx
        }
        binding.statusEditContentWarningDescription.setTextSize(TypedValue.COMPLEX_UNIT_PX, variableTextSize)
        binding.statusEditContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, variableTextSize)
        binding.statusEditMediaSensitivity.setTextSize(TypedValue.COMPLEX_UNIT_PX, variableTextSize)

        val timestamp = absoluteTimeFormatter.format(edit.createdAt, false)

        binding.statusEditInfo.text = context.getString(infoStringRes, timestamp)

        if (edit.spoilerText.isEmpty()) {
            binding.statusEditContentWarningDescription.hide()
            binding.statusEditContentWarningSeparator.hide()
        } else {
            binding.statusEditContentWarningDescription.show()
            binding.statusEditContentWarningSeparator.show()
            binding.statusEditContentWarningDescription.text = edit.spoilerText.emojify(
                edit.emojis,
                binding.statusEditContentWarningDescription,
                animateEmojis
            )
        }

        val emojifiedText = edit
            .content
            .parseAsMastodonHtml(TuskyTagHandler(context))
            .emojify(edit.emojis, binding.statusEditContent, animateEmojis)

        setClickableText(binding.statusEditContent, emojifiedText, emptyList(), emptyList(), listener)

        if (edit.poll == null) {
            binding.statusEditPollOptions.hide()
            binding.statusEditPollDescription.hide()
        } else {
            binding.statusEditPollOptions.show()

            // not used for now since not reported by the api
            // https://github.com/mastodon/mastodon/issues/22571
            // binding.statusEditPollDescription.show()

            val pollAdapter = PollAdapter()
            binding.statusEditPollOptions.adapter = pollAdapter
            binding.statusEditPollOptions.layoutManager = LinearLayoutManager(context)

            pollAdapter.setup(
                options = edit.poll.options.map { it.toViewData(false) },
                voteCount = 0,
                votersCount = null,
                emojis = edit.emojis,
                mode = if (edit.poll.multiple) { // not reported by the api
                    MULTIPLE
                } else {
                    SINGLE
                },
                resultClickListener = null,
                animateEmojis = animateEmojis,
                enabled = false
            )
        }

        if (edit.mediaAttachments.isEmpty()) {
            binding.statusEditMediaPreview.hide()
            binding.statusEditMediaSensitivity.hide()
        } else {
            binding.statusEditMediaPreview.show()
            binding.statusEditMediaPreview.aspectRatios = edit.mediaAttachments.aspectRatios()

            binding.statusEditMediaPreview.forEachIndexed { index, imageView, descriptionIndicator ->

                val attachment = edit.mediaAttachments[index]
                val hasDescription = !attachment.description.isNullOrBlank()

                if (hasDescription) {
                    imageView.contentDescription = attachment.description
                } else {
                    imageView.contentDescription =
                        imageView.context.getString(R.string.action_view_media)
                }
                descriptionIndicator.visibility = if (hasDescription) View.VISIBLE else View.GONE

                val blurhash = attachment.blurhash

                val placeholder: Drawable = if (blurhash != null && useBlurhash) {
                    decodeBlurHash(context, blurhash)
                } else {
                    ColorDrawable(MaterialColors.getColor(imageView, R.attr.colorBackgroundAccent))
                }

                if (attachment.previewUrl.isNullOrEmpty()) {
                    imageView.removeFocalPoint()
                    Glide.with(imageView)
                        .load(placeholder)
                        .centerInside()
                        .into(imageView)
                } else {
                    val focus: Focus? = attachment.meta?.focus

                    if (focus != null) {
                        imageView.setFocalPoint(focus)
                        Glide.with(imageView.context)
                            .load(attachment.previewUrl)
                            .placeholder(placeholder)
                            .centerInside()
                            .addListener(imageView)
                            .into(imageView)
                    } else {
                        imageView.removeFocalPoint()
                        Glide.with(imageView)
                            .load(attachment.previewUrl)
                            .placeholder(placeholder)
                            .centerInside()
                            .into(imageView)
                    }
                }
            }
            binding.statusEditMediaSensitivity.visible(edit.sensitive)
        }
    }

    override fun getItemCount() = edits.size

    companion object {
        private const val VIEW_TYPE_EDITS_NEWEST = 0
        private const val VIEW_TYPE_EDITS = 1
    }
}

/**
 * Handle XML tags created by [ViewEditsViewModel] and create custom spans to display inserted or
 * deleted text.
 */
class TuskyTagHandler(val context: Context) : Html.TagHandler {
    /** Class to mark the start of a span of deleted text */
    class Del

    /** Class to mark the start of a span of inserted text */
    class Ins

    override fun handleTag(opening: Boolean, tag: String, output: Editable, xmlReader: XMLReader) {
        when (tag) {
            DELETED_TEXT_EL -> {
                if (opening) {
                    start(output as SpannableStringBuilder, Del())
                } else {
                    end(
                        output as SpannableStringBuilder,
                        Del::class.java,
                        DeletedTextSpan(context)
                    )
                }
            }
            INSERTED_TEXT_EL -> {
                if (opening) {
                    start(output as SpannableStringBuilder, Ins())
                } else {
                    end(
                        output as SpannableStringBuilder,
                        Ins::class.java,
                        InsertedTextSpan(context)
                    )
                }
            }
        }
    }

    /** @return the last span in [text] of type [kind], or null if that kind is not in text */
    private fun <T> getLast(text: Spanned, kind: Class<T>): Any? {
        val spans = text.getSpans(0, text.length, kind)
        return spans?.get(spans.size - 1)
    }

    /**
     * Mark the start of a span of [text] with [mark] so it can be discovered later by [end].
     */
    private fun start(text: SpannableStringBuilder, mark: Any) {
        val len = text.length
        text.setSpan(mark, len, len, Spannable.SPAN_MARK_MARK)
    }

    /**
     * Set a [span] over the [text] most from the point recently marked with [mark] to the end
     * of the text.
     */
    private fun <T> end(text: SpannableStringBuilder, mark: Class<T>, span: Any) {
        val len = text.length
        val obj = getLast(text, mark)
        val where = text.getSpanStart(obj)
        text.removeSpan(obj)
        if (where != len) {
            text.setSpan(span, where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** Span that signifies deleted text */
    class DeletedTextSpan(context: Context) : CharacterStyle() {
        private var bgColor: Int

        init {
            bgColor = context.getColor(R.color.view_edits_background_delete)
        }

        override fun updateDrawState(tp: TextPaint) {
            tp.bgColor = bgColor
            tp.isStrikeThruText = true
        }
    }

    /** Span that signifies inserted text */
    class InsertedTextSpan(context: Context) : CharacterStyle() {
        private var bgColor: Int

        init {
            bgColor = context.getColor(R.color.view_edits_background_insert)
        }

        override fun updateDrawState(tp: TextPaint) {
            tp.bgColor = bgColor
            tp.typeface = DEFAULT_BOLD
        }
    }

    companion object {
        /** XML element to represent text that has been deleted */
        // Can't be an element that Android's HTML parser recognises, otherwise the tagHandler
        // won't be called for it.
        const val DELETED_TEXT_EL = "tusky-del"

        /** XML element to represet text that has been inserted */
        // Can't be an element that Android's HTML parser recognises, otherwise the tagHandler
        // won't be called for it.
        const val INSERTED_TEXT_EL = "tusky-ins"
    }
}
