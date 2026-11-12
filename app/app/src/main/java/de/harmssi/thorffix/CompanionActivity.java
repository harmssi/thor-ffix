package de.harmssi.thorffix;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * Begleitbildschirm (unten): zeigt Gruppe, Ort, Gil/Zeit, Kampf, Weltkarte und Inventar live aus dem Spiel.
 * Das Fenster nimmt keinen Tastatur-/Controller-Fokus an, damit der Controller beim Spiel oben bleibt.
 */
public class CompanionActivity extends Activity implements StatusClient.Listener {
    static final String HOST = "127.0.0.1";
    static final int PORT = 8766;

    private DashboardView view;
    private StatusClient client;
    private final java.util.Map<String, Long> mapRequested = new java.util.HashMap<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Focusable on purpose: when this screen holds the focus and the window cannot take keys, Android reports
        // "not responding" at the first controller key. The focus is handed back to the game screen instead.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new DashboardView(this);
        view.setOnTouchFinished(this::returnFocusToGame);
        view.setGame(path -> client.fetchSprite(path), name -> client.booster(name));
        setContentView(view);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        client = new StatusClient(HOST, PORT, this);
    }

    @Override protected void onStart() {
        super.onStart();
        client.start();
    }

    private long focusBackAt;

    private void focusBack() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - focusBackAt < 700) return;
        focusBackAt = now;
        returnFocusToGame();
    }

    @Override public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) view.postDelayed(this::focusBack, 400);
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        focusBack();
        return true;
    }

    @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent e) {
        if ((e.getSource() & android.view.InputDevice.SOURCE_CLASS_JOYSTICK) != 0) focusBack();
        return true;
    }

    @Override protected void onStop() {
        client.stop();
        super.onStop();
    }

    @Override public void onState(GameState s) {
        view.setState(s);
        if (s.inGame) {
            // Karte schon vorab laden, damit der Reiter „Karte“ auch im Feld etwas zeigt
            String key = s.world != null && s.world.naviMap == 0 ? "mist" : "world";
            Long last = mapRequested.get(key);
            long now = android.os.SystemClock.uptimeMillis();
            // einmal laden; nach einem Fehlschlag frühestens nach 15 s erneut versuchen
            if (!view.hasMap(key) && (last == null || now - last > 15000)) {
                mapRequested.put(key, now);
                client.fetchMap(key);
            }
        }
    }

    @Override public void onConnection(boolean connected) {
        view.setConnected(connected);
        if (connected) mapRequested.clear();
    }

    /** Nach einer Berührung den Eingabefokus (Controller) wieder auf den oberen Bildschirm legen. */
    private void returnFocusToGame() {
        try {
            android.content.Intent i = new android.content.Intent(this, FocusTopActivity.class);
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i, android.app.ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
        } catch (Exception e) {
            android.util.Log.w("ThorFFIX", "focus: " + e);
        }
    }

    @Override public void onSprite(String path, Bitmap sprite) {
        view.setSprite(path, sprite);
    }

    @Override public void onMap(String key, Bitmap map) {
        view.setMap(key, map);
    }
}
