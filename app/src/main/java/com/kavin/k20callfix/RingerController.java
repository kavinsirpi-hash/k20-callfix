package com.kavin.k20callfix;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public final class RingerController {
    private static ToneGenerator tone;
    private static Vibrator vibrator;
    private static boolean running;
    private static int oldAlarmVolume = -1;
    private static Context appContext;

    private RingerController() {}

    public static synchronized void start(Context context) {
        if (running) return;
        running = true;
        appContext = context.getApplicationContext();

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            try {
                oldAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM);
                int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                int target = Math.max(oldAlarmVolume, Math.max(1, (int)Math.ceil(max * 0.75)));
                am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);

                tone = new ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME);
                tone.startTone(ToneGenerator.TONE_SUP_RINGTONE);
            } catch (Throwable ignored) {
                releaseTone();
            }
        }

        try {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] p = new long[]{0,700,450,700,2200};
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createWaveform(p,0));
                } else {
                    vibrator.vibrate(p,0);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static synchronized void stop() {
        running = false;
        releaseTone();

        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Throwable ignored) {}
            vibrator = null;
        }

        if (appContext != null && oldAlarmVolume >= 0) {
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                try { am.setStreamVolume(AudioManager.STREAM_ALARM, oldAlarmVolume, 0); }
                catch (Throwable ignored) {}
            }
        }

        oldAlarmVolume = -1;
        appContext = null;
    }

    private static void releaseTone() {
        if (tone != null) {
            try { tone.stopTone(); } catch (Throwable ignored) {}
            try { tone.release(); } catch (Throwable ignored) {}
            tone = null;
        }
    }
}
