package com.colinwhite.ping;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The IconReferenceActivity class holds an almost static layout that simply displays all the icons
 * and their descriptions. The host text is the only text/image set at runtime.
 */
public class IconReferenceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_icon_reference);

        // Set up the Toolbar.
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        try {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        } catch (NullPointerException e) {
            Toast.makeText(this, getString(R.string.error), Toast.LENGTH_LONG).show();
            finish();
        }

        // Set the host text.
        TextView hostText = (TextView) findViewById(R.id.text_view_host);
        hostText.setText(Html.fromHtml(getString(R.string.host_text_prefix) + ": " + Utility.HOST));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Only the back button exists in the menu so we go back to MainActivity.
        onBackPressed();
        return true;
    }
}
