package de.harmssi.thorffix;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Unveränderlicher Schnappschuss des Spielzustands, wie ihn der Memoria-Statusserver liefert (GET /events). */
final class GameState {
    static final class Member {
        String name; int id, lv; long hp, maxHp, mp, maxMp; int trance; boolean hasTrance, inTrance, inBattle, canAct;
        int atb, maxAtb; List<String> status = new ArrayList<>();
        String face = ""; List<String> equip = new ArrayList<>(); List<Learn> learn = new ArrayList<>();
    }
    static final class Learn {
        String name; int ap, max; boolean done, support;
    }
    static final class Enemy {
        String name; int lv; long hp, maxHp; int atb, maxAtb; List<String> status = new ArrayList<>();
        List<String> weak = new ArrayList<>(), absorb = new ArrayList<>(), immune = new ArrayList<>(), half = new ArrayList<>(),
            steal = new ArrayList<>(), drop = new ArrayList<>();
        boolean info;
    }
    static final class Item {
        int id, count; String name, icon = ""; boolean used;
    }
    static final class World {
        float x, y, rot; int activeMap, naviMap; boolean hasChocobo, hasAirship; float cx, cy, ax, ay;
    }

    String scene = "title", sceneName = "", location = "", lang = "";
    boolean inGame;
    float fps;
    long playTime, gil;
    int fieldId, worldId, scenario = -1;
    final List<Member> party = new ArrayList<>();
    final List<Enemy> enemies = new ArrayList<>();
    boolean inBattle;
    World world;
    final List<Item> items = new ArrayList<>();
    final List<Item> keyItems = new ArrayList<>();
    String error;
    boolean hasExtras, boostersAllowed, bSpeed, bAssist, bDmg, bNoEnc;
    int speedFactor, steals, frogs, dragons, treasure, cardWins, cardLosses, cardDraws, chocographs;

    static GameState parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        GameState s = new GameState();
        s.error = o.optString("error", null);
        s.scene = o.optString("scene", "other");
        s.sceneName = o.optString("sceneName", "");
        s.fps = (float) o.optDouble("fps", 0);
        s.lang = o.optString("lang", "");
        s.inGame = o.optBoolean("inGame", false);
        s.playTime = o.optLong("playTime", 0);
        s.gil = o.optLong("gil", 0);
        s.location = o.optString("location", "");
        s.fieldId = o.optInt("fieldId", -1);
        s.worldId = o.optInt("worldId", -1);
        s.scenario = o.optInt("scenario", -1);
        JSONArray party = o.optJSONArray("party");
        if (party != null) for (int i = 0; i < party.length(); i++) {
            JSONObject p = party.getJSONObject(i);
            Member m = new Member();
            m.name = p.optString("name", "?");
            m.id = p.optInt("id", -1);
            m.lv = p.optInt("lv", 1);
            m.hp = p.optLong("hp"); m.maxHp = Math.max(1, p.optLong("maxHp", 1));
            m.mp = p.optLong("mp"); m.maxMp = Math.max(1, p.optLong("maxMp", 1));
            m.trance = p.optInt("trance", 0);
            m.hasTrance = p.optBoolean("hasTrance", false);
            m.inTrance = p.optBoolean("inTrance", false);
            m.inBattle = p.has("atb");
            m.atb = p.optInt("atb", 0); m.maxAtb = Math.max(1, p.optInt("maxAtb", 1));
            m.canAct = p.optBoolean("canAct", true);
            strings(p.optJSONArray("status"), m.status);
            m.face = p.optString("face", "");
            strings(p.optJSONArray("equip"), m.equip);
            JSONArray learn = p.optJSONArray("learn");
            if (learn != null) for (int k = 0; k < learn.length(); k++) {
                JSONObject a = learn.getJSONObject(k);
                Learn l = new Learn();
                l.name = a.optString("name", "?"); l.ap = a.optInt("ap", 0); l.max = Math.max(1, a.optInt("max", 1));
                l.done = a.optBoolean("done", false); l.support = a.optBoolean("support", false);
                m.learn.add(l);
            }
            s.party.add(m);
        }
        JSONObject battle = o.optJSONObject("battle");
        if (battle != null) {
            s.inBattle = true;
            JSONArray en = battle.optJSONArray("enemies");
            if (en != null) for (int i = 0; i < en.length(); i++) {
                JSONObject e = en.getJSONObject(i);
                Enemy x = new Enemy();
                x.name = e.optString("name", "?");
                x.lv = e.optInt("lv", 0);
                x.hp = e.optLong("hp"); x.maxHp = Math.max(1, e.optLong("maxHp", 1));
                x.atb = e.optInt("atb", 0); x.maxAtb = Math.max(1, e.optInt("maxAtb", 1));
                strings(e.optJSONArray("status"), x.status);
                x.info = e.has("weak");
                strings(e.optJSONArray("weak"), x.weak); strings(e.optJSONArray("absorb"), x.absorb);
                strings(e.optJSONArray("immune"), x.immune); strings(e.optJSONArray("half"), x.half);
                strings(e.optJSONArray("steal"), x.steal); strings(e.optJSONArray("drop"), x.drop);
                s.enemies.add(x);
            }
        }
        JSONObject w = o.optJSONObject("world");
        if (w != null) {
            World x = new World();
            x.x = (float) w.optDouble("x", 0); x.y = (float) w.optDouble("y", 0);
            x.rot = (float) w.optDouble("rot", 0);
            x.activeMap = w.optInt("activeMap", 1);
            x.naviMap = w.optInt("naviMap", 1);
            JSONObject c = w.optJSONObject("chocobo");
            if (c != null) { x.hasChocobo = true; x.cx = (float) c.optDouble("x"); x.cy = (float) c.optDouble("y"); }
            JSONObject a = w.optJSONObject("airship");
            if (a != null) { x.hasAirship = true; x.ax = (float) a.optDouble("x"); x.ay = (float) a.optDouble("y"); }
            s.world = x;
        }
        JSONObject bo = o.optJSONObject("boosters"), co = o.optJSONObject("counters");
        if (bo != null && co != null) {
            s.hasExtras = true;
            s.boostersAllowed = bo.optBoolean("allowed"); s.bSpeed = bo.optBoolean("speed"); s.bAssist = bo.optBoolean("assist");
            s.bDmg = bo.optBoolean("dmg"); s.bNoEnc = bo.optBoolean("noenc"); s.speedFactor = bo.optInt("speedFactor", 0);
            s.steals = co.optInt("steals"); s.frogs = co.optInt("frogs"); s.dragons = co.optInt("dragons"); s.treasure = co.optInt("treasure");
            s.cardWins = co.optInt("cardWins"); s.cardLosses = co.optInt("cardLosses"); s.cardDraws = co.optInt("cardDraws");
            s.chocographs = co.optInt("chocographs");
        }
        items(o.optJSONArray("items"), s.items);
        items(o.optJSONArray("keyItems"), s.keyItems);
        return s;
    }

    private static void strings(JSONArray a, List<String> out) {
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
    }

    private static void items(JSONArray a, List<Item> out) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            if (e == null) continue;
            Item it = new Item();
            it.id = e.optInt("id");
            it.name = e.optString("name", "");
            it.count = e.optInt("n", 0);
            it.used = e.optBoolean("used", false);
            it.icon = e.optString("icon", "");
            if (!it.name.isEmpty()) out.add(it);
        }
    }
}
