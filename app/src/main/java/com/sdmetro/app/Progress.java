package com.sdmetro.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One account's progress: XP, active days, pips, review schedule, notes, arcade bests.
 * Serialises to the same JSON shape the earlier versions stored under "sdm.v1.&lt;id&gt;". Pure Java.
 */
public final class Progress {
    public static final long DAY = 86400000L;
    /** Days until a concept comes back for review, by how many times it has been recalled. */
    public static final int[] INTERVALS = {1, 3, 7, 16, 35, 80};
    public static final int DAILY_GOAL = 5;
    private static final int[] RANK_XP = {0, 200, 600, 1500, 3500, 7000};
    private static final String[] RANK_NAME = {"Commuter", "Regular", "Line Inspector", "Station Master", "Network Planner", "Chief Architect"};

    public static final class Pips {
        public long e, i, t;
        public int count() { return (e != 0 ? 1 : 0) + (i != 0 ? 1 : 0) + (t != 0 ? 1 : 0); }
        public long get(char pip) { return pip == 'e' ? e : pip == 'i' ? i : t; }
        void set(char pip, long v) { if (pip == 'e') e = v; else if (pip == 'i') i = v; else t = v; }
    }

    public static final class Rev {
        public int n;
        public long at;
        Rev(int n, long at) { this.n = n; this.at = at; }
    }

    public static final class Rank {
        public String name, next;
        public int pct;
        public long need;
    }

    public long xp;
    public final List<String> days = new ArrayList<>();
    public final Map<String, Integer> daily = new LinkedHashMap<>();
    public final Map<String, Pips> pips = new LinkedHashMap<>();
    public final Map<String, Rev> rev = new LinkedHashMap<>();
    public final Map<String, String> notes = new LinkedHashMap<>();
    /** Last opened stop, or -1 when none. */
    public int lastSec = -1, lastIdx = -1;
    public final Map<String, Integer> arcade = new LinkedHashMap<>();
    public long updated;

    /** Reads stored progress; anything unreadable or of another version gives a blank one. */
    public static Progress fromJson(String json) {
        Progress p = new Progress();
        if (json == null) return p;
        try {
            JSONObject j = new JSONObject(json);
            if (j.optInt("v", 0) != 1) return p;
            p.xp = j.optLong("xp", 0);
            JSONArray d = j.optJSONArray("days");
            if (d != null) for (int i = 0; i < d.length(); i++) p.days.add(d.optString(i));
            JSONObject o = j.optJSONObject("daily");
            if (o != null) for (Iterator<String> it = o.keys(); it.hasNext(); ) { String k = it.next(); p.daily.put(k, o.optInt(k, 0)); }
            o = j.optJSONObject("pips");
            if (o != null) for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject v = o.optJSONObject(k);
                if (v == null) continue;
                Pips x = new Pips();
                x.e = v.optLong("e", 0); x.i = v.optLong("i", 0); x.t = v.optLong("t", 0);
                p.pips.put(k, x);
            }
            o = j.optJSONObject("rev");
            if (o != null) for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject v = o.optJSONObject(k);
                if (v != null) p.rev.put(k, new Rev(v.optInt("n", 0), v.optLong("at", 0)));
            }
            o = j.optJSONObject("notes");
            if (o != null) for (Iterator<String> it = o.keys(); it.hasNext(); ) { String k = it.next(); p.notes.put(k, o.optString(k)); }
            o = j.optJSONObject("last");
            if (o != null) { p.lastSec = o.optInt("sec", -1); p.lastIdx = o.optInt("idx", -1); }
            o = j.optJSONObject("arcade");
            if (o != null) for (Iterator<String> it = o.keys(); it.hasNext(); ) { String k = it.next(); p.arcade.put(k, o.optInt(k, 0)); }
            p.updated = j.optLong("updated", 0);
        } catch (Exception e) {
            return new Progress();
        }
        return p;
    }

    public String toJson() {
        try {
            JSONObject j = new JSONObject();
            j.put("v", 1);
            j.put("xp", xp);
            j.put("days", new JSONArray(days));
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Integer> e : daily.entrySet()) o.put(e.getKey(), e.getValue().intValue());
            j.put("daily", o);
            o = new JSONObject();
            for (Map.Entry<String, Pips> e : pips.entrySet()) {
                JSONObject v = new JSONObject();
                Pips x = e.getValue();
                if (x.e != 0) v.put("e", x.e);
                if (x.i != 0) v.put("i", x.i);
                if (x.t != 0) v.put("t", x.t);
                o.put(e.getKey(), v);
            }
            j.put("pips", o);
            o = new JSONObject();
            for (Map.Entry<String, Rev> e : rev.entrySet()) o.put(e.getKey(), new JSONObject().put("n", e.getValue().n).put("at", e.getValue().at));
            j.put("rev", o);
            o = new JSONObject();
            for (Map.Entry<String, String> e : notes.entrySet()) o.put(e.getKey(), e.getValue());
            j.put("notes", o);
            j.put("last", lastSec < 0 ? JSONObject.NULL : new JSONObject().put("sec", lastSec).put("idx", lastIdx));
            o = new JSONObject();
            for (Map.Entry<String, Integer> e : arcade.entrySet()) o.put(e.getKey(), e.getValue().intValue());
            j.put("arcade", o);
            j.put("updated", updated);
            return j.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Pips pipsOf(String key) { Pips p = pips.get(key); return p != null ? p : new Pips(); }

    public int pipCount(String key) { Pips p = pips.get(key); return p == null ? 0 : p.count(); }

    /** Marks today as active and counts one action towards the daily quest. */
    public void touchDay(String today) {
        if (!days.contains(today)) days.add(today);
        Integer n = daily.get(today);
        daily.put(today, (n == null ? 0 : n) + 1);
    }

    /** Earns a pip (first time: full XP and a review is scheduled; afterwards 5 XP). Returns the XP gained. */
    public int award(String key, char pip, int fullXp, long now, String today) {
        Pips p = pips.get(key);
        if (p == null) { p = new Pips(); pips.put(key, p); }
        boolean fresh = p.get(pip) == 0;
        if (fresh) {
            p.set(pip, now);
            if (!rev.containsKey(key)) rev.put(key, new Rev(0, now));
        }
        int gain = fresh ? fullXp : 5;
        xp += gain;
        touchDay(today);
        return gain;
    }

    public void addXp(int n, String today) { xp += n; touchDay(today); }

    /** Consecutive active days ending today, or ending yesterday when today has no activity yet. */
    public int streak(LocalDate today) {
        Set<String> set = new HashSet<>(days);
        LocalDate d = today;
        int n = 0;
        if (!set.contains(d.toString())) d = d.minusDays(1);
        while (set.contains(d.toString())) { n++; d = d.minusDays(1); }
        return n;
    }

    public Rank rank() {
        int i = 0;
        for (int j = 0; j < RANK_XP.length; j++) if (xp >= RANK_XP[j]) i = j;
        Rank r = new Rank();
        r.name = RANK_NAME[i];
        if (i + 1 < RANK_XP.length) {
            r.next = RANK_NAME[i + 1];
            r.pct = (int) Math.round((xp - RANK_XP[i]) / (double) (RANK_XP[i + 1] - RANK_XP[i]) * 100);
            r.need = RANK_XP[i + 1] - xp;
        } else {
            r.pct = 100;
        }
        return r;
    }

    public int mastered(Roadmap rm) { int n = 0; for (String k : rm.keyLoc.keySet()) if (pipCount(k) == 3) n++; return n; }

    public int touched(Roadmap rm) { int n = 0; for (String k : rm.keyLoc.keySet()) if (pipCount(k) > 0) n++; return n; }

    /** Share of a line's pips earned, 0..1. Interchange stops inside one line count once. */
    public double secProgress(Roadmap.Section s) {
        Set<String> ks = new LinkedHashSet<>();
        for (Roadmap.Stop t : s.stops) ks.add(t.key);
        int got = 0;
        for (String k : ks) got += pipCount(k);
        return ks.isEmpty() ? 0 : got / (double) (ks.size() * 3);
    }

    public static long intervalMs(int n) { return INTERVALS[Math.min(Math.max(n, 0), INTERVALS.length - 1)] * DAY; }

    /** Concepts due for review now, the longest-waiting first. */
    public List<String> dueList(long now, Roadmap rm) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Rev> e : rev.entrySet()) {
            if (rm.keyLoc.containsKey(e.getKey()) && now - e.getValue().at >= intervalMs(e.getValue().n)) out.add(e.getKey());
        }
        out.sort((a, b) -> Long.compare(rev.get(a).at, rev.get(b).at));
        return out;
    }

    /** Rates a review: 0 forgot (back to the start), 1 got it, 2 easy (skips a step). */
    public void rate(String key, int v, long now) {
        Rev r = rev.get(key);
        if (r == null) return;
        r.n = v == 0 ? 0 : r.n + v;
        r.at = now;
    }

    /** Up to four stops to do next: the first unfinished one from where you are, then the first untouched stop of each line. */
    public List<Roadmap.Stop> suggestions(Roadmap rm) {
        List<Roadmap.Stop> out = new ArrayList<>();
        if (lastSec >= 0) {
            Roadmap.Section s = rm.sec(lastSec);
            if (s != null) for (int i = Math.max(lastIdx, 0); i < s.stops.size() && out.size() < 1; i++) {
                if (pipCount(s.stops.get(i).key) < 3) out.add(s.stops.get(i));
            }
        }
        for (Roadmap.Section s : rm.sections) {
            if (out.size() >= 4) break;
            Roadmap.Stop found = null;
            for (Roadmap.Stop x : s.stops) if (pipCount(x.key) == 0) { found = x; break; }
            if (found == null) continue;
            boolean dup = false;
            for (Roadmap.Stop o : out) if (o.key.equals(found.key)) dup = true;
            if (!dup) out.add(found);
        }
        return out.size() > 4 ? out.subList(0, 4) : out;
    }

    public int dailyCount(String today) { Integer n = daily.get(today); return n == null ? 0 : n; }

    public int best(String game) { Integer n = arcade.get(game); return n == null ? 0 : n; }

    /** Records an arcade round; returns the XP it earns. */
    public int finishGame(String game, int score, String today) {
        if (score > best(game)) arcade.put(game, score);
        int gain = score * 5 + 5;
        addXp(gain, today);
        return gain;
    }
}
