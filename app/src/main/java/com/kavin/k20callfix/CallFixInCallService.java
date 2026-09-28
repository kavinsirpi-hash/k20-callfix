package com.kavin.k20callfix;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telecom.VideoProfile;

public class CallFixInCallService extends InCallService {
    public static final String ACTION_CHANGED = "com.kavin.k20callfix.CALL_CHANGED";
    private static volatile CallFixInCallService instance;
    private static volatile Call currentCall;

    private final Call.Callback callback = new Call.Callback() {
        @Override public void onStateChanged(Call call, int state) { handle(call); }
        @Override public void onDetailsChanged(Call call, Call.Details details) {
            if (call.getState() == Call.STATE_RINGING) {
                IncomingCallNotifier.show(CallFixInCallService.this, getDisplayNumber());
            }
            broadcastChange();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        IncomingCallNotifier.ensureChannel(this);
    }

    @Override public void onDestroy() {
        RingerController.stop();
        IncomingCallNotifier.cancel(this);
        instance = null;
        super.onDestroy();
    }

    @Override public void onCallAdded(Call call) {
        super.onCallAdded(call);
        currentCall = call;
        call.registerCallback(callback);
        handle(call);
    }

    @Override public void onCallRemoved(Call call) {
        RingerController.stop();
        IncomingCallNotifier.cancel(this);
        try { call.unregisterCallback(callback); } catch (Throwable ignored) {}
        if (currentCall == call) currentCall = null;
        broadcastChange();
        super.onCallRemoved(call);
    }

    private void handle(Call call) {
        if (call.getState() == Call.STATE_RINGING) {
            RingerController.start(this);
            IncomingCallNotifier.show(this, getDisplayNumber());

            KeyguardManager km =
                    (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            boolean locked = km != null && km.isKeyguardLocked();
            if (!locked) launchUi();
        } else {
            RingerController.stop();
            IncomingCallNotifier.cancel(this);

            if (call.getState() == Call.STATE_DIALING ||
                    call.getState() == Call.STATE_CONNECTING ||
                    call.getState() == Call.STATE_ACTIVE) {
                launchUi();
            }
        }
        broadcastChange();
    }

    private void launchUi() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Throwable ignored) {}
    }

    private void broadcastChange() {
        sendBroadcast(new Intent(ACTION_CHANGED).setPackage(getPackageName()));
    }

    public static CallFixInCallService getInstance() { return instance; }
    public static Call getCurrentCall() { return currentCall; }

    public static String getDisplayNumber() {
        try {
            Call c = currentCall;
            if (c != null && c.getDetails() != null && c.getDetails().getHandle() != null) {
                String n = c.getDetails().getHandle().getSchemeSpecificPart();
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Throwable ignored) {}
        return "Unknown number";
    }

    public void answer() {
        Call c = currentCall;
        if (c != null && c.getState() == Call.STATE_RINGING) {
            RingerController.stop();
            IncomingCallNotifier.cancel(this);
            c.answer(VideoProfile.STATE_AUDIO_ONLY);
            launchUi();
        }
    }

    public void reject() {
        Call c = currentCall;
        if (c != null) {
            RingerController.stop();
            IncomingCallNotifier.cancel(this);
            if (c.getState() == Call.STATE_RINGING) c.reject(false, null);
            else c.disconnect();
        }
    }

    public void end() {
        Call c = currentCall;
        if (c != null) {
            RingerController.stop();
            IncomingCallNotifier.cancel(this);
            c.disconnect();
        }
    }

    public boolean isMutedNow() {
        CallAudioState s = getCallAudioState();
        return s != null && s.isMuted();
    }

    public void setMuteState(boolean mute) { setMuted(mute); }

    public boolean isSpeakerNow() {
        CallAudioState s = getCallAudioState();
        return s != null && (s.getRoute() & CallAudioState.ROUTE_SPEAKER) != 0;
    }

    public void setSpeaker(boolean speaker) {
        setAudioRoute(speaker ? CallAudioState.ROUTE_SPEAKER : CallAudioState.ROUTE_EARPIECE);
    }
}
