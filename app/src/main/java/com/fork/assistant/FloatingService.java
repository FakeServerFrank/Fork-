package com.fork.assistant;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

public class FloatingService extends Service {
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private TextView bubble;
    private WindowManager.LayoutParams bubbleLp;
    private FrameLayout panelRoot;
    private WindowManager.LayoutParams panelLp;
    private WebView web;
    private boolean panelOpen = false;
    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private DisplayMetrics dm;

    private int dp(int v) {
        return (int) (v * dm.density + .5f);
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int id) { return START_STICKY; }

    @Override
    public void onCreate() {
        super.onCreate();
        dm = getResources().getDisplayMetrics();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForeground(1, buildNotification());
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("es", "MX"));
            }
        });
        buildPanel();
        buildBubble();
    }

    private Notification buildNotification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("fork", "Fork", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
            b = new Notification.Builder(this, "fork");
        } else {
            b = new Notification.Builder(this);
        }
        return b.setContentTitle("Fork activo")
                .setContentText("Toca la burbuja para usarlo.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();
    }

    private TextView chip(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(0xFF1B2A33);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setOnClickListener(l);
        return t;
    }

    private void buildPanel() {
        int w = (int) (dm.widthPixels * 0.94f);
        int h = (int) (dm.heightPixels * 0.55f);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFE9EEF2);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0xFFC9D4DB);
        panel.setBackground(bg);
        panel.setClipToOutline(true);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), 0, dp(6), 0);
        TextView title = new TextView(this);
        title.setText("Fork");
        title.setTextSize(17);
        title.setTextColor(0xFF1B2A33);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(chip("Bajar", new View.OnClickListener() {
            public void onClick(View v) { hidePanel(); }
        }));
        head.addView(chip("Cerrar", new View.OnClickListener() {
            public void onClick(View v) { stopSelf(); }
        }));
        panel.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "ForkNative");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith("file:")) return false;
                openUrl(url);
                return true;
            }
        });
        web.loadUrl("file:///android_asset/fork.html");
        panel.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        panelRoot = new FrameLayout(this);
        panelRoot.addView(panel, new FrameLayout.LayoutParams(w, h));
        panelRoot.setVisibility(View.GONE);

        panelLp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        panelLp.y = dp(28);
        panelLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
        wm.addView(panelRoot, panelLp);
    }
        private void buildBubble() {
        final int size = dp(56);
        bubble = new TextView(this);
        bubble.setText("F");
        bubble.setTextSize(24);
        bubble.setTextColor(0xFFFFFFFF);
        bubble.setGravity(Gravity.CENTER);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(0xFF3B3FD8);
        oval.setStroke(dp(2), 0xFFFFFFFF);
        bubble.setBackground(oval);

        bubbleLp = new WindowManager.LayoutParams(size, size, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = dm.widthPixels - size - dp(8);
        bubbleLp.y = (int) (dm.heightPixels * 0.4f);

        bubble.setOnTouchListener(new View.OnTouchListener() {
                        private int startX, startY;
            private float touchX, touchY;
            private boolean moved;

            public boolean onTouch(View v, MotionEvent e) {
            float dx, dy;
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = bubbleLp.x;
                        startY = bubbleLp.y;
                        touchX = e.getRawX();
                        touchY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        dx = e.getRawX() - touchX;
                        dy = e.getRawY() - touchY;
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                        if (moved) {
                            bubbleLp.x = startX + (int) dx;
                            bubbleLp.y = startY + (int) dy;
                            wm.updateViewLayout(bubble, bubbleLp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            togglePanel();
                        } else {
                            bubbleLp.x = (bubbleLp.x + size / 2 < dm.widthPixels / 2)
                                    ? dp(8) : dm.widthPixels - size - dp(8);
                            wm.updateViewLayout(bubble, bubbleLp);
                        }
                        return true;
                }
                return false;
            }
        });
        wm.addView(bubble, bubbleLp);
    }

    private void togglePanel() {
        if (panelOpen) hidePanel(); else showPanel();
    }

    private void showPanel() {
        panelOpen = true;
        panelLp.flags &= ~(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        panelRoot.setVisibility(View.VISIBLE);
        wm.updateViewLayout(panelRoot, panelLp);
        web.requestFocus();
    }

    private void hidePanel() {
        panelOpen = false;
        panelLp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        panelRoot.setVisibility(View.GONE);
        wm.updateViewLayout(panelRoot, panelLp);
        web.resumeTimers();
    }

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            hidePanel();
        } catch (ActivityNotFoundException e) {
            js("window.onNativeError('No encontré una app para abrir eso.')");
        }
    }

    private void js(String code) {
        if (web != null) web.evaluateJavascript(code, null);
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            js("window.onNativeError('Falta el permiso del micrófono. Abre la app Fork y acéptalo.')");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            js("window.onNativeError('Este teléfono no tiene reconocimiento de voz disponible.')");
            return;
        }
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle p) { js("window.onNativeState(true)"); }
                public void onBeginningOfSpeech() {}
                public void onRmsChanged(float v) {}
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() { js("window.onNativeState(false)"); }
                public void onError(int code) {
                    js("window.onNativeState(false)");
                    if (code != SpeechRecognizer.ERROR_NO_MATCH && code != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        js("window.onNativeError('No pude escuchar (código " + code + ").')");
                    }
                }
                public void onResults(Bundle r) {
                    js("window.onNativeState(false)");
                    ArrayList<String> list = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) {
                        js("window.onNativeSpeech(" + JSONObject.quote(list.get(0)) + ")");
                    }
                }
                public void onPartialResults(Bundle b) {}
                public void onEvent(int t, Bundle b) {}
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX");
        sr.startListening(i);
    }

    private class Bridge {
        @JavascriptInterface
        public void speak(final String t) {
            main.post(new Runnable() {
                public void run() {
                    if (tts != null) tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "fork");
                }
            });
        }

        @JavascriptInterface
        public void listen() {
            main.post(new Runnable() { public void run() { startListening(); } });
        }

        @JavascriptInterface
        public void stop() {
            main.post(new Runnable() {
                public void run() { if (sr != null) sr.stopListening(); }
            });
        }

        @JavascriptInterface
        public void open(final String url) {
            main.post(new Runnable() { public void run() { openUrl(url); } });
        }
    }

    @Override
    public void onDestroy() {
        try { wm.removeView(bubble); } catch (Exception e) {}
        try { wm.removeView(panelRoot); } catch (Exception e) {}
        if (web != null) web.destroy();
        if (sr != null) sr.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
