package com.colinwhite.ping.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import com.colinwhite.ping.R;

/**
 * ClearableEditText extends the functionality of EditText to show a clear icon, which simply gets
 * rid of all the text in the field. Used for the URL field in MainActivity.
 */
public class ClearableEditText extends EditText implements View.OnTouchListener,
        View.OnFocusChangeListener, TextWatcherAdapter.TextWatcherListener {
    private Drawable mIcon;
    private OnTouchListener mOnTouchListener;

    public ClearableEditText(Context context) {
        super(context);
        initialiseClearButton();
    }

    public ClearableEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialiseClearButton();
    }

    public ClearableEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialiseClearButton();
    }

    @Override
    public void setOnTouchListener(OnTouchListener listener) {
        this.mOnTouchListener = listener;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (getCompoundDrawables()[2] != null) {
            boolean tappedX = event.getX() > (getWidth() - getLineHeight());
            if (tappedX) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    setText("");
                }
                return true;
            }
        }
        if (mOnTouchListener != null) {
            return mOnTouchListener.onTouch(v, event);
        }
        return false;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) { /* Do nothing */ }

    @Override
    public void onTextChanged(EditText view, String text) {
        setClearIconVisible(!(text == null || text.length() == 0));
    }

    private void initialiseClearButton() {
        mIcon = getResources().getDrawable(R.drawable.ic_content_clear);
        mIcon.setBounds(0, 0, getLineHeight(), getLineHeight());
        setClearIconVisible(false);
        super.setOnTouchListener(this);
        super.setOnFocusChangeListener(this);
        addTextChangedListener(new TextWatcherAdapter(this, this));
    }

    protected void setClearIconVisible(boolean visible) {
        boolean wasVisible = (getCompoundDrawables()[2] != null);
        if (visible != wasVisible) {
            Drawable x = visible ? mIcon : null;
            setCompoundDrawables(getCompoundDrawables()[0], getCompoundDrawables()[1], x,
                    getCompoundDrawables()[3]);
        }
    }
}