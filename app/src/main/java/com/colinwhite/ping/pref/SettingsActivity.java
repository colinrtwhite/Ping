package com.colinwhite.ping.pref;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;

import com.colinwhite.ping.MainActivity;
import com.colinwhite.ping.R;

/**
 * Loads the SettingsFragment and manages the back button.
 */
public class SettingsActivity extends ActionBarActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);

        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.pref_toolbar);
        setSupportActionBar(toolbar);

        // Show the back button.
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(R.id.pref_frame, new SettingsFragment())
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Only the back button exists in the menu so we go back to MainActivity.
        Intent goBackIntent = new Intent(this, MainActivity.class);
        startActivity(goBackIntent);
        return true;
    }
}