package com.pyco.appkaizen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;


public class MainActivity extends Activity {

    private AppKaizenService mBoundService;
    private Boolean mIsBound = false;
    private XMPPReceiver xmppReceiver;
    private String xmpp_bot;
    private NotificationManager mNM;
    private ColorStateList defaultTextColors;
    private Set<String> contacts = new HashSet<String>(64);
    private String detail;
    public static final String TAG = "APPKAIZEN_APP";

    @Override
    public void onStop() {
        super.onStop();
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        xmpp_bot = sharedPref.getString("xmpp_bot", "");
        doBindService();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.options:
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        case R.id.kill_service:
            mBoundService.stop();
            doUnbindService();
            finish();
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    public void refreshUI() {
        if (mBoundService != null) {
            if (detail != null) {
                mBoundService.getSeen().put(detail, true);
                detail = null;
            }
            Set<String> subscriptions = mBoundService.getSubscriptions();
            for (String s: subscriptions) addContact(s);
            updateContacts();
        }
    }
    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "MainActivity resumed");
        refreshUI();
        if (xmppReceiver == null) xmppReceiver = new XMPPReceiver();
        IntentFilter intentFilter = new IntentFilter(AppKaizenService.CONNECTED);
        registerReceiver(xmppReceiver, intentFilter);
        intentFilter = new IntentFilter(AppKaizenService.LOGGED_IN);
        registerReceiver(xmppReceiver, intentFilter);
        intentFilter = new IntentFilter(AppKaizenService.LOGIN_FAILED);
        registerReceiver(xmppReceiver, intentFilter);
        intentFilter = new IntentFilter(AppKaizenService.ERROR);
        registerReceiver(xmppReceiver, intentFilter);
        intentFilter = new IntentFilter(AppKaizenService.MESSAGE);
        intentFilter.setPriority(1);
        registerReceiver(xmppReceiver, intentFilter);
        intentFilter = new IntentFilter(AppKaizenService.SUBSCRIBE);
        registerReceiver(xmppReceiver, intentFilter);
        intentFilter = new IntentFilter(AppKaizenService.DISCONNECTED);
        registerReceiver(xmppReceiver, intentFilter);
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNM.cancel(AppKaizenService.NOTIFICATION);
    }
    private void updateContacts() {
        LinearLayout ll = (LinearLayout) findViewById(R.id.listConvs);
        Map<String, Queue<Map<String, String>>> messages = mBoundService.getMessages();
        Map<String, Boolean> seen = mBoundService.getSeen();
        for (int i = 0; i < ll.getChildCount(); i++) {
            Button btn = (Button) ll.getChildAt(i);
            String jid = (String) btn.getText();
            int ss = messages.get(jid).size();
            if (!seen.get(jid)) btn.setTextColor(android.graphics.Color.parseColor("#00aa00"));
            else btn.setTextColor(defaultTextColors);
        }
    }
    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "Activity Paused");
        if (xmppReceiver != null) unregisterReceiver(xmppReceiver);
        //mBoundService.getSubscriptions().clear();
    }
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((AppKaizenService.LocalBinder)service).getService();
    
            // Tell the user about this for our demo.
            Toast.makeText(MainActivity.this, R.string.local_service_connected,
                    Toast.LENGTH_SHORT).show();

            mBoundService.login("talk.google.com", 5222, "gmail.com");
            refreshUI();
        }
    
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
            Toast.makeText(MainActivity.this, R.string.local_service_disconnected, Toast.LENGTH_SHORT).show();
        }
    };
    
    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        Intent intent = new Intent(this, AppKaizenService.class);
        intent.putExtra(AppKaizenService.XMPP_BOT, xmpp_bot);
        startService(intent);
        bindService(intent, mConnection, 0);
        //bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }
    
    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }
    @Override
    protected void onDestroy() {
        Log.i(TAG, "activity destroy");
        super.onDestroy();
        doUnbindService();
    }    
    OnClickListener contactBtnClick = new OnClickListener() {
        public void onClick(View v) {
            Intent intent = new Intent(MainActivity.this, DetailActivity.class);
            Button b = (Button) v;
            String from = b.getText().toString();
            List<MessageData> aa = new ArrayList();
            detail = from;
            for (Map<String, String> m: mBoundService.getMessages().get(from)) { // null pointer exception here! why?
                if (m.get(AppKaizenService.FROM).equals(from)) aa.add(new MessageData(true, m.get(AppKaizenService.BODY)));
                else if (m.get(AppKaizenService.TO).equals(from)) aa.add(new MessageData(false, m.get(AppKaizenService.BODY)));
            }
            intent.putExtra(AppKaizenService.MESSAGE, aa.toArray(new MessageData[aa.size()]));
            intent.putExtra(AppKaizenService.FROM, from);
            startActivity(intent);
        }
    };
    private void addContact(String jid) {
        if (!contacts.add(jid)) return;
        Button btn = new Button(this);
        if (defaultTextColors == null) defaultTextColors =  btn.getTextColors();
        btn.setText(jid);
        btn.setOnClickListener(contactBtnClick);
        LinearLayout ll = (LinearLayout) findViewById(R.id.listConvs);
        ll.addView(btn);
    }
    private class XMPPReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(AppKaizenService.CONNECTED)) {
                Toast.makeText(MainActivity.this, "Connected to server", Toast.LENGTH_SHORT).show();
                //if (mBoundService != null) mBoundService.login(); // in case the service autoconnected before the activity got bound to it
            } else if (intent.getAction().equals(AppKaizenService.LOGGED_IN)) {
                Toast.makeText(MainActivity.this, "Logged in!", Toast.LENGTH_SHORT).show();
            } else if (intent.getAction().equals(AppKaizenService.LOGIN_FAILED)) {
                Toast.makeText(MainActivity.this, "Login failed", Toast.LENGTH_SHORT).show();
            } else if (intent.getAction().equals(AppKaizenService.ERROR)) {
                Bundle bun = intent.getExtras();
                Toast.makeText(MainActivity.this, "Error: (" + bun.getString("class") + ") " + bun.getString("message"), Toast.LENGTH_SHORT).show();
            } else if (intent.getAction().equals(AppKaizenService.SUBSCRIBE)) {
                Bundle bun = intent.getExtras();
                Log.i(TAG, "Subscribe packet from " + bun.getString(AppKaizenService.FROM));
                addContact(bun.getString(AppKaizenService.FROM));
            } else if (intent.getAction().equals(AppKaizenService.DISCONNECTED)) {
                Toast.makeText(MainActivity.this, "Disconnected from the server", Toast.LENGTH_SHORT).show();
            } else if (intent.getAction().equals(AppKaizenService.MESSAGE)) {
                Bundle bun = intent.getExtras();
                Log.i(TAG, "Message packet from " + bun.getString(AppKaizenService.FROM));
                updateContacts();
                abortBroadcast();
            }
        }
    }
}
