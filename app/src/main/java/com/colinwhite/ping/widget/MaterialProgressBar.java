package com.colinwhite.ping.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import com.colinwhite.ping.R;

/**
 * Material design progress spinner for < Android 5.0 devices.
 * https://gist.github.com/dmide/7506c7d9614eed90805d
 */
public class MaterialProgressBar extends View {

	private final CircularProgressDrawable mDrawable;

	public MaterialProgressBar(Context context) {
		this(context, null);
	}

	public MaterialProgressBar(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public MaterialProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		mDrawable = new CircularProgressDrawable(ContextCompat.getColor(context, R.color.primary));
		mDrawable.setCallback(this);
		if (getVisibility() == VISIBLE) {
			mDrawable.start();
		}
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		super.draw(canvas);
		mDrawable.draw(canvas);
	}

	@Override
	protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
		super.onVisibilityChanged(changedView, visibility);
		if (mDrawable != null) {
			if (visibility == VISIBLE) {
				mDrawable.start();
			} else {
				mDrawable.stop();
			}
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		mDrawable.setBounds(0, 0, w, h);
	}

	@Override
	protected boolean verifyDrawable(@NonNull Drawable who) {
		return who == mDrawable || super.verifyDrawable(who);
	}
}