package com.colinwhite.ping.widget;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.colinwhite.ping.R;

/**
 * ClearableEditText extends the functionality of EditText to show a clear icon, which simply gets
 * rid of all the text in the field. Used for the URL field in MainActivity.
 */
public class ClearableEditText extends RelativeLayout {
    LayoutInflater mInflater = null;
    EditText mEditText;
    Button mClearButton;

    public ClearableEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initViews();
    }

    public ClearableEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initViews();

    }

    public ClearableEditText(Context context) {
        super(context);
        initViews();
    }

    void initViews() {
        mInflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mInflater.inflate(R.layout.clearable_edit_text, this, true);
        mEditText = (EditText) findViewById(R.id.clearable_edit);
        mClearButton = (Button) findViewById(R.id.clearable_button_clear);
        mClearButton.setVisibility(RelativeLayout.INVISIBLE);
        clearText();
        showHideClearButton();
    }

    void clearText() {
        mClearButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Set the EditText to blank, but don't modify the state of the keyboard (open/close
                // it).
                mEditText.setText("");
            }
        });
    }

    void showHideClearButton() {
        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Show/hide the clear button depending on if there is any text in mEditText.
                if (s.length() > 0) {
                    mClearButton.setVisibility(RelativeLayout.VISIBLE);
                } else {
                    mClearButton.setVisibility(RelativeLayout.GONE);
                }
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Do nothing. */ }
            @Override
            public void afterTextChanged(Editable s) { /* Do nothing. */ }
        });
    }

    public Editable getText() {
        Editable text = mEditText.getText();
        return text;
    }

    public void setOnEditorActionListener(TextView.OnEditorActionListener onEditorActionListener) {
        mEditText.setOnEditorActionListener(onEditorActionListener);
    }
}