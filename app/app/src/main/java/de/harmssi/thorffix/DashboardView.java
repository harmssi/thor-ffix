package de.harmssi.thorffix;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Die gesamte Oberfläche des Begleiters als eine gezeichnete Ansicht (Referenzmaß 1240 x 1080,
 * skaliert auf die tatsächliche Größe). Eigene Gestaltung: Nachtblau, Gold, Kristall-Akzente.
 */
final class DashboardView extends View {
    // Farben
    private static final int BG_TOP = 0xFF070A16, BG_BOTTOM = 0xFF141C3C;
    private static final int PANEL = 0xD0141B36, PANEL_EDGE = 0x66D9B45A;
    private static final int GOLD = 0xFFD9B45A, CREAM = 0xFFF2E8D0, MUTED = 0xFF8C93B0;
    private static final int HP = 0xFF5BD08A, HP_MID = 0xFFE8C14E, HP_LOW = 0xFFE0556A;
    private static final int MPC = 0xFF5AA9F0, ATBC = 0xFFF2D25C, TRANCE = 0xFFF06BC8, ENEMY = 0xFFE07A5A;
    private static final int TRACK = 0xFF0B1022;

    private static final float REF_W = 1240f, REF_H = 1080f;
    private static final int TAB_AUTO = 0, TAB_AP = 1, TAB_MAP = 2, TAB_ITEMS = 3, TAB_KEY = 4, TAB_EXTRA = 5;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface serif = Typeface.create(Typeface.SERIF, Typeface.BOLD);
    private final Typeface sans = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    private final Typeface sansBold = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private final RectF r = new RectF();
    private final Rect src = new Rect();
    private final float[] stars = new float[180];

    private GameState state;
    private GameState lastWorldState;      // für „zuletzt bekannte Position“ im Feld
    private boolean connected;
    private int tab = TAB_AUTO;
    private float scroll, maxScroll, lastTouchY, downX, downY;
    private boolean dragging;
    private final Map<String, Bitmap> maps = new HashMap<>();
    private boolean mapFailed;
    private final Map<String, float[]> shown = new HashMap<>(); // geglättete Balkenwerte
    private long lastFrame;
    private float s = 1f; // Skalierung Referenz -> Pixel

    private Runnable onTouchFinished;
    void setOnTouchFinished(Runnable r) { onTouchFinished = r; }
    private Runnable onHeaderHold;
    void setOnHeaderHold(Runnable r) { onHeaderHold = r; }

    private final RectF[] tabRects = { new RectF(), new RectF(), new RectF(), new RectF(), new RectF(), new RectF() };
    private final RectF[] boosterRects = { new RectF(), new RectF(), new RectF(), new RectF() };
    private static final String[] BOOSTERS = { "speed", "noenc", "assist", "dmg" };

    // ---- the game's own look: window skin, portraits and icons are cut from the player's game at runtime ----
    interface SpriteSource { void request(String path); }
    interface Commands { void booster(String name); }
    private SpriteSource spriteSource;
    private Commands commands;
    private final Map<String, Bitmap> sprites = new HashMap<>();
    private final Map<String, Long> asked = new HashMap<>();
    private BitmapShader skin;
    private Bitmap skinOf;

    void setGame(SpriteSource s, Commands c) { spriteSource = s; commands = c; }

    void setSprite(String path, Bitmap b) {
        if (b != null) { sprites.put(path, b); invalidate(); }
    }

    private Bitmap spr(String path) {
        Bitmap b = sprites.get(path);
        if (b == null && connected && spriteSource != null && !path.endsWith("/")) {
            long now = SystemClock.uptimeMillis();
            Long t = asked.get(path);
            if (t == null || now - t > 8000) { asked.put(path, now); spriteSource.request(path); }
        }
        return b;
    }

    private void sprite(Canvas c, Bitmap b, float l, float t, float rr, float bb) {
        src.set(0, 0, b.getWidth(), b.getHeight());
        r.set(l, t, rr, bb);
        p.setColor(0xFFFFFFFF); p.setFilterBitmap(true);
        c.drawBitmap(b, src, r, p);
    }

    /** A window as in the game's menus: its paper texture, tiled, inside its silver frame. Without the game: our own panel. */
    private void window(Canvas c, float l, float t, float rr, float b) {
        Bitmap bg = spr("window/dialog_bg"), fr = spr("window/dialog_frame_info");
        if (bg == null || fr == null) {
            r.set(l, t, rr, b);
            p.setColor(PANEL); c.drawRoundRect(r, 18, 18, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(PANEL_EDGE); c.drawRoundRect(r, 18, 18, p);
            p.setStyle(Paint.Style.FILL);
            return;
        }
        if (skinOf != bg) { skinOf = bg; skin = new BitmapShader(bg, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT); }
        p.setColor(0xFFFFFFFF); p.setShader(skin);
        c.drawRect(l + 8, t + 8, rr - 8, b - 8, p);
        p.setShader(null);
        // nine-slice: corners as they are, edges stretched, the middle stays open
        int w = fr.getWidth(), h = fr.getHeight(), k = Math.min(w, h) * 2 / 5;
        float d = Math.min(26, Math.min(rr - l, b - t) / 2f);
        float[] dx = { l, l + d, rr - d, rr }, dy = { t, t + d, b - d, b };
        int[] sx = { 0, k, w - k, w }, sy = { 0, k, h - k, h };
        p.setFilterBitmap(true);
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            if (i == 1 && j == 1) continue;
            src.set(sx[i], sy[j], sx[i + 1], sy[j + 1]);
            r.set(dx[i], dy[j], dx[i + 1], dy[j + 1]);
            c.drawBitmap(fr, src, r, p);
        }
    }

    private boolean gameLook() { return sprites.containsKey("window/dialog_bg") && sprites.containsKey("window/dialog_frame_info"); }

    DashboardView(Context c) {
        super(c);
        Random rnd = new Random(9);
        for (int i = 0; i < stars.length; i += 3) {
            stars[i] = rnd.nextFloat(); stars[i + 1] = rnd.nextFloat(); stars[i + 2] = 0.3f + rnd.nextFloat() * 1.4f;
        }
    }

    // ------------------------------------------------------------------ Daten

    private String lang = "";
    private android.content.res.Resources localized;

    /** Beschriftung folgt der Spielsprache, nicht der des Geräts (alles außer Deutsch → Englisch). */
    @Override public android.content.res.Resources getResources() {
        return localized != null ? localized : super.getResources();
    }

    void setState(GameState st) {
        if (!st.lang.isEmpty() && !st.lang.equals(lang)) {
            lang = st.lang;
            android.content.res.Configuration c = new android.content.res.Configuration(super.getResources().getConfiguration());
            c.setLocale("GR".equals(lang) ? Locale.GERMAN : Locale.ENGLISH);
            localized = getContext().createConfigurationContext(c).getResources();
        }
        if (st.world != null) lastWorldState = st;
        state = st;
        invalidate();
    }

    void setConnected(boolean c) {
        connected = c;
        if (!c) { state = null; asked.clear(); }
        invalidate();
    }

    void setMap(String key, Bitmap bmp) {
        if (bmp != null) { maps.put(key, bmp); mapFailed = false; } else mapFailed = true;
        invalidate();
    }

    boolean hasMap(String key) { return maps.containsKey(key); }

    // ------------------------------------------------------------------ Zeichnen

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        s = Math.min(getWidth() / REF_W, getHeight() / REF_H);
        c.save();
        c.translate((getWidth() - REF_W * s) / 2f, (getHeight() - REF_H * s) / 2f);
        c.scale(s, s);
        boolean animating = drawBackground(c, now);

        GameState st = state;
        if (!connected || st == null) {
            animating |= drawWaiting(c, now, getResources().getString(R.string.waiting), getResources().getString(R.string.waiting_hint));
        } else if (!st.inGame) {
            animating |= drawWaiting(c, now, getResources().getString(R.string.title_screen), getResources().getString(R.string.title_hint));
            drawFps(c, st);
        } else {
            drawHeader(c, st);
            animating |= drawParty(c, st, dt);
            drawTabs(c, st);
            animating |= drawSidePanel(c, st, dt, now);
        }
        c.restore();
        if (animating) postInvalidateOnAnimation();
    }

    private boolean drawBackground(Canvas c, long now) {
        text.setShadowLayer(2.5f, 2, 2, 0xE0000000);
        if (gameLook() && state != null && connected && state.inGame) {
            p.setColor(0xFF000000); c.drawRect(0, 0, REF_W, REF_H, p);
            return false;
        }
        p.setShader(new LinearGradient(0, 0, 0, REF_H, BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, REF_W, REF_H, p);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        for (int i = 0; i < stars.length; i += 3) {
            float tw = 0.5f + 0.5f * (float) Math.sin(now / 900.0 + i);
            p.setAlpha((int) (40 + 70 * tw));
            c.drawCircle(stars[i] * REF_W, stars[i + 1] * REF_H * 0.9f, stars[i + 2], p);
        }
        p.setAlpha(255);
        // feiner Goldrahmen
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(0x55D9B45A);
        c.drawRoundRect(10, 10, REF_W - 10, REF_H - 10, 18, 18, p);
        p.setStyle(Paint.Style.FILL);
        return state == null || !connected; // Sterne funkeln nur im Wartebildschirm dauerhaft
    }

    private boolean drawWaiting(Canvas c, long now, String title, String hint) {
        float cx = REF_W / 2f, cy = REF_H / 2f - 60;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 600.0);
        drawCrystal(c, cx, cy, 90, pulse);
        text.setTypeface(serif);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(54);
        text.setColor(CREAM);
        c.drawText(title, cx, cy + 170, text);
        text.setTypeface(sans);
        text.setTextSize(26);
        text.setColor(MUTED);
        drawWrapped(c, hint, cx, cy + 220, 820, 34);
        text.setTextAlign(Paint.Align.LEFT);
        return true;
    }

    private void drawCrystal(Canvas c, float cx, float cy, float h, float glow) {
        p.setShader(new RadialGradient(cx, cy, h * 1.8f, Color.argb((int) (60 + 80 * glow), 120, 190, 255), 0, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, h * 1.8f, p);
        p.setShader(null);
        Path d = new Path();
        d.moveTo(cx, cy - h); d.lineTo(cx + h * 0.5f, cy - h * 0.05f); d.lineTo(cx, cy + h); d.lineTo(cx - h * 0.5f, cy - h * 0.05f); d.close();
        p.setShader(new LinearGradient(cx - h * 0.5f, cy - h, cx + h * 0.5f, cy + h, 0xFFCDEBFF, 0xFF3F7FD8, Shader.TileMode.CLAMP));
        c.drawPath(d, p);
        p.setShader(null);
        Path f = new Path();
        f.moveTo(cx, cy - h); f.lineTo(cx + h * 0.18f, cy - h * 0.05f); f.lineTo(cx, cy + h); f.lineTo(cx - h * 0.18f, cy - h * 0.05f); f.close();
        p.setColor(0x66FFFFFF);
        c.drawPath(f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(GOLD);
        c.drawPath(d, p);
        c.drawOval(cx - h * 0.95f, cy + h * 0.55f, cx + h * 0.95f, cy + h * 0.85f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawHeader(Canvas c, GameState st) {
        if (gameLook()) window(c, 12, 10, REF_W - 12, 132);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(serif);
        text.setColor(CREAM);
        String loc = TextUtils.isEmpty(st.location) ? sceneLabel(st.scene) : st.location;
        text.setTextSize(fitSize(loc, 46, 26, 690, serif));
        c.drawText(loc, 36, 70, text);
        // Szenen-Chip
        String chip = sceneLabel(st.scene);
        text.setTypeface(sansBold);
        text.setTextSize(22);
        float w = text.measureText(chip) + 28;
        int chipColor = "battle".equals(st.scene) ? ENEMY : "world".equals(st.scene) ? MPC : GOLD;
        r.set(36, 86, 36 + w, 118);
        p.setColor(0x33000000 | (chipColor & 0x00FFFFFF)); c.drawRoundRect(r, 16, 16, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(chipColor); c.drawRoundRect(r, 16, 16, p);
        p.setStyle(Paint.Style.FILL);
        text.setColor(chipColor);
        c.drawText(chip, 50, 110, text);
        if (st.scenario >= 0) {
            text.setTypeface(sans); text.setColor(MUTED); text.setTextSize(20);
            c.drawText(getResources().getString(R.string.progress) + " " + st.scenario, 36 + w + 18, 110, text);
        }
        // rechts: Gil, Zeit, FPS
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTypeface(sans); text.setTextSize(20); text.setColor(MUTED);
        c.drawText(getResources().getString(R.string.gil), 1010, 44, text);
        c.drawText(getResources().getString(R.string.time), 1200, 44, text);
        text.setTypeface(sansBold); text.setTextSize(36); text.setColor(GOLD);
        c.drawText(String.format(Locale.ROOT, "%,d", st.gil).replace(',', '.'), 1010, 84, text);
        text.setColor(CREAM);
        c.drawText(formatTime(st.playTime), 1200, 84, text);
        drawFps(c, st);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private void drawFps(Canvas c, GameState st) {
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTypeface(sans); text.setTextSize(19);
        text.setColor(st.fps >= 55 ? 0xFF7FD69B : st.fps >= 28 ? HP_MID : HP_LOW);
        c.drawText(String.format(Locale.ROOT, "%.0f %s", st.fps, getResources().getString(R.string.fps)), 1200, 114, text);
        p.setColor(connected ? 0xFF7FD69B : HP_LOW);
        c.drawCircle(1120 - 0, 108, 6, p);
        text.setTextAlign(Paint.Align.LEFT);
    }

    // ------------------------------------------------------------------ Gruppe

    private boolean drawParty(Canvas c, GameState st, float dt) {
        boolean anim = false;
        List<GameState.Member> party = st.party;
        float top = 140, gap = 12, h = (REF_H - 24 - top - gap * 3) / 4f;
        for (int i = 0; i < 4; i++) {
            float y = top + i * (h + gap);
            if (i < party.size()) anim |= drawMember(c, party.get(i), i, 24, y, 640, y + h, dt, st.inBattle);
            else {
                r.set(24, y, 640, y + h);
                p.setColor(0x40141B36); c.drawRoundRect(r, 18, 18, p);
            }
        }
        return anim;
    }

    private boolean drawMember(Canvas c, GameState.Member m, int slot, float l, float t, float rr, float b, float dt, boolean battle) {
        boolean ko = m.hp == 0 || m.status.contains("Death");
        float[] v = smooth("m" + slot + m.id, dt, (float) m.hp / m.maxHp, (float) m.mp / m.maxMp, (float) m.atb / m.maxAtb, m.trance / 255f);
        boolean anim = v[4] > 0;
        if (gameLook()) {
            window(c, l, t, rr, b);
            if (ko) { r.set(l + 10, t + 10, rr - 10, b - 10); p.setColor(0x70300810); c.drawRect(r, p); }
            if (m.inTrance) {
                r.set(l + 6, t + 6, rr - 6, b - 6);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(TRANCE); c.drawRoundRect(r, 14, 14, p); p.setStyle(Paint.Style.FILL);
            }
        } else {
        r.set(l, t, rr, b);
        p.setColor(ko ? 0xD02A1420 : PANEL);
        c.drawRoundRect(r, 18, 18, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(m.inTrance ? 4 : 2);
        p.setColor(m.inTrance ? TRANCE : ko ? 0x99E0556A : PANEL_EDGE);
        if (m.inTrance) p.setMaskFilter(new BlurMaskFilter(6, BlurMaskFilter.Blur.SOLID));
        c.drawRoundRect(r, 18, 18, p);
        p.setMaskFilter(null); p.setStyle(Paint.Style.FILL);
        }
        Bitmap face = m.face.isEmpty() ? null : spr("face/" + m.face);
        if (face != null) {
            float fh = b - t - 36, fw = fh * face.getWidth() / face.getHeight();
            if (ko) p.setAlpha(110);
            sprite(c, face, l + 20, t + 18, l + 20 + fw, t + 18 + fh);
            p.setAlpha(255);
            l += fw + 14;
        }

        // Name + Stufe
        text.setTypeface(serif); text.setTextSize(36); text.setColor(ko ? MUTED : CREAM);
        c.drawText(m.name, l + 22, t + 46, text);
        text.setTypeface(sans); text.setTextSize(24); text.setColor(GOLD); text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(getResources().getString(R.string.lv) + " " + m.lv, rr - 22, t + 44, text);
        text.setTextAlign(Paint.Align.LEFT);

        float barL = l + 22, barR = rr - 22, y = t + 64;
        float hpFrac = v[0];
        int hpColor = hpFrac < 0.25f ? HP_LOW : hpFrac < 0.5f ? HP_MID : HP;
        y = bar(c, getResources().getString(R.string.hp), m.hp + " / " + m.maxHp, barL, y, barR, 20, hpFrac, hpColor, false);
        y = bar(c, getResources().getString(R.string.mp), m.mp + " / " + m.maxMp, barL, y, barR, 14, v[1], MPC, false);
        float half = (barR - barL - 16) / 2f;
        boolean showTrance = m.hasTrance || m.trance > 0;
        if (battle && m.inBattle) {
            boolean ready = m.atb >= m.maxAtb && m.canAct;
            bar(c, getResources().getString(R.string.atb), ready ? getResources().getString(R.string.ready) : "",
                    barL, y, showTrance ? barL + half : barR, 12, v[2], ATBC, ready);
        }
        if (showTrance) {
            float tl = battle && m.inBattle ? barL + half + 16 : barL;
            bar(c, getResources().getString(R.string.trance), m.inTrance ? "!!" : "", tl, y, barR, 12, v[3], TRANCE, m.inTrance || m.trance >= 255);
        }
        // Statuschips in der Namenszeile (zwischen Name und Stufe)
        text.setTypeface(serif); text.setTextSize(36);
        float chipsL = l + 22 + text.measureText(m.name) + 18;
        text.setTypeface(sans); text.setTextSize(24);
        float chipsR = rr - 22 - text.measureText(getResources().getString(R.string.lv) + " " + m.lv) - 14;
        chips(c, m.status, chipsL, t + 42, chipsR);
        return anim;
    }

    /** Beschrifteter Balken; gibt die y-Position für den nächsten zurück. */
    private float bar(Canvas c, String label, String value, float l, float y, float rr, float h, float frac, int color, boolean glow) {
        frac = Math.max(0f, Math.min(1f, frac));
        text.setTypeface(sansBold); text.setTextSize(19); text.setColor(MUTED);
        c.drawText(label, l, y + 18, text);
        if (!value.isEmpty()) {
            text.setTypeface(sans); text.setTextSize(21); text.setColor(glow ? color : CREAM); text.setTextAlign(Paint.Align.RIGHT);
            c.drawText(value, rr, y + 18, text);
            text.setTextAlign(Paint.Align.LEFT);
        }
        float by = y + 26;
        r.set(l, by, rr, by + h);
        p.setColor(TRACK); c.drawRoundRect(r, h / 2, h / 2, p);
        if (frac > 0) {
            r.set(l, by, l + (rr - l) * frac, by + h);
            p.setShader(new LinearGradient(l, by, l, by + h, lighten(color), color, Shader.TileMode.CLAMP));
            if (glow) p.setMaskFilter(new BlurMaskFilter(8, BlurMaskFilter.Blur.SOLID));
            c.drawRoundRect(r, h / 2, h / 2, p);
            p.setShader(null); p.setMaskFilter(null);
        }
        return by + h + 8;
    }

    private void chips(Canvas c, List<String> status, float l, float baseline, float maxR) {
        text.setTypeface(sansBold); text.setTextSize(18);
        float x = l;
        for (String st : status) {
            String label = statusLabel(st);
            if (label == null) continue;
            float w = text.measureText(label) + 20;
            if (x + w > maxR) break;
            int col = isPositive(st) ? 0xFF7FC8F0 : 0xFFE88A9A;
            r.set(x, baseline - 22, x + w, baseline + 4);
            p.setColor(0x33000000 | (col & 0x00FFFFFF)); c.drawRoundRect(r, 12, 12, p);
            text.setColor(col);
            c.drawText(label, x + 10, baseline - 3, text);
            x += w + 8;
        }
    }

    // ------------------------------------------------------------------ rechte Seite

    private void drawTabs(Canvas c, GameState st) {
        String[] labels = { getResources().getString(R.string.tab_auto), getResources().getString(R.string.tab_ap), getResources().getString(R.string.tab_map),
                getResources().getString(R.string.tab_items), getResources().getString(R.string.tab_key), getResources().getString(R.string.tab_extra) };
        float l = 664, rr = 1216, t = 140, b = 196, w = (rr - l - 30) / 6f;
        text.setTypeface(sansBold); text.setTextSize(20); text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 6; i++) {
            RectF tr = tabRects[i];
            tr.set(l + i * (w + 6), t, l + i * (w + 6) + w, b);
            boolean sel = tab == i;
            p.setColor(sel ? 0xFF2A3360 : 0x90141B36);
            c.drawRoundRect(tr, 14, 14, p);
            if (sel) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(GOLD);
                c.drawRoundRect(tr, 14, 14, p); p.setStyle(Paint.Style.FILL);
            }
            text.setColor(sel ? GOLD : MUTED);
            c.drawText(labels[i], tr.centerX(), tr.centerY() + 8, text);
        }
        text.setTextAlign(Paint.Align.LEFT);
    }

    private boolean drawSidePanel(Canvas c, GameState st, float dt, long now) {
        float l = 664, t = 212, rr = 1216, b = REF_H - 24;
        window(c, l, t, rr, b);
        if (gameLook()) { l += 10; t += 10; rr -= 10; b -= 10; }
        int mode = tab;
        if (mode == TAB_AUTO) mode = st.inBattle ? -1 : (st.world != null || lastWorldState != null) && !"menu".equals(st.scene) ? TAB_MAP : TAB_ITEMS;
        c.save();
        c.clipRect(l + 2, t + 2, rr - 2, b - 2);
        boolean anim;
        switch (mode) {
            case -1: anim = drawEnemies(c, st, l, t, rr, b, dt); break;
            case TAB_MAP: anim = drawMap(c, st, l, t, rr, b, now); break;
            case TAB_KEY: anim = drawList(c, st.keyItems, true, l, t, rr, b); break;
            case TAB_AP: anim = drawAbilities(c, st, l, t, rr, b); break;
            case TAB_EXTRA: anim = drawExtras(c, st, l, t, rr, b); break;
            default: anim = drawList(c, st.items, false, l, t, rr, b); break;
        }
        c.restore();
        return anim;
    }

    private boolean drawEnemies(Canvas c, GameState st, float l, float t, float rr, float b, float dt) {
        text.setTypeface(serif); text.setTextSize(30); text.setColor(ENEMY);
        c.drawText(getResources().getString(R.string.enemies), l + 22, t + 44, text);
        if (st.enemies.isEmpty()) {
            text.setTypeface(sans); text.setTextSize(24); text.setColor(MUTED);
            c.drawText(getResources().getString(R.string.no_enemies), l + 22, t + 90, text);
            return false;
        }
        float y = t + 64, h = Math.min(250, (b - y - 10) / Math.max(1, st.enemies.size()));
        boolean anim = false;
        for (int i = 0; i < st.enemies.size(); i++) {
            GameState.Enemy e = st.enemies.get(i);
            float[] v = smooth("e" + i + e.name, dt, (float) e.hp / e.maxHp, (float) e.atb / e.maxAtb, 0, 0);
            anim |= v[4] > 0;
            r.set(l + 14, y, rr - 14, y + h - 10);
            p.setColor(0x60000000); c.drawRoundRect(r, 14, 14, p);
            text.setTypeface(serif); text.setTextSize(Math.min(28, h * 0.26f)); text.setColor(CREAM);
            c.drawText(e.name, l + 30, y + Math.min(36, h * 0.32f), text);
            float yy = y + Math.min(54, h * 0.42f);
            boolean compact = h < 100;
            yy = bar(c, getResources().getString(R.string.hp), compact ? "" : e.hp + " / " + e.maxHp, l + 30, yy - 8, rr - 30, compact ? 10 : 14, v[0], ENEMY, false);
            if (!compact && e.info && h >= 150) {
                text.setTypeface(sans); text.setTextSize(21);
                float ly = yy + 22;
                ly = infoLine(c, R.string.weak, elements(e.weak), 0xFFFFD27A, l + 30, ly, rr - 30);
                String res = join(elements(e.absorb), R.string.absorbs, join(elements(e.immune), R.string.immune, join(elements(e.half), R.string.halves, "")));
                if (!res.isEmpty() && ly < y + h - 40) { text.setColor(MUTED); c.drawText(fit(res, rr - l - 60), l + 30, ly, text); ly += 28; }
                if (ly < y + h - 34) ly = infoLine(c, R.string.steal, e.steal.isEmpty() ? getResources().getString(R.string.nothing_left) : TextUtils.join(", ", e.steal), 0xFF9FE0A8, l + 30, ly, rr - 30);
                if (ly < y + h - 34 && !e.drop.isEmpty()) infoLine(c, R.string.drops, TextUtils.join(", ", e.drop), CREAM, l + 30, ly, rr - 30);
            }
            else if (!compact) chips(c, e.status, l + 30, y + h - 18, rr - 30);
            y += h;
        }
        return anim;
    }

    private float infoLine(Canvas c, int label, String value, int color, float l, float y, float rr) {
        if (value.isEmpty()) return y;
        text.setTypeface(sansBold); text.setTextSize(19); text.setColor(MUTED);
        String lab = getResources().getString(label);
        c.drawText(lab, l, y, text);
        float x = l + text.measureText(lab) + 12;
        text.setTypeface(sans); text.setTextSize(22); text.setColor(color);
        c.drawText(fit(value, rr - x), x, y, text);
        return y + 28;
    }

    private String fit(String str, float width) {
        return TextUtils.ellipsize(str, new android.text.TextPaint(text), width, TextUtils.TruncateAt.END).toString();
    }

    private String elements(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            int id = getResources().getIdentifier("el_" + n, "string", getContext().getPackageName());
            sb.append(sb.length() > 0 ? ", " : "").append(id == 0 ? n : getResources().getString(id));
        }
        return sb.toString();
    }

    private String join(String value, int label, String rest) {
        if (value.isEmpty()) return rest;
        return getResources().getString(label) + " " + value + (rest.isEmpty() ? "" : " · " + rest);
    }

    /** What the equipped gear still teaches: the reason to keep an item on, or the all-clear to swap it. */
    private boolean drawAbilities(Canvas c, GameState st, float l, float t, float rr, float b) {
        float y = t + 16 - scroll, rowH = 44;
        Bitmap stone = spr("icon/ability_stone"), bar = spr("general/ap_bar_bg");
        for (GameState.Member m : st.party) {
            text.setTypeface(serif); text.setTextSize(28); text.setColor(GOLD);
            c.drawText(m.name, l + 22, y + 30, text);
            y += 42;
            if (m.learn.isEmpty()) {
                text.setTypeface(sans); text.setTextSize(21); text.setColor(MUTED);
                c.drawText(getResources().getString(R.string.ap_none), l + 22, y + 24, text);
                y += rowH;
            }
            for (GameState.Learn a : m.learn) {
                if (y + rowH > t && y < b) {
                    float x = l + 22;
                    if (stone != null && !a.support) { sprite(c, stone, x, y + 4, x + 38, y + 36); }
                    else { p.setColor(a.support ? 0xFF7FC8F0 : 0xFFE0556A); c.drawCircle(x + 18, y + 20, 9, p); }
                    text.setTypeface(sans); text.setTextSize(24); text.setColor(a.done ? MUTED : CREAM);
                    c.drawText(fit(a.name, rr - l - 330), x + 48, y + 29, text);
                    float bl = rr - 250, br = rr - 110;
                    r.set(bl, y + 12, br, y + 30); p.setColor(TRACK); c.drawRoundRect(r, 9, 9, p);
                    float frac = a.done ? 1 : Math.min(1f, a.ap / (float) a.max);
                    if (frac > 0) { r.set(bl, y + 12, bl + (br - bl) * frac, y + 30); p.setColor(a.done ? GOLD : 0xFF58C8F0); c.drawRoundRect(r, 9, 9, p); }
                    text.setTextAlign(Paint.Align.RIGHT); text.setTextSize(21); text.setColor(a.done ? GOLD : CREAM);
                    c.drawText(a.done ? "★ " + getResources().getString(R.string.ap_done) : a.ap + " / " + a.max, rr - 22, y + 28, text);
                    text.setTextAlign(Paint.Align.LEFT);
                }
                y += rowH;
            }
            y += 10;
        }
        maxScroll = Math.max(0, y + scroll - b + 10);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        return false;
    }

    /** The game's booster buttons by touch, and the counters the game keeps but never shows. */
    private boolean drawExtras(Canvas c, GameState st, float l, float t, float rr, float b) {
        maxScroll = 0; scroll = 0;
        text.setTypeface(serif); text.setTextSize(28); text.setColor(GOLD);
        c.drawText(getResources().getString(R.string.boosters), l + 22, t + 44, text);
        if (!st.hasExtras) return false;
        boolean[] on = { st.bSpeed, st.bNoEnc, st.bAssist, st.bDmg };
        int[] names = { R.string.b_speed, R.string.b_noenc, R.string.b_assist, R.string.b_dmg };
        String[] icons = { "icon/icon_ff", "icon/icon_encounter", "icon/icon_battle", "icon/icon_9999" };
        float bw = (rr - l - 44 - 14) / 2f, bh = 92;
        for (int i = 0; i < 4; i++) {
            float x = l + 22 + (i % 2) * (bw + 14), y = t + 62 + (i / 2) * (bh + 12);
            RectF br = boosterRects[i];
            br.set(x, y, x + bw, y + bh);
            p.setColor(on[i] ? 0xC0245A38 : 0x90000000); c.drawRoundRect(br, 14, 14, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(on[i] ? 3 : 2); p.setColor(on[i] ? 0xFF7FD69B : 0x66FFFFFF); c.drawRoundRect(br, 14, 14, p);
            p.setStyle(Paint.Style.FILL);
            Bitmap ic = spr(icons[i]);
            float tx = x + 18;
            if (ic != null) {
                float ih = 40, iw = ih * ic.getWidth() / ic.getHeight();
                p.setAlpha(on[i] ? 255 : 120);
                sprite(c, ic, x + 14, y + 26, x + 14 + iw, y + 26 + ih);
                p.setAlpha(255);
                tx = x + 24 + iw;
            }
            text.setTypeface(sansBold); text.setTextSize(22); text.setColor(st.boostersAllowed ? CREAM : MUTED);
            String label = getResources().getString(names[i]) + (i == 0 && st.speedFactor > 1 ? " ×" + st.speedFactor : "");
            c.drawText(fit(label, x + bw - tx - 10), tx, y + 40, text);
            text.setTypeface(sans); text.setTextSize(20); text.setColor(on[i] ? 0xFF7FD69B : MUTED);
            c.drawText(getResources().getString(on[i] ? R.string.on : R.string.off), tx, y + 70, text);
        }
        float y = t + 62 + 2 * (bh + 12) + 8;
        if (!st.boostersAllowed) {
            text.setTypeface(sans); text.setTextSize(19); text.setColor(HP_MID);
            c.drawText(fit(getResources().getString(R.string.boosters_off), rr - l - 44), l + 22, y + 14, text);
            y += 30;
        }
        text.setTypeface(serif); text.setTextSize(28); text.setColor(GOLD);
        c.drawText(getResources().getString(R.string.counters), l + 22, y + 44, text);
        y += 62;
        // Treasure hunter: below 200 points rank H, then one rank per 20 points up to A (320), S from 340
        String rank = st.treasure >= 340 ? "S" : st.treasure < 200 ? "H" : String.valueOf((char) ('G' - (st.treasure - 200) / 20));
        long left = 12 * 3600 - st.playTime;
        String[][] rows = {
            { getResources().getString(R.string.c_excalibur), left > 0 ? formatTime(left) : getResources().getString(R.string.c_excalibur_over) },
            { getResources().getString(R.string.c_steals), String.valueOf(st.steals) },
            { getResources().getString(R.string.c_frogs), String.valueOf(st.frogs) },
            { getResources().getString(R.string.c_dragons), String.valueOf(st.dragons) },
            { getResources().getString(R.string.c_treasure), getResources().getString(R.string.rank) + " " + rank + " · " + st.treasure },
            { getResources().getString(R.string.c_chocographs), st.chocographs + " / 24" },
            { getResources().getString(R.string.c_cards), st.cardWins + " / " + st.cardLosses + " / " + st.cardDraws },
        };
        float rowH = Math.min(46, (b - y - 12) / rows.length);
        for (int i = 0; i < rows.length; i++, y += rowH) {
            if (i % 2 == 0) { r.set(l + 8, y, rr - 8, y + rowH); p.setColor(0x22FFFFFF); c.drawRoundRect(r, 10, 10, p); }
            text.setTypeface(sans); text.setTextSize(23); text.setColor(CREAM);
            c.drawText(rows[i][0], l + 24, y + rowH * 0.68f, text);
            text.setTextAlign(Paint.Align.RIGHT); text.setColor(i == 0 && left <= 0 ? MUTED : GOLD);
            c.drawText(rows[i][1], rr - 24, y + rowH * 0.68f, text);
            text.setTextAlign(Paint.Align.LEFT);
        }
        return false;
    }

    private boolean drawMap(Canvas c, GameState st, float l, float t, float rr, float b, long now) {
        GameState ws = st.world != null ? st : lastWorldState;
        String key = ws != null && ws.world != null && ws.world.naviMap == 0 ? "mist" : "world";
        Bitmap bmp = maps.get(key);
        float pad = 16, aw = rr - l - pad * 2, ah = b - t - pad * 2 - 40;
        float iw = bmp != null ? bmp.getWidth() : 1536, ih = bmp != null ? bmp.getHeight() : 1280;
        float scale = Math.min(aw / iw, ah / ih);
        float dw = iw * scale, dh = ih * scale, dx = l + pad + (aw - dw) / 2f, dy = t + pad + (ah - dh) / 2f;
        r.set(dx, dy, dx + dw, dy + dh);
        if (bmp != null) {
            src.set(0, 0, bmp.getWidth(), bmp.getHeight());
            p.setColor(0xFFFFFFFF);
            p.setFilterBitmap(true);
            c.drawBitmap(bmp, src, r, p);
        } else {
            drawSchematic(c, r);
        }
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(PANEL_EDGE); c.drawRect(r, p); p.setStyle(Paint.Style.FILL);

        text.setTypeface(sans); text.setTextSize(21); text.setColor(MUTED);
        String caption = bmp == null ? getResources().getString(!mapFailed && connected ? R.string.map_loading : R.string.map_schematic)
                : st.world == null && ws != null ? getResources().getString(R.string.map_last) : "";
        c.drawText(caption, l + 20, b - 20, text);
        if (ws == null || ws.world == null) return false;

        GameState.World w = ws.world;
        float[] cal = MapCalibration.forKey(key, bmp != null);
        if (w.hasAirship) marker(c, r, cal, w.ax, w.ay, 0xFF7FE0FF, 9);
        if (w.hasChocobo) marker(c, r, cal, w.cx, w.cy, 0xFFF2D25C, 9);
        float px = r.left + r.width() * (cal[0] + cal[1] * w.x), py = r.top + r.height() * (cal[2] - cal[3] * w.y);
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 300.0);
        p.setColor(Color.argb((int) (90 * pulse + 40), 255, 220, 120));
        c.drawCircle(px, py, 18 + 8 * pulse, p);
        // Blickrichtung als Pfeil (Spiel: Rotation um Y, 0 = Norden)
        c.save();
        c.translate(px, py);
        c.rotate(w.rot);
        Path a = new Path();
        a.moveTo(0, -18); a.lineTo(11, 12); a.lineTo(0, 6); a.lineTo(-11, 12); a.close();
        p.setColor(0xFFFFE9A8); c.drawPath(a, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f); p.setColor(0xFF3A2A00); c.drawPath(a, p);
        p.setStyle(Paint.Style.FILL);
        c.restore();
        return st.world != null; // Puls nur, solange man wirklich auf der Weltkarte ist
    }

    private void marker(Canvas c, RectF area, float[] cal, float x, float y, int color, float rad) {
        float px = area.left + area.width() * (cal[0] + cal[1] * x), py = area.top + area.height() * (cal[2] - cal[3] * y);
        p.setColor(0xAA000000); c.drawCircle(px, py, rad + 3, p);
        p.setColor(color); c.drawCircle(px, py, rad, p);
    }

    private void drawSchematic(Canvas c, RectF area) {
        p.setShader(new LinearGradient(area.left, area.top, area.right, area.bottom, 0xFF0E2546, 0xFF0A1830, Shader.TileMode.CLAMP));
        c.drawRect(area, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1); p.setColor(0x334A78B8);
        for (int i = 1; i < 12; i++) {
            float x = area.left + area.width() * i / 12f; c.drawLine(x, area.top, x, area.bottom, p);
        }
        for (int i = 1; i < 10; i++) {
            float y = area.top + area.height() * i / 10f; c.drawLine(area.left, y, area.right, y, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private boolean drawList(Canvas c, List<GameState.Item> list, boolean key, float l, float t, float rr, float b) {
        float rowH = 48, top = t + 14;
        maxScroll = Math.max(0, list.size() * rowH - (b - top - 14));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        if (list.isEmpty()) {
            text.setTypeface(sans); text.setTextSize(24); text.setColor(MUTED);
            c.drawText(getResources().getString(R.string.no_items), l + 22, t + 50, text);
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            float y = top + i * rowH - scroll;
            if (y + rowH < t || y > b) continue;
            GameState.Item it = list.get(i);
            if (i % 2 == 0) { r.set(l + 8, y, rr - 8, y + rowH); p.setColor(0x22FFFFFF); c.drawRoundRect(r, 10, 10, p); }
            text.setTypeface(sans); text.setTextSize(26);
            text.setColor(key && it.used ? MUTED : CREAM);
            Bitmap ic = it.icon.isEmpty() ? null : spr("icon/" + it.icon);
            float tx = l + 24;
            if (ic != null) { sprite(c, ic, l + 18, y + 5, l + 18 + 38f * ic.getWidth() / ic.getHeight(), y + 43); tx = l + 70; }
            c.drawText(TextUtils.ellipsize(it.name, new android.text.TextPaint(text), rr - tx - 116, TextUtils.TruncateAt.END).toString(), tx, y + 33, text);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(GOLD);
            c.drawText(key ? (it.used ? getResources().getString(R.string.used) : "◆") : "× " + it.count, rr - 24, y + 33, text);
            text.setTextAlign(Paint.Align.LEFT);
        }
        if (maxScroll > 0) { // Bildlaufanzeige
            float trackH = b - t - 28, thumb = Math.max(40, trackH * (trackH / (trackH + maxScroll)));
            float ty = t + 14 + (trackH - thumb) * (scroll / maxScroll);
            r.set(rr - 8, ty, rr - 4, ty + thumb); p.setColor(0x88D9B45A); c.drawRoundRect(r, 2, 2, p);
        }
        return false;
    }

    // ------------------------------------------------------------------ Eingabe

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        float ox = (getWidth() - REF_W * s) / 2f, oy = (getHeight() - REF_H * s) / 2f;
        float x = (e.getX() - ox) / s, y = (e.getY() - oy) / s;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x; downY = y; lastTouchY = y; dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(y - downY) > 12) dragging = true;
                if (dragging) { scroll -= (y - lastTouchY); lastTouchY = y; invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (onTouchFinished != null) postDelayed(onTouchFinished, 120);
                if (!dragging && e.getActionMasked() == MotionEvent.ACTION_UP && downY < 120
                        && e.getEventTime() - e.getDownTime() > 700 && onHeaderHold != null) {
                    onHeaderHold.run(); // hold the header: step aside for the launcher's own panel
                    return true;
                }
                if (!dragging && e.getActionMasked() == MotionEvent.ACTION_UP) {
                    for (int i = 0; i < tabRects.length; i++) {
                        if (tabRects[i].contains(x, y)) { tab = i; scroll = 0; invalidate(); performClick(); break; }
                    }
                    GameState st = state;
                    if (tab == TAB_EXTRA && st != null && st.hasExtras && st.boostersAllowed && commands != null)
                        for (int i = 0; i < boosterRects.length; i++)
                            if (boosterRects[i].contains(x, y)) { commands.booster(BOOSTERS[i]); performClick(); break; }
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override public boolean performClick() { return super.performClick(); }

    // ------------------------------------------------------------------ Hilfen

    /** Glättet bis zu 4 Werte pro Schlüssel; Index 4 = 1, solange noch animiert wird. */
    private float[] smooth(String key, float dt, float a, float b, float c, float d) {
        float[] cur = shown.get(key);
        float[] target = { a, b, c, d };
        if (cur == null) { cur = new float[] { a, b, c, d, 0 }; shown.put(key, cur); return cur; }
        float k = Math.min(1f, dt * 9f);
        boolean moving = false;
        for (int i = 0; i < 4; i++) {
            float diff = target[i] - cur[i];
            if (Math.abs(diff) < 0.002f) cur[i] = target[i];
            else { cur[i] += diff * k; moving = true; }
        }
        cur[4] = moving ? 1 : 0;
        return cur;
    }

    private String sceneLabel(String scene) {
        switch (scene == null ? "" : scene) {
            case "field": return getResources().getString(R.string.scene_field);
            case "world": return getResources().getString(R.string.scene_world);
            case "battle": return getResources().getString(R.string.scene_battle);
            case "menu": return getResources().getString(R.string.scene_menu);
            case "cards": return getResources().getString(R.string.scene_cards);
            case "ending": return getResources().getString(R.string.scene_ending);
            default: return getResources().getString(R.string.scene_other);
        }
    }

    private String statusLabel(String name) {
        int id = getResources().getIdentifier("st_" + name, "string", getContext().getPackageName());
        return id == 0 ? null : getResources().getString(id); // interne Zustände (z. B. LowHP, Jump) werden nicht angezeigt
    }

    private static boolean isPositive(String st) {
        switch (st) {
            case "AutoLife": case "Trance": case "Defend": case "Regen": case "Haste": case "Float":
            case "Shell": case "Protect": case "Vanish": case "Reflect": return true;
            default: return false;
        }
    }

    private static String formatTime(long sec) {
        return String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60);
    }

    private float fitSize(String str, float max, float min, float width, Typeface tf) {
        text.setTypeface(tf);
        float size = max;
        text.setTextSize(size);
        while (size > min && text.measureText(str) > width) { size -= 2; text.setTextSize(size); }
        return size;
    }

    private void drawWrapped(Canvas c, String str, float cx, float y, float width, float lineH) {
        String[] words = str.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (text.measureText(test) > width && line.length() > 0) {
                c.drawText(line.toString(), cx, y, text); y += lineH; line.setLength(0); line.append(w);
            } else { line.setLength(0); line.append(test); }
        }
        if (line.length() > 0) c.drawText(line.toString(), cx, y, text);
    }

    private static int lighten(int c) {
        int r = Math.min(255, Color.red(c) + 60), g = Math.min(255, Color.green(c) + 60), b = Math.min(255, Color.blue(c) + 60);
        return Color.argb(Color.alpha(c), r, g, b);
    }
}
