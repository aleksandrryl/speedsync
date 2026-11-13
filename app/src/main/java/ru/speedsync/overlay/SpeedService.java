package ru.speedsync.overlay;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Фоновый мониторинг скорости.
 *
 * Работает как foreground-сервис: приложение можно свернуть, оно продолжает
 * слушать GPS. Пока скорость ниже 90 км/ч — ничего не показывает.
 * Пересекли 90 — в углу экрана всплывает маленький виджет с обратным отсчётом
 * на 2 минуты. Скорость упала ниже 90 — виджет исчезает, отсчёт сбрасывается.
 */
public class SpeedService extends Service implements LocationListener {

    public static final String CHANNEL_ID = "speedsync_monitor";
    public static final int NOTIFICATION_ID = 101;
    public static final String ACTION_STOP = "ru.speedsync.overlay.STOP";

    /** Порог, км/ч. */
    private static final float THRESHOLD_KMH = 90f;
    /** Длительность обратного отсчёта. */
    private static final long COUNTDOWN_MS = 120_000L;

    private LocationManager locationManager;
    private WindowManager windowManager;

    private View overlayView;
    private TextView speedView;
    private TextView timerView;

    private long countdownEndsAt = 0L;
    private float lastSpeedKmh = 0f;
    private boolean tracking = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateTimer();
            handler.postDelayed(this, 500L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startAsForeground();

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startTracking();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        hideOverlay();
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (Exception ignored) {
                // нет разрешения или GPS уже выключен
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    /* ------------------------------------------------------------------ */
    /* Уведомление и foreground                                             */
    /* ------------------------------------------------------------------ */

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.channel_desc));
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void startAsForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stop = new Intent(this, SpeedService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.drawable.ic_stat_speed)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.notif_stop), stopIntent).build())
                .build();
    }

    /* ------------------------------------------------------------------ */
    /* GPS                                                                 */
    /* ------------------------------------------------------------------ */

    private void startTracking() {
        if (tracking) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        if (locationManager == null) return;

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 500L, 0f, this, Looper.getMainLooper());
            tracking = true;
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) {
                onLocationChanged(last);
            }
        } catch (SecurityException | IllegalArgumentException e) {
            // GPS недоступен — сервис остаётся, но без данных
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        float kmh = location.hasSpeed() ? location.getSpeed() * 3.6f : 0f;
        lastSpeedKmh = kmh;

        if (kmh >= THRESHOLD_KMH) {
            if (overlayView == null) {
                showOverlay();
            } else {
                updateSpeedText();
            }
        } else if (overlayView != null) {
            hideOverlay();
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    @Deprecated
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    /* ------------------------------------------------------------------ */
    /* Виджет поверх других приложений                                     */
    /* ------------------------------------------------------------------ */

    private void showOverlay() {
        if (windowManager == null || !Settings.canDrawOverlays(this)) return;

        countdownEndsAt = SystemClock.elapsedRealtime() + COUNTDOWN_MS;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(16), dp(10), dp(16), dp(10));

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF20B1219);
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), 0x66FFB545);
        card.setBackground(background);
        card.setElevation(dp(8));

        speedView = new TextView(this);
        speedView.setTextColor(0xFF8BA0B2);
        speedView.setTextSize(12);
        speedView.setGravity(Gravity.CENTER);

        timerView = new TextView(this);
        timerView.setTextColor(0xFFFFB545);
        timerView.setTextSize(28);
        timerView.setTypeface(Typeface.DEFAULT_BOLD);
        timerView.setGravity(Gravity.CENTER);
        timerView.setIncludeFontPadding(false);

        card.addView(speedView);
        card.addView(timerView);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SpeedService.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(16);
        params.y = dp(72);

        try {
            windowManager.addView(card, params);
            overlayView = card;
            updateSpeedText();
            updateTimer();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        } catch (Exception e) {
            overlayView = null;
            speedView = null;
            timerView = null;
        }
    }

    private void hideOverlay() {
        handler.removeCallbacks(ticker);
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
                // окно уже убрано системой
            }
        }
        overlayView = null;
        speedView = null;
        timerView = null;
        countdownEndsAt = 0L;
    }

    private void updateSpeedText() {
        if (speedView == null) return;
        speedView.setText(String.format(Locale.US, "%.0f км/ч", lastSpeedKmh));
    }

    private void updateTimer() {
        if (timerView == null) return;

        long left = Math.max(0L, countdownEndsAt - SystemClock.elapsedRealtime());
        long seconds = Math.max(0L, (left + 999L) / 1000L - 1L);
        timerView.setText(String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60));

        updateSpeedText();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
