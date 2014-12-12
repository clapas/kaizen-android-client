package com.pyco.appkaizen;

import com.pyco.appkaizen.KaizenContract.KaizenContact;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.SmackAndroid;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.XMPPConnection;


public class AppKaizenService extends Service {

    public static final String CONNECTED = "com.pyco.appkaizen.CONNECTED";
    public static final String DISCONNECTED = "com.pyco.appkaizen.DISCONNECTED";
    public static final String LOGGED_IN = "com.pyco.appkaizen.LOGGED_IN";
    public static final String TRY_LOGIN = "com.pyco.appkaizen.TRY_LOGIN";
    public static final String LOGIN_FAILED = "com.pyco.appkaizen.LOGIN_FAILED";
    public static final String ERROR = "com.pyco.appkaizen.ERROR";
    public static final String MESSAGE = "com.pyco.appkaizen.MESSAGE";
    public static final String SUBSCRIBE = "com.pyco.appkaizen.SUBSCRIBE";
    public static final String ACTION = "com.pyco.appkaizen.ACTION";
    public static final String FROM = "com.pyco.appkaizen.FROM";
    public static final String TO = "com.pyco.appkaizen.TO";
    public static final String BODY = "com.pyco.appkaizen.BODY";
    public static final String XMPP_BOT = "com.pyco.appkaizen.XMPP_BOT";
    public static final String TAG = "APPKAIZEN_APP";
    public static final String PORT = "com.pycoh.appkaizen.PORT";
    public static final String HOST = "com.pycoh.appkaizen.HOST";
    public static final String SERVICE = "com.pycoh.appkaizen.SERVICE";
    public static final int NOTIFICATION = R.string.local_service_started;
    public XMPPConnection connection = null;

    private NotificationManager mNM;
    private NotificationCompat.Builder mNotifyBuilder;

    private String user;
    private Roster roster;
    private Map<String, Queue<Map<String, String>>> messages = new HashMap<String, Queue<Map<String, String>>>(64);
    private Map<String, Boolean> seen = new HashMap<String, Boolean>(64);
    private Set<String> subscriptions = new HashSet<String>(64);
    private XMPPReceiver xmppReceiver;
    private String xmpp_bot;
    private Boolean try_login = false;
    private String host;
    private int port;
    private String service;
    private KaizenDbHelper mDbHelper;
    private SQLiteDatabase wDb, rDb;

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        AppKaizenService getService() {
            return AppKaizenService.this;
        }
    }

    @Override
    public void onCreate() {

        Context context = getApplicationContext();
        SmackAndroid asmk = SmackAndroid.init(context);

        xmppReceiver = new XMPPReceiver();
        IntentFilter intentFilter = new IntentFilter(MESSAGE);
        intentFilter.setPriority(0);
        registerReceiver(xmppReceiver, intentFilter);
        intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(xmppReceiver, intentFilter);

        mDbHelper = new KaizenDbHelper(context);
        wDb = mDbHelper.getWritableDatabase();
        rDb = mDbHelper.getReadableDatabase();
        retrieveContacts();
        
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (sp.getBoolean(TRY_LOGIN, false)) {
            try_login = true;
            host = sp.getString(HOST, "");
            if (host.equals("")) return;
            port = sp.getInt(PORT, -1);
            service = sp.getString(SERVICE, "");
            xmpp_bot = sp.getString(XMPP_BOT, "");
            login(host, port, service);
            Log.i(TAG, "Service created. Is connected? " + connection.isConnected() + "; user: " + connection.getUser());
        }
    }
    private void retrieveContacts() {
        // Projection specifies which columns from the database you will actually use after this query.
        String[] projection = {
            KaizenContact._ID,
            KaizenContact.COLUMN_NAME_JID,
        };
        
        // How you want the results sorted in the resulting Cursor
        //String sortOrder = KaizenContact.COLUMN_NAME_JID + " DESC";
        
        Cursor cursor = rDb.query(
            KaizenContact.TABLE_NAME,  // The table to query
            projection,                               // The columns to return
            null, //selection,                                // The columns for the WHERE clause
            null, //selectionArgs,                            // The values for the WHERE clause
            null,                                     // don't group the rows
            null,                                     // don't filter by row groups
            null //sortOrder                                 // The sort order
        );
        while (cursor.moveToNext()) {
            String jid = cursor.getString(cursor.getColumnIndexOrThrow(KaizenContact.COLUMN_NAME_JID));
            addContact(jid);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
    public void stop() {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putBoolean(TRY_LOGIN, false);
        editor.commit();
        stopSelf();
    }
    @Override
    public void onDestroy() {

        Log.i(TAG, "service destroy");
        // Tell the user we stopped.
        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();

        try {
            roster.reload();
            Collection<RosterEntry> entries = roster.getEntries();
            for (RosterEntry re: entries) 
                if (re.getUser().indexOf("@" + xmpp_bot) != -1)
                    roster.removeEntry(re);
            connection.disconnect();
        } catch (Exception e) {}
        if (xmppReceiver != null) unregisterReceiver(xmppReceiver);
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);
    }
    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        Bundle bun = intent.getExtras();
        xmpp_bot = bun.getString(XMPP_BOT);
        Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(XMPP_BOT, xmpp_bot);
        editor.commit();
        return mBinder;
    }
    public void login(String host, int port, String service) {
        boolean reconnect;
        try_login = true;
        Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putBoolean(TRY_LOGIN, true);
        if (!host.equals(this.host) || port != this.port || !service.equals(this.service)) {
            reconnect = true;
            editor.putString(HOST, host);
            editor.putInt(PORT, port);
            editor.putString(SERVICE, service);
        } else reconnect = false;
        editor.commit();
        this.host = host;
        this.port = port;
        this.service = service;
        if (connect(reconnect)) loginInternal();
    }
    private boolean connect(boolean reconnect) {
        if (connection != null) {
            if (reconnect) try {
                connection.disconnect();
            } catch (Exception e) {}
            else if (connection.isConnected()) {
                sendBroadcast(new Intent(CONNECTED));
                return true;
            } else try {
                connection.connect();
                sendBroadcast(new Intent(CONNECTED));
                return true;
            } catch (Exception e) {
                sendBroadcast(new Intent(ERROR).putExtra("class", e.getClass().getName()).putExtra("message", e.getMessage()));
                return false;
            }
        }
        Toast.makeText(this, R.string.xmpp_connecting, Toast.LENGTH_SHORT).show();

        SASLAuthentication.registerSASLMechanism("X-OAUTH2", SASLGoogleOAuth2Mechanism.class);
        SASLAuthentication.supportSASLMechanism("X-OAUTH2", 0);
        ConnectionConfiguration connConfig = new ConnectionConfiguration(host, port, service);
        connConfig.setSecurityMode(SecurityMode.required);
        connConfig.setReconnectionAllowed(true);
        connection = new XMPPTCPConnection(connConfig);
        connection.addPacketListener(new AppKaizenPacketListener(), new AppKaizenPacketFilter());
        connection.addConnectionListener(new AppKaizenConnectionListener());
        
        try {
            connection.connect();
            sendBroadcast(new Intent(CONNECTED));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void loginInternal() {
        if (connection.getUser() != null) {
            loggedIn();
            return;
        }
        String GOOGLE_TOKEN_TYPE = "oauth2:https://www.googleapis.com/auth/googletalk";
        AccountManager am = AccountManager.get(this);
        Account accounts[] = am.getAccountsByType("com.google");
        this.user = accounts[0].name;
        new GetAuthTokenTask(am).execute(accounts[0], GOOGLE_TOKEN_TYPE, true);
    }
    public void afterGetAuthToken(String authToken) {
        new LoginTask().execute(user, authToken, "appkaizen");
    }
    private class GetAuthTokenTask extends AsyncTask<Object, Void, String> {

        AccountManager am;
        public GetAuthTokenTask(AccountManager am) {
            this.am = am;
        }

        @Override
        protected String doInBackground(Object... params) {
            Account account;
            String tokenType;
            Boolean notify;
            try {
                if (params[0] instanceof Account) account = (Account) params[0];
                else throw new Exception("Bad account");
                if (params[1] instanceof String) tokenType = (String) params[1];
                else throw new Exception("Bad token type");
                if (params[2] instanceof Boolean) notify = (Boolean) params[2];
                else throw new Exception("Bad notification flag");
            } catch (Exception e) {
                sendBroadcast(new Intent(ERROR).putExtra("class", e.getClass().getName()).putExtra("message", e.getMessage()));
                return null;
            }
            try {
                return am.blockingGetAuthToken(account, tokenType, notify);
            } catch (Exception e) {
                sendBroadcast(new Intent(ERROR).putExtra("class", e.getClass().getName()).putExtra("message", e.getMessage()));
                return null;
            }
        }
        protected void onPostExecute(String token) {
            if (token != null) afterGetAuthToken(token);
        }
    }
    private void loggedIn() {
        sendBroadcast(new Intent(LOGGED_IN));
        roster = connection.getRoster(); // needed to be able to receive presence packets, e.g. subscribe, available, etc.
    }
    private class LoginTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {
            String user, tokenType, resource;
            try {
                user = params[0];
                tokenType = params[1];
                resource = params[2];
            } catch (Exception e) {
                return false;
            }
            try {
                connection.login(user, tokenType, resource);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        protected void onPostExecute(Boolean logged_in) {
            if (logged_in) loggedIn();
            else sendBroadcast(new Intent(LOGIN_FAILED));
        }
    }
    private void saveContact(String jid) {
        wDb = mDbHelper.getWritableDatabase();
        // Create a new map of values, where column names are the keys
        ContentValues values = new ContentValues();
        values.put(KaizenContact.COLUMN_NAME_JID, jid);

        // Insert the new row, returning the primary key value of the new row
        wDb.insert(KaizenContact.TABLE_NAME, null, values);
    }
    private boolean addContact(String jid) {
        if (!subscriptions.add(jid)) return false;

        return true;
    }
    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.local_service_started);

        // The PendingIntent to launch our activity if the user selects this notification
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);

        // Send the notification.
        mNotifyBuilder = new NotificationCompat.Builder(this)
            .setContentTitle(getText(R.string.local_service_label))
        //    .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(contentIntent);
        //mNM.notify(NOTIFICATION, mNotifyBuilder.build());
    }
    private void addMessage(String from, String to, String body) {
        String contact;
        if (from.equals("me")) contact = to;
        else contact = from;
        if (messages.get(contact) == null) messages.put(contact, new LinkedList<Map<String, String>>());
        Map<String, String> m = new HashMap<String, String>();
        m.put(FROM, from);
        m.put(TO, to);
        m.put(BODY, body);
        messages.get(contact).add(m);
    }
    public Map<String, Boolean> getSeen() {
        return seen;
    }
    public Set<String> getSubscriptions() {
        return subscriptions;
    }
    public Map<String, Queue<Map<String, String>>> getMessages() {
        return messages;
    }
    private class AppKaizenConnectionListener implements ConnectionListener {
        @Override
        public void authenticated(XMPPConnection connection) {}
        @Override
        public void connected(XMPPConnection connection) {}
        @Override
        public void connectionClosed() {
            Log.i(TAG, "Connection closed");
            sendBroadcast(new Intent(DISCONNECTED));
        }
        @Override
        public void connectionClosedOnError(Exception e) {
            Log.i(TAG, "Connection closed on error");
            sendBroadcast(new Intent(DISCONNECTED));
	}
        @Override
        public void reconnectingIn(int seconds) {
            //Log.i(TAG, "Reconnecting");
        }
        @Override
        public void reconnectionFailed(Exception e) {
            Log.i(TAG, "Reconnection failed");
        }
        @Override
        public void reconnectionSuccessful() {
            Log.i(TAG, "Reconnection successful; user: " + connection.getUser());
        }
    }
    private class AppKaizenPacketListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            if (packet instanceof Presence) {
                Presence p = (Presence) packet;
                Log.i(TAG, "AK " + p.getType().toString() + " packet from: " + p.getFrom());
                if (p.getType() == Presence.Type.subscribe) {
                    if (addContact(p.getFrom())) {
                        saveContact(p.getFrom());
                        sendBroadcast(new Intent(SUBSCRIBE).putExtra(FROM, p.getFrom()));
                    }
                }
            } else if (packet instanceof Message) {
                Message message = (Message) packet;
                if (roster == null) Log.i(TAG, "Roster is null!!!!!!!");
                Log.i(TAG, "AK some message packet packet from: " + message.getFrom());
                String body = message.getBody();
                String from = message.getFrom().split("/")[0];
                addMessage(from, "me", body);
                sendOrderedBroadcast(new Intent(MESSAGE).putExtra(FROM, from).putExtra(BODY, body), null);
                seen.put(from, false);
            }
        }
    }
    private class AppKaizenPacketFilter implements PacketFilter {
        @Override
        public boolean accept(Packet packet) {
            return (packet.getFrom() != null && 
                    packet.getFrom().indexOf("@" + xmpp_bot) != -1);
        }
    }
    private class XMPPReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(MESSAGE)) {
                Bundle bun = intent.getExtras();
                String from = bun.getString(FROM);
                String body = bun.getString(BODY);
                if (bun.getString(TO) == null) {
                    mNotifyBuilder.setContentText(from.substring(0, from.indexOf('@')) + ": " + body);
                    mNotifyBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                    //mNotifyBuilder.setDefaults(Notification.DEFAULT_VIBRATE);
                    mNM.notify(NOTIFICATION, mNotifyBuilder.build());
                    return;
                }
                addMessage("me", bun.getString(TO), body);
                Message msg = new Message(bun.getString(TO), Message.Type.chat);
                msg.setBody(body);
                try {
                    connection.sendPacket(msg);
                } catch (Exception e) {
                    sendBroadcast(new Intent(ERROR).putExtra("class", e.getClass().getName()).putExtra("message", e.getMessage()));
                }
            } else if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)){
                Bundle bun = intent.getExtras();
                if (bun == null) return;
                NetworkInfo ni = (NetworkInfo) bun.get(ConnectivityManager.EXTRA_NETWORK_INFO);
                if (ni != null && ni.getState() == NetworkInfo.State.CONNECTED) {
                    if (try_login) {
                        connect(false);
                        loginInternal();
                    }
                    Log.i(TAG, "Network available. Is connected? " + ((connection != null) ? connection.isConnected() + "; user: " + connection.getUser() : "NO"));
                } else Log.i(TAG, "Network disconnected");
            }
        }
    }
}
