package com.colinwhite.ping.pref;

import android.content.Context;
import android.graphics.Color;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/**
 * Need a separate CheckBoxPreference class to change its title text to black.
 */
public class PingCheckBoxPreference extends CheckBoxPreference {
    public PingCheckBoxPreference(Context context) {
        super(context);
    }

    public PingCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public PingCheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        TextView title = (TextView) view.findViewById(android.R.id.title);
        title.setTextColor(Color.BLACK);
    }
}