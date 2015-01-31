package com.colinwhite.ping;

import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.colinwhite.ping.data.PingContract;
import com.colinwhite.ping.data.PingContract.MonitorEntry;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MonitorDetailActivity extends ActionBarActivity {

    public static final String URL_FIELD_VALUE = "URL_FIELD_VALUE";
    private static final String DATE_FORMAT = "dd/MM/yy";

    // Database fields
    private static EditText mTitleField;
    private static EditText mUrlField;
    private static SeekBar mPingFrequency;
    private static Calendar mEndDate;

    private static Toolbar mToolbar;
    private static ImageView mMonitorIcon;
    private static TextView mDatePickerOutput;
    private static DatePickerDialog mDatePickerDialog;
    private static Button mSaveButton;
    private boolean mHasEndDate = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor_detail);

        // Set the icon's width to be equal to its height (square).
        mMonitorIcon = (ImageView) findViewById(R.id.monitor_icon);
        mMonitorIcon.setMaxWidth(mMonitorIcon.getHeight());

        // Attach the create_monitor_toolbar to the activity.
        mToolbar = (Toolbar) findViewById(R.id.create_monitor_toolbar);
        if (mToolbar != null) {
            setSupportActionBar(mToolbar);
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Make the DatePicker set the output TextField's date when it is changed.
        mEndDate = Calendar.getInstance();
        final DatePickerDialog.OnDateSetListener date = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int day) {
                mEndDate.set(Calendar.YEAR, year);
                mEndDate.set(Calendar.MONTH, month);
                mEndDate.set(Calendar.DAY_OF_MONTH, day);

                // Set the output TextView's text.
                SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT, Locale.UK);
                mDatePickerOutput.setText(format.format(mEndDate.getTime()));

                mHasEndDate = true;
            }
        };

        // Set the DatePicker to popup when the TextField is clicked.
        mDatePickerOutput = (TextView) findViewById(R.id.date_picker_output);
        mDatePickerDialog = new DatePickerDialog(this,
                date,
                mEndDate.get(Calendar.YEAR),
                mEndDate.get(Calendar.MONTH),
                mEndDate.get(Calendar.DAY_OF_MONTH));
        mDatePickerOutput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDatePickerDialog.show();
            }
        });

        // Set the URL EditText to the passed value.
        mUrlField = (EditText) findViewById(R.id.url_text_field_create);
        Intent intent = getIntent();
        if (intent.hasExtra(URL_FIELD_VALUE)) {
            mUrlField.setText(intent.getStringExtra(URL_FIELD_VALUE));
        }

        // Get references to the title field and ping frequency seek bar.
        mTitleField = (EditText) findViewById(R.id.create_monitor_title);
        mPingFrequency = (SeekBar) findViewById(R.id.ping_frequency_seek_bar);

        // Set the save button to write to the database and close the activity.
        mSaveButton = (Button) findViewById(R.id.save_button);
        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ContentValues values = new ContentValues();
                values.put(MonitorEntry.TITLE, mTitleField.getText().toString());
                values.put(MonitorEntry.URL, mUrlField.getText().toString());
                values.put(MonitorEntry.PING_FREQUENCY, mPingFrequency.getProgress());
                if (mHasEndDate) {
                    values.put(MonitorEntry.END_DATE, mEndDate.getTimeInMillis());
                }

                getContentResolver().insert(PingContract.MonitorEntry.CONTENT_URI, values);
                // Notify the Monitor ListView that the database has been updated.
                getContentResolver().notifyChange(MonitorEntry.CONTENT_URI, null);
                finish();
            }
        });
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
