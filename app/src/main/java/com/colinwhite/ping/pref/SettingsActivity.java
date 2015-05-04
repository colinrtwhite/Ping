package com.colinwhite.ping.pref;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.Toast;

import com.colinwhite.ping.R;

/**
 * Loads the SettingsFragment and manages the back button.
 */
public class SettingsActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);

        // Set up the Toolbar.
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        try {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        } catch (NullPointerException e) {
            Toast.makeText(this, getString(R.string.error), Toast.LENGTH_LONG).show();
            finish();
        }

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(R.id.pref_frame, new SettingsFragment())
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Only the back button exists in the menu so we go back to MainActivity.
        onBackPressed();
        return true;
    }
}