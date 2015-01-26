package com.colinwhite.ping;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;

public class CreateMonitorActivity extends ActionBarActivity {

    public static final String URL_FIELD_VALUE = "URL_FIELD_VALUE";

    private static Toolbar mToolbar;
    private static ImageView mMonitorIcon;
    private static EditText mUrlField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_monitor);

        // Set the icon's width to be equal to its height (square).
        mMonitorIcon = (ImageView) findViewById(R.id.monitor_icon);
        mMonitorIcon.setMaxWidth(mMonitorIcon.getHeight());

        // Attach the create_monitor_toolbar to the activity.
        mToolbar = (Toolbar) findViewById(R.id.create_monitor_toolbar);
        if (mToolbar != null) {
            setSupportActionBar(mToolbar);
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mUrlField = (EditText) findViewById(R.id.url_text_field_create);

        // Set the URL EditText to the passed value.
        Intent intent = getIntent();
        if (intent.hasExtra(URL_FIELD_VALUE)) {
            mUrlField.setText(intent.getStringExtra(URL_FIELD_VALUE));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_create_monitor, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
