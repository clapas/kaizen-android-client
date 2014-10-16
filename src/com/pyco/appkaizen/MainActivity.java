package com.pyco.appkaizen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;


public class MainActivity extends Activity {

    private NotificationManager mNM;
    private AppKaizenService mBoundService;
    private Boolean mIsBound = false;
    private XMPPReceiver xmppReceiver;
    public static final String TAG = "APPKAIZEN_APP";

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.local_service_started;

    @Override
    public void onStop() {
        super.onStop();
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        doBindService();
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
    }
    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "MainActivity resumed");
        if (mBoundService != null) {
            Set<String> subscriptions = mBoundService.getSubscriptions();
            for (String s: subscriptions) {
                addContact(s);
                subscriptions.remove(s);
            }
        }
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
        registerReceiver(xmppReceiver, intentFilter);
        intentFilter = new IntentFilter(AppKaizenService.SUBSCRIBE);
        registerReceiver(xmppReceiver, intentFilter);
        intentFilter = new IntentFilter(AppKaizenService.DISCONNECTED);
        registerReceiver(xmppReceiver, intentFilter);
    }
    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "Activity Paused");
        if (xmppReceiver != null) unregisterReceiver(xmppReceiver);
        mBoundService.getSubscriptions().clear();
    }
    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.local_service_started);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.ic_launcher, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.local_service_label), text, contentIntent);

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
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

            mBoundService.connect("talk.google.com", 5222, "gmail.com");
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
        intent.putExtra(AppKaizenService.XMPP_BOT, "appkaizen.appspotchat.com");
        //startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        //bindService(intent, mConnection, 0);
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
        super.onDestroy();
        doUnbindService();
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);
    }    
    OnClickListener contactBtnClick = new OnClickListener() {
        public void onClick(View v) {
            Intent intent = new Intent(MainActivity.this, DetailActivity.class);
            Button b = (Button) v;
            String from = b.getText().toString();
            List<MessageData> aa = new ArrayList();
            for (Map<String, String> m: mBoundService.getMessages()) {
                if (m.get(AppKaizenService.FROM).equals(from)) aa.add(new MessageData(true, m.get(AppKaizenService.BODY)));
                else if (m.get(AppKaizenService.TO).equals(from)) aa.add(new MessageData(false, m.get(AppKaizenService.BODY)));
            }
            intent.putExtra(AppKaizenService.MESSAGE, aa.toArray(new MessageData[aa.size()]));
            intent.putExtra(AppKaizenService.FROM, from);
            startActivity(intent);
        }
    };
    private void addContact(String jid) {
        Button btn = new Button(this);
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
                mBoundService.login();
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
            } 
        }
    }
}
