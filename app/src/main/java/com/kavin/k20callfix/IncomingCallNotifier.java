package com.kavin.k20callfix;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class IncomingCallNotifier {
    private static final String CHANNEL_ID = "incoming_calls_v04";
    private static final int NOTIFICATION_ID = 2004;

    private IncomingCallNotifier() {}

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "Incoming calls",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Full-screen incoming-call UI for K20 CallFix");
        ch.enableVibration(false);
        ch.setSound(null, null);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    public static void show(Context context, String number) {
        ensureChannel(context);

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;

        Intent ui = new Intent(context, MainActivity.class);
        ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent fullScreen = PendingIntent.getActivity(context, 200, ui, piFlags);

        Intent answerIntent = new Intent(context, CallActionReceiver.class)
                .setAction(CallActionReceiver.ACTION_ANSWER);
        PendingIntent answer = PendingIntent.getBroadcast(context, 201, answerIntent, piFlags);

        Intent rejectIntent = new Intent(context, CallActionReceiver.class)
                .setAction(CallActionReceiver.ACTION_REJECT);
        PendingIntent reject = PendingIntent.getBroadcast(context, 202, rejectIntent, piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        b.setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("Incoming call")
                .setContentText(number == null ? "Unknown number" : number)
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreen)
                .setFullScreenIntent(fullScreen, true)
                .setSound(null)
                .setVibrate(null)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.sym_action_call, "Answer", answer).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "Reject", reject).build());

        nm.notify(NOTIFICATION_ID, b.build());
    }

    public static void cancel(Context context) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }
}
