package com.fork.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private TextView status;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + .5f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(56), dp(24), dp(24));
        root.setBackgroundColor(0xFFE9EEF2);

        TextView title = new TextView(this);
        title.setText("Fork");
        title.setTextSize(36);
        title.setTextColor(0xFF1B2A33);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setTextColor(0xFF5B7C8D);
        status.setPadding(0, dp(8), 0, dp(24));
        root.addView(status);

        Button start = new Button(this);
        start.setText("Activar burbuja");
        start.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { begin(); }
        });
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("Quitar burbuja");
        stop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, FloatingService.class));
            }
        });
        root.addView(stop);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.setText(Settings.canDrawOverlays(this)
                ? "Permiso para flotar sobre otras apps: activo."
                : "Falta el permiso para flotar sobre otras apps. Toca Activar burbuja.");
    }

    private void begin() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Activa «Mostrar sobre otras apps» para Fork y vuelve.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        ArrayList<String> need = new ArrayList<String>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.POST_NOTIFICATIONS");
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), 7);
            return;
        }
        launch();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        launch();
    }

    private void launch() {
        Intent i = new Intent(this, FloatingService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        finish();
    }
}