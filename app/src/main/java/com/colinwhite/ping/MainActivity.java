package com.colinwhite.ping;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;


public class MainActivity extends ActionBarActivity {
    public final static String URL_ID = "URL_ID";

    private static Toolbar mToolbar;
    private static ImageButton mButton;
    private static EditText mEditText;
    private static LinearLayout mActivityContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mActivityContainer = (LinearLayout) findViewById(R.id.activity_container);

        // Attach the main_toolbar to the activity.
        mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        if (mToolbar != null) {
            setSupportActionBar(mToolbar);
        }

        // Set the onClickListener for the ping button to call PingService with the URL from
        // the text field.
        mButton = (ImageButton) findViewById(R.id.button_id);
        Button.OnClickListener onClickListener = new Button.OnClickListener() {
            public void onClick(View v) {
                // Retrieve the text from the URL input field.
                String inputText = mEditText.getText().toString();

                // Validate input
                if (!inputText.isEmpty()) {
                    Intent checkIfUpServiceIntent = new Intent(getApplicationContext(),
                            PingService.class);

                    // Add the URL from the text field to the Intent.
                    checkIfUpServiceIntent.putExtra(URL_ID, inputText);

                    // Remove focus from mEditText once URL has been entered.
                    mActivityContainer.requestFocus();

                    // Close the virtual keyboard.
                    InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);

                    startService(checkIfUpServiceIntent);
                }
            }
        };
        mButton.setOnClickListener(onClickListener);

        // Set the EditText to use the same onClickListener.
        mEditText = (EditText) findViewById(R.id.url_text_field_quick);
        mEditText.setOnClickListener(onClickListener);

        // Instantiate the intent filter and the receiver
        IntentFilter filter = new IntentFilter(PingServiceReceiver.ACTION_RESPONSE);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        PingServiceReceiver receiver = new PingServiceReceiver();
        registerReceiver(receiver, filter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

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

    /**
     * Simple BroadcastReceiver that is notified when the background PingService
     * finishes and updates the main TextView with the returned information.
     */
    public class PingServiceReceiver extends BroadcastReceiver {
        public static final String ACTION_RESPONSE =
                "com.colinwhite.ping.intent.action.CHECK_UP_SERVICE_COMPLETE";

        @Override
        public void onReceive(Context context, Intent intent) {
            TextView textView = (TextView) findViewById(R.id.output_text_view_id);

            switch (intent.getIntExtra(PingService.STATUS_ID, PingService.OTHER)) {
                case PingService.IS_UP:
                    textView.setText(R.string.is_up);
                    break;
                case PingService.IS_DOWN:
                    textView.setText(R.string.is_down);
                    break;
                case PingService.DOES_NOT_EXIST:
                    textView.setText(R.string.does_not_exist);
                    break;
                case PingService.OTHER:
                    textView.setText(R.string.other);
                    break;
            }
        }
    }
}
