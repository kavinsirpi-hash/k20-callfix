package com.kavin.k20callfix;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.TelecomManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_ROLE = 100;
    private static final int REQ_CALL = 101;
    private LinearLayout root;
    private EditText number;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { rebuild(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = dp(22);
        root.setPadding(p,p,p,p);
        setContentView(root);
        rebuild();
    }

    @Override protected void onStart() {
        super.onStart();
        registerReceiver(receiver, new IntentFilter(CallFixInCallService.ACTION_CHANGED));
        rebuild();
    }

    @Override protected void onStop() {
        try { unregisterReceiver(receiver); } catch (Throwable ignored) {}
        super.onStop();
    }

    private void rebuild() {
        root.removeAllViews();
        Call call = CallFixInCallService.getCurrentCall();
        CallFixInCallService svc = CallFixInCallService.getInstance();

        if (call != null && svc != null) {
            root.addView(text(CallFixInCallService.getDisplayNumber(), 30));
            root.addView(text(stateName(call.getState()), 18));

            if (call.getState() == Call.STATE_RINGING) {
                Button a = button("Answer");
                a.setOnClickListener(v -> svc.answer());
                root.addView(a);
                Button r = button("Reject");
                r.setOnClickListener(v -> svc.reject());
                root.addView(r);
            } else if (call.getState() != Call.STATE_DISCONNECTED) {
                Button m = button(svc.isMutedNow() ? "Unmute" : "Mute");
                m.setOnClickListener(v -> { svc.setMuteState(!svc.isMutedNow()); rebuild(); });
                root.addView(m);
                Button s = button(svc.isSpeakerNow() ? "Earpiece" : "Speaker");
                s.setOnClickListener(v -> { svc.setSpeaker(!svc.isSpeakerNow()); rebuild(); });
                root.addView(s);
                Button e = button("End call");
                e.setOnClickListener(v -> svc.end());
                root.addView(e);
            }
            return;
        }

        root.addView(text("K20 CallFix", 30));
        root.addView(text("Incoming ringing is generated inside this app with ToneGenerator, bypassing MIUI's ringtone MediaPlayer path.", 16));

        TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
        boolean isDefault = tm != null && getPackageName().equals(tm.getDefaultDialerPackage());
        root.addView(text(isDefault ? "ACTIVE - default Phone app" : "Not active yet", 18));

        Button makeDefault = button("Make K20 CallFix default Phone app");
        makeDefault.setOnClickListener(v -> requestDialerRole());
        root.addView(makeDefault);

        number = new EditText(this);
        number.setHint("Phone number");
        number.setInputType(InputType.TYPE_CLASS_PHONE);
        root.addView(number);

        Button callButton = button("Call");
        callButton.setOnClickListener(v -> placeCall());
        root.addView(callButton);

        root.addView(text("Rollback: Settings > Apps > Default apps > Phone > Xiaomi/Contacts.", 14));
    }

    private void requestDialerRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQ_ROLE);
                return;
            }
        }
        Intent i = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
        i.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
        startActivityForResult(i, REQ_ROLE);
    }

    private void placeCall() {
        String n = number == null ? "" : number.getText().toString().trim();
        if (n.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
            return;
        }
        TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
        if (tm != null) tm.placeCall(Uri.fromParts("tel", n, null), new Bundle());
    }

    private TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0,dp(10),0,dp(10));
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setMinHeight(dp(56));
        return b;
    }

    private String stateName(int s) {
        switch(s) {
            case Call.STATE_RINGING: return "Incoming call";
            case Call.STATE_DIALING: return "Dialing";
            case Call.STATE_CONNECTING: return "Connecting";
            case Call.STATE_ACTIVE: return "Connected";
            case Call.STATE_HOLDING: return "On hold";
            default: return "Call state " + s;
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
