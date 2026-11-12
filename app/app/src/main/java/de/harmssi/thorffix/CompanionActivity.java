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
        view.setOnHeaderHold(this::lower);
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
        if (connected) {
            mapRequested.clear();
            raise();
        }
    }

    private android.app.Presentation front;

    /**
     * DroidDeck draws its control panel as a Presentation, which lies above every activity on this screen.
     * A Presentation of our own that is opened later (the game is running by then) lies above that panel.
     */
    private void raise() {
        try {
            if (front != null) front.dismiss();
            if (view.getParent() != null) ((android.view.ViewGroup) view.getParent()).removeView(view);
            front = new android.app.Presentation(this, getDisplay());
            // not focusable: keys and the controller stay with the game
            front.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            front.setContentView(view);
            front.show();
        } catch (Exception e) {
            android.util.Log.w("ThorFFIX", "raise: " + e);
            if (view.getParent() == null) setContentView(view);
        }
    }

    /** Back under the launcher's panel (hold the header); closing that panel shows the deck again. */
    private void lower() {
        if (front == null) return;
        front.dismiss();
        front = null;
        if (view.getParent() != null) ((android.view.ViewGroup) view.getParent()).removeView(view);
        setContentView(view);
    }

    @Override protected void onDestroy() {
        if (front != null) front.dismiss();
        super.onDestroy();
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
