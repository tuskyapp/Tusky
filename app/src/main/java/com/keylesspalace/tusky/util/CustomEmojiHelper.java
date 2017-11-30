/* Copyright 2017 Andrew Dawson
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

package com.keylesspalace.tusky.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ReplacementSpan;
import android.widget.TextView;

import com.keylesspalace.tusky.entity.Status;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomEmojiHelper {

    /**
     * replaces emoji shortcodes in a text with EmojiSpans
     * @param text the text containing custom emojis
     * @param emojis a list of the custom emojis
     * @param textView a reference to the textView the emojis will be shown in
     * @return the text with the shortcodes replaced by EmojiSpans
     */
    public static Spanned emojifyText(Spanned text, List<Status.Emoji> emojis, final TextView textView) {

        if (!emojis.isEmpty()) {

            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            for (Status.Emoji emoji : emojis) {
                CharSequence pattern = new StringBuilder(":").append(emoji.getShortcode()).append(':');
                Matcher matcher = Pattern.compile(pattern.toString()).matcher(text);
                while (matcher.find()) {
                    // We keep a span as a Picasso target, because Picasso keeps weak reference to
                    // the target so an anonymous class would likely be garbage collected.
                    EmojiSpan span = new EmojiSpan(textView);
                    builder.setSpan(span, matcher.start(), matcher.end(), 0);
                    Picasso.with(textView.getContext())
                            .load(emoji.getUrl())
                            .into(span);
                }
            }

            return builder;
        }

        return text;
    }

    public static Spanned emojifyString(String string, List<Status.Emoji> emojis, final TextView textView) {
        return emojifyText(new SpannedString(string), emojis, textView);
    }


    public static class EmojiSpan extends ReplacementSpan implements Target {

        private @Nullable Drawable imageDrawable;
        private WeakReference<TextView> textViewWeakReference;

        EmojiSpan(TextView textView) {
            this.textViewWeakReference = new WeakReference<>(textView);
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                           @Nullable Paint.FontMetricsInt fm) {

            /* update FontMetricsInt or otherwise span does not get drawn when
               it covers the whole text */
            Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
            if (fm != null) {
                fm.top = metrics.top;
                fm.ascent = metrics.ascent;
                fm.descent = metrics.descent;
                fm.bottom = metrics.bottom;
            }

            return (int) (paint.getTextSize()*1.2);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, @NonNull Paint paint) {
            if (imageDrawable == null) return;
            canvas.save();

            int emojiSize = (int) (paint.getTextSize() * 1.1);
            imageDrawable.setBounds(0, 0, emojiSize, emojiSize);

            int transY = bottom - imageDrawable.getBounds().bottom;
            transY -= paint.getFontMetricsInt().descent/2;
            canvas.translate(x, transY);
            imageDrawable.draw(canvas);
            canvas.restore();
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            TextView textView = textViewWeakReference.get();
            if(textView != null) {
                imageDrawable = new BitmapDrawable(textView.getContext().getResources(), bitmap);
                textView.invalidate();
            }
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {}

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {}
    }

}
