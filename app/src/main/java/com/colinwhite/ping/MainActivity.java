package com.colinwhite.ping;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;


public class MainActivity extends ActionBarActivity {
    public final static String URL_ID = "URL_ID";

    private static Toolbar mToolbar;
    private static ImageButton mButton;
    private static ImageButton mFloatingButton;
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

        // Set the onClickListener for the ping button to attempt to start the PingService.
        mButton = (ImageButton) findViewById(R.id.ping_button);
        mButton.setOnClickListener(new ImageButton.OnClickListener() {
            public void onClick(View v) {
                startPingService();
            }
        });

        // Give the same logic to the "enter" key on the soft keyboard while in the EditText.
        mEditText = (EditText) findViewById(R.id.url_text_field_quick);
        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean isActionDone = actionId == EditorInfo.IME_ACTION_DONE;
                if (isActionDone) {
                    startPingService();
                }
                return isActionDone;
            }
        });

        // Set the floating button to open the CreateMonitorActivity.
        mFloatingButton = (ImageButton) findViewById(R.id.add_button);
        mFloatingButton.setOnClickListener(new ImageButton.OnClickListener() {
            public void onClick(View v) {
                // Pass the value of the EditText to CreateMonitorActivity.
                Intent createMonitorActivityIntent = new Intent(getApplicationContext(),
                        CreateMonitorActivity.class);
                String text = mEditText.getText().toString();
                if (!text.isEmpty()) {
                    createMonitorActivityIntent.putExtra(CreateMonitorActivity.URL_FIELD_VALUE,
                            text);
                }
                startActivity(createMonitorActivityIntent);
            }
        });

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

    private void startPingService() {
        // Retrieve the text from the URL input field.
        String inputText = mEditText.getText().toString();

        // Validate input
        if (!inputText.isEmpty()) {
            Intent pingServiceIntent = new Intent(getApplicationContext(),
                    PingService.class);

            // Add the URL from the text field to the Intent.
            pingServiceIntent.putExtra(URL_ID, inputText);

            // Close the virtual keyboard.
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);

            // Remove focus from mEditText once URL has been entered.
            mActivityContainer.requestFocus();

            // Instantiate the intent filter and the receiver to receive the output.
            IntentFilter filter = new IntentFilter(PingServiceReceiver.ACTION_RESPONSE);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            PingServiceReceiver receiver = new PingServiceReceiver();
            registerReceiver(receiver, filter);

            startService(pingServiceIntent);
        }
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
            TextView textView = (TextView) findViewById(R.id.output_text_view);

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

            // Don't leak the BroadcastReceiver.
            context.unregisterReceiver(this);
        }
    }
}
