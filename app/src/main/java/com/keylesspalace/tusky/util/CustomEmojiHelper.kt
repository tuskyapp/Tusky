/* Copyright 2017 Andrew Dawson
 * Copyright 2020 a1batross
 * 
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.style.ReplacementSpan
import android.view.View

import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.keylesspalace.tusky.entity.Emoji

import java.lang.ref.WeakReference
import java.util.regex.Pattern
import androidx.preference.PreferenceManager

/**
 * replaces emoji shortcodes in a text with EmojiSpans
 * @param text the text containing custom emojis
 * @param emojis a list of the custom emojis (nullable for backward compatibility with old mastodon instances)
 * @param view a reference to the a view the emojis will be shown in (should be the TextView, but parents of the TextView are also acceptable)
 * @return the text with the shortcodes replaced by EmojiSpans
*/
fun emojifyText(text: Spanned, emojis: List<Emoji>?, view: View) : Spanned {
    if (emojis != null && emojis.isNotEmpty()) {
        val builder = SpannableStringBuilder(text)
        for (emoji in emojis) {
            val pattern = StringBuilder(":").append(emoji.shortcode).append(":")
            val matcher = Pattern.compile(pattern.toString(), Pattern.LITERAL)
                .matcher(text)
            while(matcher.find()) {
                val span = EmojiSpan(WeakReference<View>(view))
                builder.setSpan(span, matcher.start(), matcher.end(), 0);
                Glide.with(view)
                    .asBitmap()
                    .load(emoji.url)
                    .into(span.getTarget())
            }
        }
        
        return builder
    }
    
    return text
}

fun emojifyString(string: String, emojis: List<Emoji>?, view: View) : Spanned {
    return emojifyText(SpannedString(string), emojis, view)
}

public class EmojiSpan(val viewWeakReference: WeakReference<View>) : ReplacementSpan() {
    var imageDrawable: Drawable? = null
    
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?) : Int {
        if (fm != null) {
            /* update FontMetricsInt or otherwise span does not get drawn when
             * it covers the whole text */
            val metrics = paint.fontMetricsInt
            fm.top = metrics.top
            fm.ascent = metrics.ascent
            fm.descent = metrics.descent
            fm.bottom = metrics.bottom
        }
        
        return (paint.textSize * 1.2).toInt()
    }
    
    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        if (imageDrawable == null)
            return
        
        canvas.save()
        
        val emojiSize = (paint.textSize * 1.1).toInt()
        imageDrawable!!.setBounds(0, 0, emojiSize, emojiSize)
        
        var transY = bottom - imageDrawable!!.bounds.bottom
        transY -= paint.fontMetricsInt.descent / 2;
        
        canvas.translate(x, transY.toFloat())
        imageDrawable!!.draw(canvas)
        canvas.restore()
    }
    
    fun getTarget(): Target<Bitmap> {
        return object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                val view = viewWeakReference.get()
                if (view != null) {
                    imageDrawable = BitmapDrawable(view.context.resources, resource)
                    view.invalidate()
                }
            }
            
            override fun onLoadCleared(placeholder: Drawable?) {}
        }
    }
}
