package com.keylesspalace.tusky.view;

import android.support.annotation.Nullable;
import android.text.Layout;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.keylesspalace.tusky.util.CustomEmojiHelper;

/**
 * LinkMovementMethod which shows shortcode for long-clicking custom emoji.
 * Also fixes a problem when {@link android.text.method.LinkMovementMethod} does not delegate
 * clicks to the view.
 */
public final class EmojiLinkMovementMethod extends LinkMovementMethod {
    private boolean shouldShow = false;
    @Nullable
    private final View.OnClickListener clickListener;

    public EmojiLinkMovementMethod(@Nullable View.OnClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getAction();
        Log.d("EmojiLinkMovement", event.getAction() + "");
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                CustomEmojiHelper.EmojiSpan[] links = getEmojiSpans(widget, buffer, event);
                if (links.length > 0) {
                    this.shouldShow = true;
                    widget.postDelayed(() -> {
                        if (shouldShow) {
                            Toast.makeText(widget.getContext(), links[0].getShortcode(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }, 600);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
                if (this.shouldShow) {
                    CustomEmojiHelper.EmojiSpan[] emojiSpans =
                            this.getEmojiSpans(widget, buffer, event);
                    if (emojiSpans.length > 0) {
                        this.shouldShow = false;
                        return true;
                    }
                }
                break;
            // fallthrough
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_OUTSIDE:
                this.shouldShow = false;
                break;
        }
        Log.d("EmojiLinkMovement", "Doing super for event " + action);
        boolean superResult = super.onTouchEvent(widget, buffer, event);
        if (!superResult && action == MotionEvent.ACTION_UP && this.clickListener != null) {
            // if the finger was released and no span handled action, call clickHandler if present
            this.clickListener.onClick(widget);
        }
        return superResult;
    }

    private CustomEmojiHelper.EmojiSpan[] getEmojiSpans(TextView widget, Spannable buffer,
                                                        MotionEvent event) {
        // this is basically copied from LinkMovementMethod
        int x = (int) event.getX();
        int y = (int) event.getY();

        x -= widget.getTotalPaddingLeft();
        y -= widget.getTotalPaddingTop();

        x += widget.getScrollX();
        y += widget.getScrollY();

        Layout layout = widget.getLayout();
        int line = layout.getLineForVertical(y);
        int off = layout.getOffsetForHorizontal(line, x);

        return buffer.getSpans(off, off, CustomEmojiHelper.EmojiSpan.class);
    }
}
