package com.keylesspalace.tusky.util

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import androidx.core.text.parseAsHtml
import com.keylesspalace.tusky.viewdata.StatusViewData

fun String.toSpanned(): Spanned {
    return this.replace("<br> ", "<br>&nbsp;")
    .replace("<br /> ", "<br />&nbsp;")
        .replace("<br/> ", "<br/>&nbsp;")
        .replace("  ", "&nbsp;&nbsp;")
        .parseAsHtml()
        /* Html.fromHtml returns trailing whitespace if the html ends in a </p> tag, which
         * most status contents do, so it should be trimmed. */
        .trimTrailingWhitespace()
}


fun replaceCrashingCharacters(content: Spanned): Spanned {
    return replaceCrashingCharacters(content as CharSequence) as Spanned
}

fun replaceCrashingCharacters(content: CharSequence): CharSequence? {
    var replacing = false
    var builder: SpannableStringBuilder? = null
    val length = content.length
    for (index in 0 until length) {
        val character = content[index]

        // If there are more than one or two, switch to a map
        if (character == SOFT_HYPHEN) {
            if (!replacing) {
                replacing = true
                builder = SpannableStringBuilder(content, 0, index)
            }
            builder!!.append(ASCII_HYPHEN)
        } else if (replacing) {
            builder!!.append(character)
        }
    }
    return if (replacing) builder else content
}

private const val SOFT_HYPHEN = '\u00ad'
private const val ASCII_HYPHEN = '-'