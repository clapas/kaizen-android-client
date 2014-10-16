package com.pyco.appkaizen;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import android.accounts.Account;
import android.content.BroadcastReceiver;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
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
    public XMPPConnection connection = null;
    private String user;
    private Roster roster;
    private Queue<Map<String, String>> messages = new LinkedList<Map<String, String>>();
    private Set<String> subscriptions = new HashSet<String>(64);
    private XMPPReceiver xmppReceiver;
    private String xmpp_bot;
    private Boolean try_connect = false;
    private String host;
    private int port;
    private String service;

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
        registerReceiver(xmppReceiver, intentFilter);
        intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(xmppReceiver, intentFilter);
    }
    /*
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
    */
    @Override
    public void onDestroy() {

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
    }
    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        Bundle bun = intent.getExtras();
        xmpp_bot = bun.getString(XMPP_BOT);
        return mBinder;
    }

    public void connect(String host, int port, String service) {
        try_connect = true;
        this.host = host;
        this.port = port;
        this.service = service;
        connect();
    }
    public void connect() {
        Toast.makeText(this, R.string.xmpp_connecting, Toast.LENGTH_SHORT).show();

        SASLAuthentication.registerSASLMechanism("X-OAUTH2", SASLGoogleOAuth2Mechanism.class);
        SASLAuthentication.supportSASLMechanism("X-OAUTH2", 0);
        ConnectionConfiguration connConfig = new ConnectionConfiguration(host, port, service);
        connConfig.setSecurityMode(SecurityMode.required);
        connConfig.setReconnectionAllowed(true);
        connection = new XMPPTCPConnection(connConfig);
        try {
            connection.connect();
            sendBroadcast(new Intent(CONNECTED));
        } catch (Exception e) {
            sendBroadcast(new Intent(ERROR).putExtra("class", e.getClass().getName()).putExtra("message", e.getMessage()));
        }
    }

    public void login() {
        if (connection.getUser() != null) loggedIn();
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
        roster = connection.getRoster();
        connection.addPacketListener(new AppKaizenPacketListener(), new AppKaizenPacketFilter());
        connection.addConnectionListener(new AppKaizenConnectionListener());
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
    private boolean addContact(String jid) {
        if (!subscriptions.add(jid)) return false;

        return true;
    }
    private void addMessage(String from, String to, String body) {
        Map<String, String> m = new HashMap<String, String>();
        m.put(FROM, from);
        m.put(TO, to);
        m.put(BODY, body);
        messages.add(m);
    }
    public Set<String> getSubscriptions() {
        return subscriptions;
    }
    public Queue<? extends Map<String, String>> getMessages() {
        return messages;
    }
    private class AppKaizenConnectionListener implements ConnectionListener {
        @Override
        public void authenticated(XMPPConnection connection) {}
        @Override
        public void connected(XMPPConnection connection) {}
        @Override
        public void connectionClosed() {}
        @Override
        public void connectionClosedOnError(Exception e) {
            sendBroadcast(new Intent(DISCONNECTED));
            try {
                connection.disconnect();
            } catch (Exception e2) {}
	}
        @Override
        public void reconnectingIn(int seconds) {}
        @Override
        public void reconnectionFailed(Exception e) {}
        @Override
        public void reconnectionSuccessful() {}
    }
    private class AppKaizenPacketListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            if (packet instanceof Presence) {
                Presence p = (Presence) packet;
                if (p.getType() == Presence.Type.subscribe) {
                    if (addContact(p.getFrom()))
                        sendBroadcast(new Intent(SUBSCRIBE).putExtra(FROM, p.getFrom()));
                }
            } else if (packet instanceof Message) {
                Message message = (Message) packet;
                String body = message.getBody();
                String from = message.getFrom().split("/")[0];
                addMessage(from, "me", body);
                sendBroadcast(new Intent(MESSAGE).putExtra(FROM, from).putExtra(BODY, body));
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
                if (bun.getString(TO) == null) return;
                addMessage(bun.getString(FROM), bun.getString(TO), bun.getString(BODY));
                Message msg = new Message(bun.getString(TO), Message.Type.chat);
                msg.setBody(bun.getString(BODY));
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
                    if (try_connect && !connection.isConnected()) try {
                        connect();
                    } catch (Exception e) {
                        sendBroadcast(new Intent(ERROR).putExtra("class", e.getClass().getName()).putExtra("message", e.getMessage()));
                    }
                }
            }
        }
    }
}
