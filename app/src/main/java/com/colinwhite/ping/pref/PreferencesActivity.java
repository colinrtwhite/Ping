package com.colinwhite.ping.pref;

import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.ListView;

import com.colinwhite.ping.R;

/**
 * Loads the SettingsFragment and manages the back button.
 */
public class PreferencesActivity extends AppCompatPreferenceActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_preferences);

		setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		// Style preference ListView appropriately on pre-Lollipop.
		ListView preferences = (ListView) findViewById(android.R.id.list);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			preferences.setSelector(R.drawable.ripple_accent);
			preferences.setDivider(new ColorDrawable(ContextCompat.getColor(this, android.R.color.darker_gray)));
			preferences.setDividerHeight(1);
		}

		// Remove the padding around the preferences list items.
		int padding = preferences.getPaddingStart();
		preferences.setPadding(0, 0, 0, 0);
		for (int i = 0; i < preferences.getChildCount(); i++) {
			preferences.getChildAt(i).setPadding(padding, 0, padding, 0);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Only the back button exists in the menu so we go back to MainActivity.
		onBackPressed();
		return true;
	}

	/**
	 * SettingsFragment loads the preferences from preferences.xml and reduces the in-built padding
	 * on the preferences ListView.
	 */
	public static class PreferencesFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// Load the preferences from an XML resource.
			addPreferencesFromResource(R.xml.preferences);
		}
	}
}