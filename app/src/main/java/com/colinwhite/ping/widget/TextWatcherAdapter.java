package com.colinwhite.ping.widget;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

/**
 * TextWatcherAdapter is used to add an onTextChanged listener to an EditText.
 */
class TextWatcherAdapter implements TextWatcher {
    private final EditText mView;
    private final TextWatcherListener mListener;

    public interface TextWatcherListener {
        void onTextChanged(EditText view, String text);
    }

    public TextWatcherAdapter(EditText editText, TextWatcherListener listener) {
        this.mView = editText;
        this.mListener = listener;
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        mListener.onTextChanged(mView, s.toString());
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Do nothing. */ }

    @Override
    public void afterTextChanged(Editable s) { /* Do nothing. */ }
}