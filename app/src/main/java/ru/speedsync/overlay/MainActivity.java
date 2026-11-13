package ru.speedsync.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Экран настройки: разрешения и кнопки запуска/остановки фонового мониторинга.
 * Само приложение после запуска можно свернуть — виджет живёт в сервисе.
 */
public class MainActivity extends Activity {

    private static final int REQ_LOCATION = 1;
    private static final int REQ_NOTIFICATIONS = 2;

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF070B0F);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(0xFFEAF2F8);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.subtitle);
        subtitle.setTextColor(0xFF8BA0B2);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(8), 0, dp(20));
        root.addView(subtitle);

        statusView = new TextView(this);
        statusView.setTextColor(0xFFFFB545);
        statusView.setTextSize(14);
        statusView.setLineSpacing(dp(4), 1f);
        statusView.setPadding(0, 0, 0, dp(18));
        root.addView(statusView);

        addButton(root, R.string.btn_location, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestLocation();
            }
        });

        addButton(root, R.string.btn_overlay, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOverlaySettings();
            }
        });

        addButton(root, R.string.btn_notifications, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestNotifications();
            }
        });

        addButton(root, R.string.btn_start, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startMonitoring();
            }
        });

        addButton(root, R.string.btn_stop, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopMonitoring();
            }
        });

        addButton(root, R.string.btn_battery, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBatterySettings();
            }
        });

        TextView hint = new TextView(this);
        hint.setText(R.string.hint);
        hint.setTextColor(0xFF8BA0B2);
        hint.setTextSize(13);
        hint.setLineSpacing(dp(4), 1f);
        hint.setPadding(0, dp(18), 0, 0);
        root.addView(hint);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateStatus();
        if (requestCode == REQ_LOCATION && !hasLocation() && grantResults.length > 0) {
            Toast.makeText(this, R.string.need_location, Toast.LENGTH_LONG).show();
        }
    }

    private void addButton(LinearLayout parent, int textRes, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(textRes);
        button.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        parent.addView(button);
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasOverlay() {
        return Settings.canDrawOverlays(this);
    }

    private boolean hasNotifications() {
        if (Build.VERSION.SDK_INT < 33) return true;
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updateStatus() {
        statusView.setText(getString(R.string.status_fmt,
                hasLocation() ? "разрешено" : "не разрешено",
                hasOverlay() ? "разрешено" : "не разрешено",
                hasNotifications() ? "разрешено" : "не разрешено"));
    }

    private void requestLocation() {
        if (hasLocation()) {
            Toast.makeText(this, R.string.location_already, Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, R.string.notif_not_needed, Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
    }

    private void openOverlaySettings() {
        if (hasOverlay()) {
            Toast.makeText(this, R.string.overlay_already, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, R.string.battery_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void startMonitoring() {
        if (!hasLocation()) {
            Toast.makeText(this, R.string.need_location, Toast.LENGTH_LONG).show();
            requestLocation();
            return;
        }
        if (!hasOverlay()) {
            Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show();
            openOverlaySettings();
            return;
        }
        Intent service = new Intent(this, SpeedService.class);
        startForegroundService(service);
        Toast.makeText(this, R.string.started, Toast.LENGTH_SHORT).show();
    }

    private void stopMonitoring() {
        stopService(new Intent(this, SpeedService.class));
        Toast.makeText(this, R.string.stopped, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
