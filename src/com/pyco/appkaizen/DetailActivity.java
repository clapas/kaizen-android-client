package com.pyco.appkaizen;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.widget.LinearLayout;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

public class DetailActivity extends Activity {
    public static final String TAG = "APPKAIZEN_APP";
    private String current_jid;
    private XMPPReceiver xmppReceiver;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detail);
        Intent intent = getIntent();
        current_jid = intent.getStringExtra(AppKaizenService.FROM);
        Parcelable[] msgs = intent.getParcelableArrayExtra(AppKaizenService.MESSAGE);
        LinearLayout ll = (LinearLayout) findViewById(R.id.detailConv);
        TextView tv;
        if (msgs != null) for (int i = 0; i < msgs.length; i++) {
            tv = new TextView(this);
            if (!((MessageData) msgs[i]).getReceived()) tv.setGravity(5);
            tv.setText(((MessageData)msgs[i]).getBody());
            ll.addView(tv);
        }
        fullScrollDown();
    }
    public void fullScrollDown() {
        final ScrollView sv = (ScrollView)findViewById(R.id.scroll);
        sv.post(new Runnable() {            
            @Override
            public void run() {
                sv.fullScroll(View.FOCUS_DOWN);              
            }
        });
    }
    public void sendMessage(View view) {
        EditText editText = (EditText) findViewById(R.id.editText);
        String message = editText.getText().toString();
        editText.setText("");
        TextView tv = new TextView(this);
        tv.setGravity(5);
        tv.setText(message);
        LinearLayout ll = (LinearLayout) findViewById(R.id.detailConv);
        ll.addView(tv);
        sendBroadcast(new Intent(AppKaizenService.MESSAGE).
            putExtra(AppKaizenService.FROM, "me").
            putExtra(AppKaizenService.TO, current_jid).
            putExtra(AppKaizenService.BODY, message));
        fullScrollDown();
    }
    @Override
    public void onResume() {
        super.onResume();
        if (xmppReceiver == null) xmppReceiver = new XMPPReceiver();
        IntentFilter intentFilter = new IntentFilter(AppKaizenService.MESSAGE);
        registerReceiver(xmppReceiver, intentFilter);
    }
    private void appendMessage(String body) {
        TextView tv = new TextView(this);
        tv.setText(body);
        LinearLayout ll = (LinearLayout) findViewById(R.id.detailConv);
        ll.addView(tv);
        fullScrollDown();
    }
    private class XMPPReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(AppKaizenService.MESSAGE)) {
                Bundle bun = intent.getExtras();
                if (bun.getString(AppKaizenService.FROM).equals(current_jid))
                    appendMessage(bun.getString(AppKaizenService.BODY));
            }
        }
    }
}
