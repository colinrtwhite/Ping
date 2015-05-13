package com.colinwhite.ping.pref;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.colinwhite.ping.R;

/**
 * SettingsFragment loads the preferences from preferences.xml and reduces the in-built padding
 * on the preferences ListView.
 */
public class PreferencesFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource.
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Remove the padding on the preferences fragment.
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (view != null) {
            ListView preferences = (ListView) view.findViewById(android.R.id.list);
            preferences.setPadding(0, 0, 0, 0);
        }
        return view;
    }
}