package com.workmoney.timer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Foreground user-initiated timer. Persist checkpoints once per second. */
public final class TimingService extends Service {
    public static final String ACTION_START = "com.workmoney.timer.START";
    public static final String ACTION_PAUSE = "com.workmoney.timer.PAUSE";
    private static final String CHANNEL = "earning_timer";
    private static final int NOTIFICATION_ID = 101;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WorkStore store;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (store == null) return;
            if (!store.sync()) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return;
            }
            publish();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        store = new WorkStore(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "工作计时", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_PAUSE.equals(action)) {
            store.pause();
            handler.removeCallbacks(ticker);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) store.start();
        if (!store.isRunning()) {
            handler.removeCallbacks(ticker);
            stopSelf();
            return START_NOT_STICKY;
        }
        // Android requires prompt foreground promotion after startForegroundService().
        startForeground(NOTIFICATION_ID, notification());
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        return START_STICKY;
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String earned = store.amount(store.todayMillis()).toPlainString();
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("正在努力赚钱 · ¥" + earned)
            .setContentText("今天已工作 " + SalaryMath.hoursClock(store.todayMillis()) + " · 点击查看")
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build();
    }

    private void publish() {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification());
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
