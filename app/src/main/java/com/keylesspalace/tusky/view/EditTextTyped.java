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
import android.support.v13.view.inputmethod.EditorInfoCompat;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.keylesspalace.tusky.util.Assert;

public class EditTextTyped extends AppCompatEditText {

    InputConnectionCompat.OnCommitContentListener onCommitContentListener;
    String[] mimeTypes;
    private OnPasteListener mOnPasteListener;

    public EditTextTyped(Context context) {
        super(context);
    }

    public EditTextTyped(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public void addOnPasteListener(OnPasteListener mOnPasteListener) {
        this.mOnPasteListener = mOnPasteListener;
    }

    public void setMimeTypes(String[] types,
                             InputConnectionCompat.OnCommitContentListener listener) {
        mimeTypes = types;
        onCommitContentListener = listener;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
        InputConnection connection = super.onCreateInputConnection(editorInfo);
        if (onCommitContentListener != null) {
            Assert.expect(mimeTypes != null);
            EditorInfoCompat.setContentMimeTypes(editorInfo, mimeTypes);
            return InputConnectionCompat.createWrapper(connection, editorInfo,
                    onCommitContentListener);
        } else {
            return connection;
        }
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        boolean consumed = super.onTextContextMenuItem(id);
        switch (id) {
            case android.R.id.paste:
                onPaste();
        }
        return consumed;
    }

    /**
     * Text was pasted into the EditText.
     */
    public void onPaste() {
        if (mOnPasteListener != null)
            mOnPasteListener.onPaste();
    }

    public interface OnPasteListener {
        void onPaste();
    }
}
