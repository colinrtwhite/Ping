package com.colinwhite.ping;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.MenuItem;
import android.widget.TextView;

public class IconReferenceActivity extends ActionBarActivity {
    // UI elements
    private static Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_icon_reference);

        // Set up the Toolbar.
        mToolbar = (Toolbar) findViewById(R.id.icon_toolbar);
        if (mToolbar != null) {
            setSupportActionBar(mToolbar);
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set the host text.
        TextView hostText = (TextView) findViewById(R.id.text_view_host);
        hostText.setText(Html.fromHtml(getString(R.string.host_text_prefix) + ": " + Utility.HOST));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Only the back button exists in the menu so we go back to MainActivity.
        Intent goBackIntent = new Intent(this, MainActivity.class);
        startActivity(goBackIntent);
        return true;
    }
}
