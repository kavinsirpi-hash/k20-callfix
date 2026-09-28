package com.kavin.k20callfix;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.telecom.Call;
import android.telecom.TelecomManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_ROLE = 100;
    private static final int REQ_CALL = 101;
    private static final String PREFS = "callfix";
    private static final String NULL = "__CALLFIX_NULL__";
    private static final String BLOCKED_URI = "content://com.kavin.k20callfix.blocked/system-ringtone";

    private final Handler handler = new Handler();
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

    @Override protected void onResume() {
        super.onResume();
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (sp.getBoolean("waiting_write_settings", false) && Settings.System.canWrite(this)) {
            sp.edit().putBoolean("waiting_write_settings", false).apply();
            applyGuard();
        }
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

        root.addView(text("K20 CallFix v0.4", 30));
        root.addView(text(
                "Crash-safe mode replaces only Android's writable system ringtone value. " +
                "CallFix rings separately and now uses a full-screen CALL notification " +
                "for incoming calls while the phone is locked.",
                16));

        TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
        boolean isDefault = tm != null && getPackageName().equals(tm.getDefaultDialerPackage());
        root.addView(text("Default Phone app: " + (isDefault ? "YES" : "NO"), 18));
        root.addView(text("System-ringer guard: " + (isGuardEnabled() ? "ENABLED" : "DISABLED"), 18));

        Button makeDefault = button("1. Make K20 CallFix default Phone app");
        makeDefault.setOnClickListener(v -> requestDialerRole());
        root.addView(makeDefault);

        Button enable = button("2. Enable crash-safe ringing");
        enable.setOnClickListener(v -> enableGuard());
        root.addView(enable);

        Button test = button("Test CallFix sound");
        test.setOnClickListener(v -> {
            RingerController.start(this);
            handler.postDelayed(RingerController::stop, 2500);
        });
        root.addView(test);

        Button restore = button("Restore Xiaomi system ringing");
        restore.setOnClickListener(v -> restoreGuard());
        root.addView(restore);

        number = new EditText(this);
        number.setHint("Phone number");
        number.setInputType(InputType.TYPE_CLASS_PHONE);
        root.addView(number);

        Button callButton = button("Call");
        callButton.setOnClickListener(v -> placeCall());
        root.addView(callButton);

        root.addView(text(
                "Restore Xiaomi system ringing before uninstalling CallFix. " +
                "If needed, selecting a normal ringtone in MIUI Settings also restores it.",
                14));
    }

    private void enableGuard() {
        if (!Settings.System.canWrite(this)) {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean("waiting_write_settings", true).apply();

            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Throwable t) {
                Toast.makeText(this, "Modify-system-settings screen unavailable", Toast.LENGTH_LONG).show();
            }
            return;
        }
        applyGuard();
    }

    private void applyGuard() {
        backupOriginals();

        boolean ringtoneOk = false;
        try {
            ringtoneOk = Settings.System.putString(
                    getContentResolver(), Settings.System.RINGTONE, BLOCKED_URI);
        } catch (Throwable t) {
            Toast.makeText(this, "Could not replace system ringtone: " + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }

        boolean volumeOk = false;
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            try {
                am.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
                volumeOk = am.getStreamVolume(AudioManager.STREAM_RING) == 0;
            } catch (Throwable t) {
                Toast.makeText(this, "Could not mute MIUI ring stream: " + t.getClass().getSimpleName(),
                        Toast.LENGTH_LONG).show();
            }
        }

        String actual = Settings.System.getString(getContentResolver(), Settings.System.RINGTONE);
        boolean actualOk = BLOCKED_URI.equals(actual);

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("guard_enabled", ringtoneOk && actualOk && volumeOk)
                .apply();

        if (actualOk && volumeOk) {
            Toast.makeText(this, "Crash-safe ringing enabled", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this,
                    "Guard incomplete. Ringtone=" + (actualOk ? "OK" : "FAILED") +
                    ", ring volume=" + (volumeOk ? "OK" : "FAILED"),
                    Toast.LENGTH_LONG).show();
        }
        rebuild();
    }

    private void backupOriginals() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (sp.getBoolean("backed_up", false)) return;

        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int ringVol = am == null ? -1 : am.getStreamVolume(AudioManager.STREAM_RING);

        sp.edit()
                .putString("orig_ringtone",
                        encode(Settings.System.getString(getContentResolver(), Settings.System.RINGTONE)))
                .putInt("orig_ring_volume", ringVol)
                .putBoolean("backed_up", true)
                .apply();
    }

    private void restoreGuard() {
        if (!Settings.System.canWrite(this)) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Throwable t) {
                Toast.makeText(this, "Modify-system-settings screen unavailable", Toast.LENGTH_LONG).show();
            }
            return;
        }

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (sp.getBoolean("backed_up", false)) {
            try {
                Settings.System.putString(getContentResolver(), Settings.System.RINGTONE,
                        decode(sp.getString("orig_ringtone", NULL)));
            } catch (Throwable t) {
                Toast.makeText(this, "Could not restore ringtone", Toast.LENGTH_LONG).show();
            }

            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int oldVolume = sp.getInt("orig_ring_volume", -1);
            if (am != null && oldVolume >= 0) {
                try { am.setStreamVolume(AudioManager.STREAM_RING, oldVolume, 0); }
                catch (Throwable ignored) {}
            }
        }

        sp.edit()
                .putBoolean("guard_enabled", false)
                .putBoolean("backed_up", false)
                .remove("orig_ringtone")
                .remove("orig_ring_volume")
                .apply();

        Toast.makeText(this, "System ringing restored", Toast.LENGTH_SHORT).show();
        rebuild();
    }

    private boolean isGuardEnabled() {
        String current = Settings.System.getString(getContentResolver(), Settings.System.RINGTONE);
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        boolean volumeZero = am != null && am.getStreamVolume(AudioManager.STREAM_RING) == 0;
        return BLOCKED_URI.equals(current) && volumeZero;
    }

    private String encode(String value) { return value == null ? NULL : value; }
    private String decode(String value) { return NULL.equals(value) ? null : value; }

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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
