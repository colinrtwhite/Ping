package com.colinwhite.ping.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.widget.EditText;

/**
 * ClearableEditText extends the functionality of EditText to show a clear icon, which simply gets
 * rid of all the text in the field. Used for the URL field in MainActivity.
 *
 * Source: https://github.com/yanchenko/droidparts/blob/master/droidparts/src/org/droidparts/widget/ClearableEditText.java
 * (with some small tweaks)
 */
public class ClearableEditText extends EditText implements OnTouchListener,
		OnFocusChangeListener, TextWatcherAdapter.TextWatcherListener {

	private Drawable clearIcon;
	private Listener listener;
	private OnTouchListener l;
	private OnFocusChangeListener f;

	public ClearableEditText(Context context) {
		super(context);
		init();
	}

	public ClearableEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public ClearableEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	private static boolean isNotEmpty(CharSequence str) {
		return str != null && str.length() > 0;
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	@Override
	public void setOnTouchListener(OnTouchListener l) {
		this.l = l;
	}

	@Override
	public void setOnFocusChangeListener(OnFocusChangeListener f) {
		this.f = f;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if (getCompoundDrawables()[2] != null) {
			if (event.getX() > (getWidth() - getPaddingEnd() - clearIcon.getIntrinsicWidth())) {
				if (event.getAction() == MotionEvent.ACTION_UP) {
					setText("");
					if (listener != null) {
						listener.didClearText();
					}
				}
				return true;
			}
		}
		return l != null && l.onTouch(v, event);
	}

	@Override
	public void onFocusChange(View v, boolean hasFocus) {
		if (hasFocus) {
			setClearIconVisible(isNotEmpty(getText()));
		} else {
			setClearIconVisible(false);
		}
		if (f != null) {
			f.onFocusChange(v, hasFocus);
		}
	}

	@Override
	public void onTextChanged(EditText view, String text) {
		if (isFocused()) {
			setClearIconVisible(isNotEmpty(text));
		}
	}

	private void init() {
		clearIcon = getCompoundDrawables()[2];
		if (clearIcon == null) {
			clearIcon = ContextCompat.getDrawable(getContext(), android.R.drawable.presence_offline);
		}
		clearIcon.setBounds(0, 0, clearIcon.getIntrinsicWidth(), clearIcon.getIntrinsicHeight());
		setClearIconVisible(false);
		super.setOnTouchListener(this);
		super.setOnFocusChangeListener(this);
		addTextChangedListener(new TextWatcherAdapter(this, this));
	}

	private void setClearIconVisible(boolean visible) {
		boolean wasVisible = (getCompoundDrawables()[2] != null);
		if (visible != wasVisible) {
			Drawable x = visible ? clearIcon : null;
			setCompoundDrawables(getCompoundDrawables()[0],
					getCompoundDrawables()[1], x, getCompoundDrawables()[3]);
		}
	}

	public interface Listener {
		void didClearText();
	}
}