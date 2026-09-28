package com.kavin.k20callfix;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CallActionReceiver extends BroadcastReceiver {
    public static final String ACTION_ANSWER = "com.kavin.k20callfix.ANSWER";
    public static final String ACTION_REJECT = "com.kavin.k20callfix.REJECT";

    @Override public void onReceive(Context context, Intent intent) {
        CallFixInCallService svc = CallFixInCallService.getInstance();
        if (svc == null || intent == null) return;

        String action = intent.getAction();
        if (ACTION_ANSWER.equals(action)) {
            svc.answer();
        } else if (ACTION_REJECT.equals(action)) {
            svc.reject();
        }
    }
}
