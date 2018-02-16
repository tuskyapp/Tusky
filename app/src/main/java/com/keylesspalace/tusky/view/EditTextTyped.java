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

package com.keylesspalace.tusky.view;

import android.content.Context;
import android.support.text.emoji.widget.EmojiEditTextHelper;
import android.support.v13.view.inputmethod.EditorInfoCompat;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v7.widget.AppCompatMultiAutoCompleteTextView;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.keylesspalace.tusky.util.Assert;

public class EditTextTyped extends AppCompatMultiAutoCompleteTextView {

    private InputConnectionCompat.OnCommitContentListener onCommitContentListener;
    private String[] mimeTypes;
    private EmojiEditTextHelper mEmojiEditTextHelper;

    public EditTextTyped(Context context) {
        super(context);
    }

    public EditTextTyped(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init();
    }
    private void init() {
        super.setKeyListener(getEmojiEditTextHelper().getKeyListener(getKeyListener()));
    }

    @Override
    public void setKeyListener(android.text.method.KeyListener keyListener) {
        super.setKeyListener(getEmojiEditTextHelper().getKeyListener(keyListener));
    }

    private EmojiEditTextHelper getEmojiEditTextHelper() {
        if (mEmojiEditTextHelper == null) {
            mEmojiEditTextHelper = new EmojiEditTextHelper(this);
        }
        return mEmojiEditTextHelper;
    }

    public void setMimeTypes(String[] types,
                             InputConnectionCompat.OnCommitContentListener listener) {
        mimeTypes = types;
        onCommitContentListener = listener;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
        InputConnection connection = super.onCreateInputConnection(editorInfo);
        connection = getEmojiEditTextHelper().onCreateInputConnection(connection, editorInfo);
        if (onCommitContentListener != null) {
            Assert.expect(mimeTypes != null);
            EditorInfoCompat.setContentMimeTypes(editorInfo, mimeTypes);
            return InputConnectionCompat.createWrapper(connection, editorInfo,
                    onCommitContentListener);
        } else {
            return connection;
        }
    }
}
