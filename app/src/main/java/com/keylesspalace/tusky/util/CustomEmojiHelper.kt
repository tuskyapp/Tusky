/* Copyright 2020 Tusky Contributors
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

@file:JvmName("CustomEmojiHelper")
package com.keylesspalace.tusky.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.keylesspalace.tusky.entity.Emoji
import java.lang.ref.WeakReference
import java.util.regex.Pattern

/**
 * replaces emoji shortcodes in a text with EmojiSpans
 * @receiver the text containing custom emojis
 * @param emojis a list of the custom emojis (nullable for backward compatibility with old mastodon instances)
 * @param view a reference to the a view the emojis will be shown in (should be the TextView, but parents of the TextView are also acceptable)
 * @return the text with the shortcodes replaced by EmojiSpans
*/
fun CharSequence.emojify(emojis: List<Emoji>?, view: View, animate: Boolean): CharSequence {
    if (emojis.isNullOrEmpty())
        return this

    val builder = SpannableStringBuilder.valueOf(this)

    emojis.forEach { (shortcode, url, staticUrl) ->
        val matcher = Pattern.compile(":$shortcode:", Pattern.LITERAL)
            .matcher(this)

        while (matcher.find()) {
            val span = EmojiSpan(WeakReference(view))

            builder.setSpan(span, matcher.start(), matcher.end(), 0)
            Glide.with(view)
                .asDrawable()
                .load(if (animate) { url } else { staticUrl })
                .into(span.getTarget(animate))
        }
    }
    return builder
}

class EmojiSpan(val viewWeakReference: WeakReference<View>) : ReplacementSpan() {
    var imageDrawable: Drawable? = null

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
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
        imageDrawable?.let { drawable ->
            canvas.save()

            val emojiSize = (paint.textSize * 1.1).toInt()
            drawable.setBounds(0, 0, emojiSize, emojiSize)

            var transY = bottom - drawable.bounds.bottom
            transY -= paint.fontMetricsInt.descent / 2

            canvas.translate(x, transY.toFloat())
            drawable.draw(canvas)
            canvas.restore()
        }
    }

    fun getTarget(animate: Boolean): Target<Drawable> {
        return object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                viewWeakReference.get()?.let { view ->
                    if (animate && resource is Animatable) {
                        val callback = resource.callback

                        resource.callback = object : Drawable.Callback {
                            override fun unscheduleDrawable(p0: Drawable, p1: Runnable) {
                                callback?.unscheduleDrawable(p0, p1)
                            }
                            override fun scheduleDrawable(p0: Drawable, p1: Runnable, p2: Long) {
                                callback?.scheduleDrawable(p0, p1, p2)
                            }
                            override fun invalidateDrawable(p0: Drawable) {
                                callback?.invalidateDrawable(p0)
                                view.invalidate()
                            }
                        }
                        resource.start()
                    }

                    imageDrawable = resource
                    view.invalidate()
                }
            }

            override fun onLoadCleared(placeholder: Drawable?) {}
        }
    }
}
