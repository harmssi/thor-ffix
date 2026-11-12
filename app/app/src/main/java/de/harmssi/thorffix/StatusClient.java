package de.harmssi.thorffix;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Verbindet sich mit dem Memoria-Statusserver (Server-Sent Events auf 127.0.0.1) und verbindet sich
 * nach Abbrüchen selbständig neu. Alle Rückrufe kommen auf dem UI-Thread an.
 */
final class StatusClient {
    interface Listener {
        void onState(GameState state);
        void onConnection(boolean connected);
        void onMap(String key, Bitmap map);
        void onSprite(String path, Bitmap sprite);
    }

    private static final String TAG = "ThorFFIX";
    private final String base;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile HttpURLConnection current;
    private Thread thread;

    StatusClient(String host, int port, Listener listener) {
        this.base = "http://" + host + ":" + port;
        this.listener = listener;
    }

    void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "ffix-sse");
        thread.start();
    }

    void stop() {
        running = false;
        HttpURLConnection c = current;
        if (c != null) c.disconnect();
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        boolean wasConnected = false;
        while (running) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(base + "/events").openConnection();
                current = c;
                c.setConnectTimeout(1500);
                c.setReadTimeout(12000); // Server sendet spätestens alle 5 s ein keep-alive
                c.setRequestProperty("Accept", "text/event-stream");
                c.setUseCaches(false);
                if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
                BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8), 1 << 16);
                if (!wasConnected) { wasConnected = true; post(() -> listener.onConnection(true)); }
                StringBuilder data = new StringBuilder();
                String line;
                while (running && (line = in.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        data.append(line.substring(line.length() > 5 && line.charAt(5) == ' ' ? 6 : 5));
                    } else if (line.isEmpty() && data.length() > 0) {
                        String json = data.toString();
                        data.setLength(0);
                        try {
                            GameState s = GameState.parse(json);
                            post(() -> listener.onState(s));
                        } catch (Exception e) {
                            Log.w(TAG, "bad snapshot: " + e);
                        }
                    }
                }
            } catch (Exception e) {
                if (running) Log.d(TAG, "not connected: " + e.getMessage());
            } finally {
                if (c != null) c.disconnect();
                current = null;
            }
            if (wasConnected) { wasConnected = false; post(() -> listener.onConnection(false)); }
            sleep(1000);
        }
    }

    /** Lädt ein Kartenbild, das der Server zur Laufzeit aus den Spieldaten des Nutzers erzeugt. */
    void fetchMap(String key) {
        new Thread(() -> {
            Bitmap bmp = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(base + "/map/" + key + ".png").openConnection();
                c.setConnectTimeout(1500);
                c.setReadTimeout(10000);
                if (c.getResponseCode() == 200) {
                    try (InputStream in = c.getInputStream()) { bmp = BitmapFactory.decodeStream(in); }
                }
            } catch (Exception e) {
                Log.d(TAG, "map " + key + ": " + e.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
            Bitmap result = bmp;
            post(() -> listener.onMap(key, result));
        }, "ffix-map").start();
    }

    /** One of the game's own UI graphics (window skin, portraits, icons), cut from its atlases by the server. */
    void fetchSprite(String path) {
        new Thread(() -> {
            Bitmap bmp = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(base + "/ui/" + path + ".png").openConnection();
                c.setConnectTimeout(1500);
                c.setReadTimeout(10000);
                if (c.getResponseCode() == 200) {
                    try (InputStream in = c.getInputStream()) { bmp = BitmapFactory.decodeStream(in); }
                }
            } catch (Exception e) {
                Log.d(TAG, "sprite " + path + ": " + e.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
            Bitmap result = bmp;
            post(() -> listener.onSprite(path, result));
        }, "ffix-sprite").start();
    }

    /** Presses one of the game's booster buttons (speed, assist, dmg, noenc); the game applies its own rules. */
    void booster(String name) {
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(base + "/cmd/booster/" + name).openConnection();
                c.setConnectTimeout(1500);
                c.setReadTimeout(3000);
                c.getResponseCode();
            } catch (Exception e) {
                Log.d(TAG, "booster " + name + ": " + e.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
        }, "ffix-cmd").start();
    }

    private void post(Runnable r) { ui.post(r); }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
